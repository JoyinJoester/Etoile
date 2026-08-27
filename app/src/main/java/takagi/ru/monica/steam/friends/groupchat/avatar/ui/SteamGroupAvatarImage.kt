package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend

@Composable
internal fun SteamGroupAvatarImage(
    url: String,
    members: List<SteamFriend> = emptyList(),
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        val normalizedUrl = url.trim()
        value = if (normalizedUrl.isBlank()) {
            null
        } else {
            loadSteamGroupAvatarWithRetry {
                loadSteamRemoteImage(context, normalizedUrl)
            }
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (members.isNotEmpty()) {
            SteamGroupMemberAvatarGrid(
                members = members,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(0.52f)
                )
            }
        }
    }
}

internal suspend fun <T> loadSteamGroupAvatarWithRetry(
    retryDelaysMillis: List<Long> = GROUP_AVATAR_RETRY_DELAYS_MILLIS,
    delayBlock: suspend (Long) -> Unit = { delay(it) },
    load: suspend () -> T?
): T? {
    retryDelaysMillis.forEach { delayMillis ->
        if (delayMillis > 0L) delayBlock(delayMillis)
        load()?.let { return it }
    }
    return null
}

private val GROUP_AVATAR_RETRY_DELAYS_MILLIS = listOf(0L, 1_000L, 4_000L, 12_000L)
