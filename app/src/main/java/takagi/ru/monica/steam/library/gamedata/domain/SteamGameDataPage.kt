package takagi.ru.monica.steam.library.gamedata.domain

import java.net.URI

internal data class SteamGameDataPage(
    val steamId: String,
    val appId: Int,
    val url: String
)

internal fun steamGameDataPage(steamId: String?, appId: Int): SteamGameDataPage? {
    val normalizedSteamId = steamId?.trim()?.takeIf(String::isNotBlank) ?: return null
    val numericSteamId = normalizedSteamId.toULongOrNull() ?: return null
    if (numericSteamId < STEAM_ID64_ACCOUNT_BASE || appId !in SUPPORTED_GAME_DATA_APP_IDS) {
        return null
    }
    return SteamGameDataPage(
        steamId = normalizedSteamId,
        appId = appId,
        url = "https://steamcommunity.com/profiles/$normalizedSteamId/gcpd/$appId/"
    )
}

internal object SteamReplayBrowserPolicy {
    fun normalizedUrl(rawUrl: String?): String? {
        val candidate = rawUrl?.trim()?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val trustedLegacyReplay = scheme == "http" && TRUSTED_REPLAY_HOSTS.any { root ->
            host == root || host.endsWith(".$root")
        }
        if (scheme != "https" && !trustedLegacyReplay) {
            return null
        }
        return candidate
    }
}

private val SUPPORTED_GAME_DATA_APP_IDS = setOf(
    570, // Dota 2
    730  // Counter-Strike 2
)

private val STEAM_ID64_ACCOUNT_BASE = 76_561_197_960_265_728uL

private val TRUSTED_REPLAY_HOSTS = setOf(
    "valve.net",
    "steamcontent.com",
    "steamusercontent.com",
    "steampowered.com",
    "steamcommunity.com"
)
