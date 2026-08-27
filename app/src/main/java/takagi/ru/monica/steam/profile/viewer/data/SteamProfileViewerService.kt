package takagi.ru.monica.steam.profile.viewer.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.community.data.SteamCommunityBadgeCatalogLoader
import takagi.ru.monica.steam.community.data.SteamCommunityParser
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievementProgress
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparison
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGroup
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerFailureReason
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerResult
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.profile.viewer.domain.buildSteamAchievementComparison
import takagi.ru.monica.steam.profile.viewer.domain.withKnownSelfGames

internal class SteamProfileViewerService(
    private val remote: SteamProfileViewerRemote = SteamProfileViewerSteamRemote()
) {
    fun fetchProfile(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget,
        language: String,
        knownSelfGames: List<SteamGame> = emptyList()
    ): SteamProfileViewerResult<SteamProfileViewerSnapshot> {
        if (!viewer.hasRealSteamId) {
            return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.ACCOUNT_REQUIRED
            )
        }
        val accessToken = viewer.accessToken?.takeIf(String::isNotBlank)
            ?: return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.SESSION_REQUIRED
            )
        return runCatching {
            val summary = SteamProfileViewerParser.parseProfileSummary(
                payload = remote.fetchProfileSummary(accessToken, target.steamId),
                target = target
            ) ?: return@runCatching SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.INVALID_RESPONSE
            )
            val level = runCatching {
                SteamProfileViewerParser.parseSteamLevel(
                    remote.fetchSteamLevel(accessToken, target.steamId.toLong())
                )
            }.getOrNull()
            val resolvedSummary = summary.copy(steamLevel = level)
            val communityCounts = runCatching {
                SteamProfileViewerParser.parseCommunityCounts(
                    remote.fetchCommunityProfile(viewer, target.steamId, language)
                )
            }.getOrNull()
            val apiBadges = runCatching {
                SteamCommunityParser.badges(
                    remote.fetchBadges(accessToken, target.steamId)
                ).badges
            }.getOrDefault(emptyList())
            val badgeDetails = runCatching {
                SteamCommunityBadgeCatalogLoader.load(target.steamId) { page ->
                    remote.fetchBadgePage(
                        viewer = viewer,
                        targetSteamId = target.steamId,
                        language = language,
                        page = page
                    )
                }
            }.getOrDefault(emptyList())
            val profileBadges = SteamCommunityParser.mergeBadgeDetails(
                badges = apiBadges,
                details = badgeDetails
            )
            val isSelf = viewer.steamId == target.steamId
            val targetGameResult = loadTargetGames(
                accessToken = accessToken,
                targetSteamId = target.steamId,
                language = language,
                profileIsPublic = resolvedSummary.isPublic,
                isSelf = isSelf
            )
            if (targetGameResult.failure != null && isSelf) {
                return@runCatching SteamProfileViewerResult.Failure(targetGameResult.failure)
            }
            val targetGames = targetGameResult.games
            val viewerGames = if (isSelf) {
                targetGames
            } else {
                SteamProfileViewerParser.parseOwnedGames(
                    remote.fetchOwnedGames(accessToken, viewer.steamId.toLong(), language)
                )
            }
            val snapshot = SteamProfileViewerSnapshot(
                    viewerAccountId = viewer.id,
                    viewerSteamId = viewer.steamId,
                    target = resolvedSummary,
                    targetGames = targetGames,
                    viewerGames = viewerGames,
                    gameDataVisibility = targetGameResult.visibility,
                    fetchedAt = System.currentTimeMillis(),
                    friendCount = communityCounts?.friendCount
                        ?: 0.takeIf { resolvedSummary.isPublic && communityCounts != null },
                    groupCount = communityCounts?.groupCount
                        ?: 0.takeIf { resolvedSummary.isPublic && communityCounts != null },
                    badgeCount = communityCounts?.badgeCount
                        ?: profileBadges.takeIf { it.isNotEmpty() }
                            ?.count { it.isUnlocked },
                    badges = profileBadges
                ).withKnownSelfGames(knownSelfGames)
            SteamProfileViewerResult.Success(snapshot)
        }.getOrElse { error -> failure(error, targetIsOtherUser = false) }
    }

    fun fetchCommunityFriends(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget,
        language: String
    ): SteamProfileViewerResult<List<SteamFriend>> = fetchCommunityList(viewer, target) {
        SteamProfileViewerParser.parseCommunityFriends(
            remote.fetchCommunityFriends(viewer, target.steamId, language)
        )
    }

    fun fetchCommunityGroups(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget,
        language: String
    ): SteamProfileViewerResult<List<SteamProfileGroup>> = fetchCommunityList(viewer, target) {
        SteamProfileViewerParser.parseCommunityGroups(
            remote.fetchCommunityGroups(viewer, target.steamId, language)
        )
    }

    fun fetchAchievementComparison(
        viewer: SteamAccount,
        targetSteamId: String,
        game: SteamGame,
        language: String
    ): SteamProfileViewerResult<SteamAchievementComparison> {
        if (!viewer.hasRealSteamId) {
            return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.ACCOUNT_REQUIRED
            )
        }
        val accessToken = viewer.accessToken?.takeIf(String::isNotBlank)
            ?: return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.SESSION_REQUIRED
            )
        return runCatching {
            if (game.achievementTotalCount == 0) {
                return@runCatching SteamProfileViewerResult.Success(
                    emptyAchievementComparison(viewer.steamId, targetSteamId, game)
                )
            }
            val definitions = remote.fetchAchievementDefinitions(
                accessToken,
                game.appId,
                language
            )
            if (!SteamProfileViewerParser.hasAchievementDefinitions(definitions)) {
                return@runCatching SteamProfileViewerResult.Success(
                    emptyAchievementComparison(viewer.steamId, targetSteamId, game)
                )
            }
            val targetResponse = remote.fetchUserAchievements(
                accessToken,
                targetSteamId.toLong(),
                game.appId
            )
            val viewerResponse = if (targetSteamId == viewer.steamId) {
                targetResponse
            } else {
                remote.fetchUserAchievements(
                    accessToken,
                    viewer.steamId.toLong(),
                    game.appId
                )
            }
            val targetAchievements = SteamGameLibraryService.parseAchievementResponses(
                accountId = targetSteamId.toLong(),
                appId = game.appId,
                gameName = game.name,
                definitionsResponse = definitions,
                userResponse = targetResponse
            )
            val viewerAchievements = SteamGameLibraryService.parseAchievementResponses(
                accountId = viewer.steamId.toLong(),
                appId = game.appId,
                gameName = game.name,
                definitionsResponse = definitions,
                userResponse = viewerResponse
            )
            SteamProfileViewerResult.Success(
                buildSteamAchievementComparison(
                    viewerSteamId = viewer.steamId,
                    targetSteamId = targetSteamId,
                    viewer = viewerAchievements,
                    target = targetAchievements
                )
            )
        }.getOrElse { error -> failure(error, targetSteamId != viewer.steamId) }
    }

    private fun emptyAchievementComparison(
        viewerSteamId: String,
        targetSteamId: String,
        game: SteamGame
    ): SteamAchievementComparison = SteamAchievementComparison(
        viewerSteamId = viewerSteamId,
        targetSteamId = targetSteamId,
        appId = game.appId,
        gameName = game.name,
        achievements = emptyList(),
        fetchedAt = System.currentTimeMillis()
    )

    private fun loadTargetGames(
        accessToken: String,
        targetSteamId: String,
        language: String,
        profileIsPublic: Boolean,
        isSelf: Boolean
    ): TargetGamesResult {
        if (!isSelf && !profileIsPublic) {
            return TargetGamesResult(
                games = emptyList(),
                visibility = SteamProfileGameDataVisibility.PRIVATE
            )
        }
        val rawGames = runCatching {
            SteamProfileViewerParser.parseOwnedGames(
                remote.fetchOwnedGames(accessToken, targetSteamId.toLong(), language)
            )
        }.getOrElse { error ->
            val reason = failureReason(error, targetIsOtherUser = !isSelf)
            return TargetGamesResult(
                games = emptyList(),
                visibility = if (reason == SteamProfileViewerFailureReason.GAME_DATA_PRIVATE) {
                    SteamProfileGameDataVisibility.PRIVATE
                } else {
                    SteamProfileGameDataVisibility.UNAVAILABLE
                },
                failure = reason
            )
        }
        val progress = rawGames.map(SteamGame::appId)
            .distinct()
            .chunked(ACHIEVEMENT_PROGRESS_BATCH_SIZE)
            .fold(linkedMapOf<Int, SteamGameAchievementProgress>()) { result, batch ->
                val parsed = runCatching {
                    SteamProfileViewerParser.parseAchievementProgress(
                        remote.fetchAchievementProgress(
                            accessToken = accessToken,
                            targetSteamId = targetSteamId.toLong(),
                            appIds = batch,
                            language = language
                        )
                    )
                }.getOrDefault(emptyMap())
                result.apply { putAll(parsed) }
            }
        val games = SteamProfileViewerParser.applyAchievementProgress(rawGames, progress)
        return TargetGamesResult(
            games = games,
            visibility = if (!isSelf && games.isEmpty()) {
                SteamProfileGameDataVisibility.UNAVAILABLE
            } else {
                SteamProfileGameDataVisibility.AVAILABLE
            }
        )
    }

    private fun failure(
        error: Throwable,
        targetIsOtherUser: Boolean
    ): SteamProfileViewerResult.Failure = SteamProfileViewerResult.Failure(
        failureReason(error, targetIsOtherUser)
    )

    private fun <T> fetchCommunityList(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget,
        block: () -> List<T>
    ): SteamProfileViewerResult<List<T>> {
        if (!viewer.hasRealSteamId) {
            return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.ACCOUNT_REQUIRED
            )
        }
        if (viewer.accessToken.isNullOrBlank()) {
            return SteamProfileViewerResult.Failure(
                SteamProfileViewerFailureReason.SESSION_REQUIRED
            )
        }
        return runCatching { SteamProfileViewerResult.Success(block()) }
            .getOrElse { error -> failure(error, target.steamId != viewer.steamId) }
    }

    private fun failureReason(
        error: Throwable,
        targetIsOtherUser: Boolean
    ): SteamProfileViewerFailureReason = when (error) {
        is SteamApiException -> when {
            error.eResult == 429 || error.httpStatusCode == 429 ->
                SteamProfileViewerFailureReason.RATE_LIMITED
            targetIsOtherUser && (
                error.eResult == 15 || error.httpStatusCode == 403
            ) -> SteamProfileViewerFailureReason.GAME_DATA_PRIVATE
            error.eResult == 5 || error.eResult == 15 || error.eResult == 401 ||
                error.eResult == 403 || error.httpStatusCode == 401 ->
                SteamProfileViewerFailureReason.SESSION_REQUIRED
            else -> SteamProfileViewerFailureReason.NETWORK
        }
        else -> SteamProfileViewerFailureReason.NETWORK
    }

    private data class TargetGamesResult(
        val games: List<SteamGame>,
        val visibility: SteamProfileGameDataVisibility,
        val failure: SteamProfileViewerFailureReason? = null
    )

    private companion object {
        const val ACHIEVEMENT_PROGRESS_BATCH_SIZE = 100
    }
}
