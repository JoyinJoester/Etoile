package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage

@Composable
internal fun CommunityAvatar(
    imageUrl: String,
    fallback: String,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberCommunityImage(imageUrl)
    Surface(
        modifier = modifier,
        shape = LocalSteamAvatarShape.current,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.steam_account_avatar_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    fallback.ifBlank { "S" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun CommunityGameIcon(imageUrl: String, modifier: Modifier = Modifier) {
    val bitmap = rememberCommunityImage(imageUrl)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SportsEsports, contentDescription = null)
            }
        }
    }
}

@Composable
internal fun CommunityBadgeIcon(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberCommunityImage(imageUrl)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = contentDescription,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun rememberCommunityImage(url: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = if (url.isBlank()) null else loadSteamRemoteImage(context, url)
    }
    return bitmap
}
