package takagi.ru.monica.steam.store.ui.gallery

import androidx.compose.runtime.Composable
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.media.SteamFullscreenImageViewer
import takagi.ru.monica.steam.foundation.media.SteamImageViewerStrings

@Composable
internal fun SteamStoreScreenshotViewer(
    gameName: String,
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    SteamFullscreenImageViewer(
        title = gameName,
        images = screenshots,
        initialIndex = initialIndex,
        fileStemForIndex = { index ->
            SteamScreenshotDownloadPolicy.fileStem(gameName, index)
        },
        strings = SteamImageViewerStrings(
            imageDescription = R.string.steam_store_screenshot_description,
            close = R.string.steam_store_screenshot_close,
            previous = R.string.steam_store_screenshot_previous,
            next = R.string.steam_store_screenshot_next,
            position = R.string.steam_store_screenshot_position,
            download = R.string.steam_store_screenshot_download,
            downloading = R.string.steam_store_screenshot_downloading,
            downloadSuccess = R.string.steam_store_screenshot_download_success,
            downloadFailed = R.string.steam_store_screenshot_download_failed,
            downloadUnsupported = R.string.steam_store_screenshot_download_unsupported,
            downloadTooLarge = R.string.steam_store_screenshot_download_too_large,
            permissionDenied = R.string.steam_store_screenshot_permission_denied,
            invalidSource = R.string.steam_store_screenshot_invalid_source,
            loadFailed = R.string.steam_store_screenshot_load_failed
        ),
        onDismiss = onDismiss
    )
}
