package takagi.ru.monica.steam.profile.viewer.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerFailureReason
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerResult
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget

class SteamProfileViewerServiceTest {
    @Test
    fun publicFriendProfileIncludesGamesLevelAndCommonGameCount() {
        val remote = FakeProfileViewerRemote().apply {
            summaries[FRIEND_STEAM_ID] = summary(FRIEND_STEAM_ID, visibility = 3)
            levels[FRIEND_STEAM_ID] = levelResponse(42)
            games[FRIEND_STEAM_ID] = ownedGamesResponse(
                game(10, "Shared", 100),
                game(20, "Friend only", 200)
            )
            games[VIEWER_STEAM_ID] = ownedGamesResponse(
                game(10, "Shared", 300),
                game(30, "Viewer only", 400)
            )
            progress[FRIEND_STEAM_ID] = achievementProgressResponse(10, 5, 10)
            communityProfileHtml = communityCounts(friends = 12, groups = 3, badges = 2)
            badgePayload = badgeResponse(1)
            badgePages[1] = """
                <div class="pageLinks"><a class="pagelink" href="?p=2">2</a></div>
                <div id="badge_badge_1" class="badge_row is_link">
                  <a class="badge_row_overlay" href="/profiles/$FRIEND_STEAM_ID/badges/1"></a>
                  <div class="badge_info_title">Community Ambassador</div>
                  <div class="badge_info_description">Level 2, 200 XP</div>
                  <div class="badge_info_unlocked">Unlocked 1 Jan</div>
                </div>
            """.trimIndent()
            badgePages[2] = """
                <div id="badge_gamebadge_620_0_0" class="badge_row is_link">
                  <a class="badge_row_overlay" href="/profiles/$FRIEND_STEAM_ID/gamecards/620/"></a>
                  <div class="badge_title">Portal 2</div>
                  <div class="badge_info_title">Badge not crafted</div>
                  <div class="badge_info_description">0 XP</div>
                </div>
            """.trimIndent()
        }
        val result = SteamProfileViewerService(remote).fetchProfile(
            viewer = account(),
            target = SteamProfileViewerTarget(FRIEND_STEAM_ID),
            language = "schinese"
        ) as SteamProfileViewerResult.Success

        assertEquals(42, result.value.target.steamLevel)
        assertEquals(2, result.value.targetGameCount)
        assertEquals(1, result.value.commonGameCount)
        assertEquals(12, result.value.friendCount)
        assertEquals(3, result.value.groupCount)
        assertEquals(2, result.value.badgeCount)
        assertEquals(2, result.value.badges.size)
        assertTrue(!result.value.badges.first { it.appId == 620 }.isUnlocked)
        assertEquals(5, result.value.targetGames.first { it.appId == 10 }.achievementUnlockedCount)
        assertEquals(SteamProfileGameDataVisibility.AVAILABLE, result.value.gameDataVisibility)
    }

    @Test
    fun privateProfileStillReturnsSummaryWithExplicitPrivateGameState() {
        val remote = FakeProfileViewerRemote().apply {
            summaries[FRIEND_STEAM_ID] = summary(FRIEND_STEAM_ID, visibility = 1)
            levels[FRIEND_STEAM_ID] = levelResponse(5)
            games[VIEWER_STEAM_ID] = ownedGamesResponse(game(30, "Viewer only", 400))
        }
        val result = SteamProfileViewerService(remote).fetchProfile(
            viewer = account(),
            target = SteamProfileViewerTarget(FRIEND_STEAM_ID),
            language = "schinese"
        ) as SteamProfileViewerResult.Success

        assertTrue(result.value.targetGames.isEmpty())
        assertEquals(SteamProfileGameDataVisibility.PRIVATE, result.value.gameDataVisibility)
        assertEquals(0, remote.targetOwnedGameRequests)
    }

    @Test
    fun targetGamePermissionFailureDoesNotHideBasicProfile() {
        val remote = FakeProfileViewerRemote().apply {
            summaries[FRIEND_STEAM_ID] = summary(FRIEND_STEAM_ID, visibility = 3)
            levels[FRIEND_STEAM_ID] = levelResponse(7)
            gameFailures[FRIEND_STEAM_ID] = SteamApiException(
                message = "private",
                httpStatusCode = 403
            )
            games[VIEWER_STEAM_ID] = ownedGamesResponse(game(30, "Viewer only", 400))
        }
        val result = SteamProfileViewerService(remote).fetchProfile(
            viewer = account(),
            target = SteamProfileViewerTarget(FRIEND_STEAM_ID),
            language = "schinese"
        ) as SteamProfileViewerResult.Success

        assertEquals(SteamProfileGameDataVisibility.PRIVATE, result.value.gameDataVisibility)
    }

