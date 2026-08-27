package takagi.ru.monica.steam.profile.viewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

@Composable
internal fun rememberSteamProfileViewerImage(url: String?): ImageBitmap? {
    val context = LocalContext.current
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = url
            ?.takeIf(String::isNotBlank)
            ?.let { imageUrl -> cache.load(imageUrl)?.asImageBitmap() }
    }
    return bitmap
}
