package takagi.ru.monica.steam.community.data

import java.security.SecureRandom
import java.util.Locale
import takagi.ru.monica.steam.community.domain.SteamCommunityGateway
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot
import takagi.ru.monica.steam.community.domain.STEAM_COMMUNITY_CORE_SECTIONS
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException

class SteamCommunityService(
    private val api: SteamApiClient = SteamApiClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamCommunityGateway {
    private val secureRandom = SecureRandom()

    override fun fetch(account: SteamAccount): SteamCommunitySnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val token = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val playerQuery = mapOf("steamid" to account.steamId)
        val failures = linkedSetOf<SteamCommunitySection>()
        val authenticationFailures = mutableListOf<Throwable>()

        fun <T> section(
            section: SteamCommunitySection,
            fallback: () -> T,
            request: () -> T
        ): T = try {
            request()
        } catch (error: Throwable) {
            failures += section
            if (error.requiresCommunitySessionRefresh()) authenticationFailures += error
            fallback()
        }

        val profile = section(SteamCommunitySection.PROFILE, fallback = { null }) {
            SteamCommunityParser.profile(
                api.steamApiGetJson(
                    path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                    query = mapOf("steamids" to account.steamId),
                    accessToken = token
                )
            ) ?: error("Steam profile unavailable")
        }
        val level = section(SteamCommunitySection.LEVEL, fallback = { null }) {
            SteamCommunityParser.level(
                api.steamApiGetJson(
                    path = "/IPlayerService/GetSteamLevel/v1/",
                    query = playerQuery,
                    accessToken = token
                )
            ) ?: error("Steam level unavailable")
        }
        val badges = section(
            section = SteamCommunitySection.BADGES,
            fallback = { SteamCommunityParser.ParsedBadges(emptyList(), null, null) }
        ) {
            SteamCommunityParser.badges(
                api.steamApiGetJson(
                    path = "/IPlayerService/GetBadges/v1/",
                    query = playerQuery,
                    accessToken = token
                )
            )
        }
        val recentGames = section(
            section = SteamCommunitySection.RECENT_GAMES,
            fallback = { emptyList() }
        ) {
            SteamCommunityParser.recentGames(
                api.steamApiGetJson(
                    path = "/IPlayerService/GetRecentlyPlayedGames/v1/",
                    query = playerQuery + ("count" to "10"),
                    accessToken = token
                )
            )
        }
        val badgeDetails = runCatching {
            SteamCommunityParser.badgeDetails(
                html = api.communityGetText(
                    path = "/profiles/${account.steamId}/badges/",
                    query = mapOf("p" to "1", "l" to communityLanguage()),
                    cookies = communityCookies(account),
                    referer = "https://steamcommunity.com/profiles/${account.steamId}/"
                ),
                steamId = account.steamId
            )
        }.getOrDefault(emptyList())
        val liveBadges = SteamCommunityParser.mergeBadgeDetails(
            badges = badges.badges,
            details = badgeDetails
        )

        if (
            failures.containsAll(STEAM_COMMUNITY_CORE_SECTIONS) &&
            authenticationFailures.isNotEmpty()
        ) {
            throw authenticationFailures.first()
        }

        return SteamCommunitySnapshot(
            accountSteamId = account.steamId,
            profile = profile,
            steamLevel = level,
            badges = liveBadges,
            playerXp = badges.playerXp,
            playerXpNeededToLevelUp = badges.playerXpNeededToLevelUp,
            recentGames = recentGames,
            unavailableSections = failures,
            fetchedAt = nowMillis()
        )
    }

    private fun communityCookies(account: SteamAccount): Map<String, String> {
        val loginSecure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: "${account.steamId}||${account.accessToken.orEmpty()}"
        val sessionBytes = ByteArray(12).also(secureRandom::nextBytes)
        val sessionId = sessionBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return mapOf(
            "steamLoginSecure" to loginSecure,
            "sessionid" to sessionId,
            "mobileClient" to "android",
            "mobileClientVersion" to "777777 3.6.4"
        )
    }

    private fun communityLanguage(): String = when (Locale.getDefault().toLanguageTag().lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "schinese"
        "zh-tw", "zh-hk", "zh-hant" -> "tchinese"
        "ja", "ja-jp" -> "japanese"
        "ru", "ru-ru" -> "russian"
        "vi", "vi-vn" -> "vietnamese"
        else -> "english"
    }
}

private fun Throwable.requiresCommunitySessionRefresh(): Boolean {
    val error = this as? SteamApiException ?: return false
    return error.eResult?.let { it == 5 || it == 15 || it == 401 || it == 403 } == true ||
        error.httpStatusCode?.let { it == 401 || it == 403 } == true
}