    @Test
    fun optionalCommunityDataFailureDoesNotHideProfileAndGames() {
        val remote = FakeProfileViewerRemote().apply {
            summaries[FRIEND_STEAM_ID] = summary(FRIEND_STEAM_ID, visibility = 3)
            levels[FRIEND_STEAM_ID] = levelResponse(7)
            games[FRIEND_STEAM_ID] = ownedGamesResponse(game(10, "Shared", 100))
            games[VIEWER_STEAM_ID] = ownedGamesResponse(game(10, "Shared", 200))
            optionalCommunityFailure = SteamApiException(
                message = "community unavailable",
                httpStatusCode = 500
            )
        }
        val result = SteamProfileViewerService(remote).fetchProfile(
            viewer = account(),
            target = SteamProfileViewerTarget(FRIEND_STEAM_ID),
            language = "schinese"
        ) as SteamProfileViewerResult.Success

        assertEquals(1, result.value.targetGameCount)
        assertEquals(null, result.value.friendCount)
        assertEquals(null, result.value.groupCount)
        assertTrue(result.value.badges.isEmpty())
    }

    @Test
    fun selfProfileKeepsKnownLibraryPerfectGameProgress() {
        val remote = FakeProfileViewerRemote().apply {
            summaries[VIEWER_STEAM_ID] = summary(VIEWER_STEAM_ID, visibility = 3)
            levels[VIEWER_STEAM_ID] = levelResponse(12)
            games[VIEWER_STEAM_ID] = ownedGamesResponse(game(10, "Perfect", 300))
            communityProfileHtml = """
                <div class="profile_friend_links">
                  <span class="profile_count_link_total">32</span>
                </div>
            """.trimIndent()
        }
        val knownGame = takagi.ru.monica.steam.library.SteamGame(
            appId = 10,
            name = "Perfect",
            playtimeForeverMinutes = 300,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 10,
            achievementTotalCount = 10,
            allAchievementsUnlocked = true
        )

        val result = SteamProfileViewerService(remote).fetchProfile(
            viewer = account(),
            target = SteamProfileViewerTarget(VIEWER_STEAM_ID),
            language = "schinese",
            knownSelfGames = listOf(knownGame)
        ) as SteamProfileViewerResult.Success

        assertEquals(1, result.value.perfectGameCount)
        assertEquals(0, result.value.groupCount)
        assertTrue(result.value.targetGames.single().isPerfectAchievementGame)
    }

    @Test
    fun missingAccessTokenReturnsSessionRequired() {
        val result = SteamProfileViewerService(FakeProfileViewerRemote()).fetchProfile(
            viewer = account().copy(accessToken = null),
            target = SteamProfileViewerTarget(FRIEND_STEAM_ID),
            language = "schinese"
        ) as SteamProfileViewerResult.Failure

        assertEquals(SteamProfileViewerFailureReason.SESSION_REQUIRED, result.reason)
    }

