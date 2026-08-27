package takagi.ru.monica.github.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import takagi.ru.monica.github.domain.GithubAvatarRepository

@Composable
fun GithubAvatar(
    login: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape
) {
    val model = remember(avatarUrl) { normalizeGithubAvatarUrl(avatarUrl) }
    val repository = LocalGithubAvatarRepository.current
    var bitmap by remember(model) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(model, repository) {
        bitmap = model?.let { url ->
            repository.bytes(url).getOrNull()?.let { bytes ->
                withContext(Dispatchers.Default) { decodeGithubAvatar(bytes) }
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = login.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

val LocalGithubAvatarRepository = staticCompositionLocalOf<GithubAvatarRepository> {
    object : GithubAvatarRepository {
        override suspend fun bytes(url: String): Result<ByteArray?> = Result.success(null)
    }
}

internal fun normalizeGithubAvatarUrl(value: String?): String? {
    val parsed = value?.trim()?.takeIf(String::isNotEmpty)
        ?.let { it.toHttpUrlOrNull() }
        ?: return null
    return parsed.takeIf { it.scheme == "https" && it.host.isNotBlank() }?.toString()
}

private fun decodeGithubAvatar(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > MAX_AVATAR_EDGE || bounds.outHeight / sampleSize > MAX_AVATAR_EDGE) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )?.asImageBitmap()
}

private const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
private const val MAX_AVATAR_EDGE = 256
