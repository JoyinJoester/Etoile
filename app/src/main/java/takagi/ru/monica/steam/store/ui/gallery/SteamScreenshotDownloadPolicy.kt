package takagi.ru.monica.steam.store.ui.gallery

import takagi.ru.monica.steam.foundation.media.SteamImageDownloadPolicy

internal object SteamScreenshotDownloadPolicy {
    fun isAllowedUrl(rawUrl: String): Boolean {
        return SteamImageDownloadPolicy.isAllowedUrl(rawUrl)
    }

    fun normalizeMimeType(rawMimeType: String?): String? {
        return SteamImageDownloadPolicy.normalizeMimeType(rawMimeType)
    }

    fun buildDisplayName(
        gameName: String,
        screenshotIndex: Int,
        mimeType: String,
        timestampMillis: Long
    ): String {
        return SteamImageDownloadPolicy.buildDisplayName(
            fileStem = fileStem(gameName, screenshotIndex),
            mimeType = mimeType,
            timestampMillis = timestampMillis,
            fallbackStem = "steam_game_screenshot"
        )
    }

    fun safeFileStem(rawName: String): String {
        return SteamImageDownloadPolicy.safeFileStem(
            rawName = rawName,
            fallbackStem = "steam_game",
            maxLength = 56
        )
    }

    fun fileStem(gameName: String, screenshotIndex: Int): String =
        "${safeFileStem(gameName)}_screenshot_${screenshotIndex.coerceAtLeast(0) + 1}"
}