    @Test
    fun gameWithoutAchievementDefinitionsReturnsEmptyComparison() {
        val remote = FakeProfileViewerRemote()
        val result = SteamProfileViewerService(remote).fetchAchievementComparison(
            viewer = account(),
            targetSteamId = FRIEND_STEAM_ID,
            game = takagi.ru.monica.steam.library.SteamGame(
                appId = 10,
                name = "No achievements",
                playtimeForeverMinutes = 0,
                playtimeRecentMinutes = 0
            ),
            language = "schinese"
        ) as SteamProfileViewerResult.Success

        assertEquals(0, result.value.total)
        assertEquals(0, remote.userAchievementRequests)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = VIEWER_STEAM_ID,
        accountName = "viewer",
        displayName = "Viewer",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$VIEWER_STEAM_ID||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun summary(steamId: String, visibility: Int): JsonObject = buildJsonObject {
        put("response", buildJsonObject {
            put("players", buildJsonArray {
                add(buildJsonObject {
                    put("steamid", steamId)
                    put("personaname", "Friend")
                    put("avatarfull", "https://avatars.steamstatic.com/test_full.jpg")
                    put("profileurl", "https://steamcommunity.com/profiles/$steamId")
                    put("communityvisibilitystate", visibility)
                    put("personastate", 1)
                })
            })
        })
    }

    private fun levelResponse(level: Int): ByteArray = SteamProtoWriter().apply {
        writeVarint(1, level.toLong())
    }.toByteArray()

    private fun game(appId: Int, name: String, playtime: Int): SteamProtoWriter =
        SteamProtoWriter().apply {
            writeVarint(1, appId.toLong())
            writeString(2, name)
            writeVarint(3, 0)
            writeVarint(4, playtime.toLong())
            writeString(5, "icon")
        }

    private fun ownedGamesResponse(vararg games: SteamProtoWriter): ByteArray =
        SteamProtoWriter().apply {
            writeVarint(1, games.size.toLong())
            games.forEach { writeMessage(2, it) }
        }.toByteArray()

    private fun achievementProgressResponse(
        appId: Int,
        unlocked: Int,
        total: Int
    ): ByteArray = SteamProtoWriter().apply {
        writeMessage(1, SteamProtoWriter().apply {
            writeVarint(1, appId.toLong())
            writeVarint(2, unlocked.toLong())
            writeVarint(3, total.toLong())
            writeBool(5, unlocked >= total)
        })
    }.toByteArray()

    private fun communityCounts(friends: Int, groups: Int, badges: Int): String = """
        <div class="profile_friend_links"><span class="profile_count_link_total">$friends</span></div>
        <div class="profile_group_links"><span class="profile_count_link_total">$groups</span></div>
        <a href="/profiles/$FRIEND_STEAM_ID/badges/">
          <span class="count_link_label">Badges</span>
          <span class="profile_count_link_total">$badges</span>
        </a>
    """.trimIndent()

    private fun badgeResponse(id: Int): JsonObject = buildJsonObject {
        put("response", buildJsonObject {
            put("badges", buildJsonArray {
                add(buildJsonObject {
                    put("badgeid", id)
                    put("level", 2)
                    put("xp", 200)
                })
            })
        })
    }

    private companion object {
        const val VIEWER_STEAM_ID = "76561198000000001"
        const val FRIEND_STEAM_ID = "76561198000000002"
    }
}

private class FakeProfileViewerRemote : SteamProfileViewerRemote {
    val summaries = mutableMapOf<String, JsonObject>()
    val levels = mutableMapOf<String, ByteArray>()
    val games = mutableMapOf<String, ByteArray>()
    val progress = mutableMapOf<String, ByteArray>()
    val gameFailures = mutableMapOf<String, Throwable>()
    var communityProfileHtml: String = ""
    var communityFriendsHtml: String = ""
    var communityGroupsHtml: String = ""
    var badgePayload: JsonObject = buildJsonObject { put("response", buildJsonObject {}) }
    val badgePages = mutableMapOf<Int, String>()
    var optionalCommunityFailure: Throwable? = null
    var targetOwnedGameRequests = 0
    var userAchievementRequests = 0

    override fun fetchProfileSummary(accessToken: String, targetSteamId: String): JsonObject =
        requireNotNull(summaries[targetSteamId])

    override fun fetchSteamLevel(accessToken: String, targetSteamId: Long): ByteArray =
        levels[targetSteamId.toString()] ?: ByteArray(0)

    override fun fetchOwnedGames(
        accessToken: String,
        targetSteamId: Long,
        language: String
    ): ByteArray {
        val key = targetSteamId.toString()
        if (key == "76561198000000002") targetOwnedGameRequests++
        gameFailures[key]?.let { throw it }
        return requireNotNull(games[key])
    }

    override fun fetchAchievementProgress(
        accessToken: String,
        targetSteamId: Long,
        appIds: List<Int>,
        language: String
    ): ByteArray = progress[targetSteamId.toString()] ?: ByteArray(0)

    override fun fetchAchievementDefinitions(
        accessToken: String,
        appId: Int,
        language: String
    ): ByteArray = ByteArray(0)

    override fun fetchUserAchievements(
        accessToken: String,
        targetSteamId: Long,
        appId: Int
    ): ByteArray {
        userAchievementRequests++
        return ByteArray(0)
    }

    override fun fetchCommunityProfile(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String {
        optionalCommunityFailure?.let { throw it }
        return communityProfileHtml
    }

    override fun fetchCommunityFriends(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String {
        optionalCommunityFailure?.let { throw it }
        return communityFriendsHtml
    }

    override fun fetchCommunityGroups(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String {
        optionalCommunityFailure?.let { throw it }
        return communityGroupsHtml
    }

    override fun fetchBadges(accessToken: String, targetSteamId: String): JsonObject {
        optionalCommunityFailure?.let { throw it }
        return badgePayload
    }

    override fun fetchBadgePage(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String,
        page: Int
    ): String {
        optionalCommunityFailure?.let { throw it }
        return badgePages[page].orEmpty()
    }
}
