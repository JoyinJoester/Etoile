package takagi.ru.monica.steam.library.screenshots.domain

internal data class SteamGameScreenshotsPage(
    val steamId: String,
    val appId: Int,
    val url: String
)

internal fun steamGameScreenshotsPage(
    steamId: String?,
    appId: Int
): SteamGameScreenshotsPage? {
    val normalizedSteamId = steamId?.trim()?.takeIf(String::isNotBlank) ?: return null
    val numericSteamId = normalizedSteamId.toULongOrNull() ?: return null
    if (numericSteamId < STEAM_ID64_ACCOUNT_BASE || appId <= 0) return null
    return SteamGameScreenshotsPage(
        steamId = normalizedSteamId,
        appId = appId,
        url = "https://steamcommunity.com/profiles/$normalizedSteamId/screenshots/?appid=$appId"
    )
}

private val STEAM_ID64_ACCOUNT_BASE = 76_561_197_960_265_728uL
