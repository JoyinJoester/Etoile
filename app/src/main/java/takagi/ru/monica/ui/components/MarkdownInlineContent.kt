package takagi.ru.monica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import takagi.ru.monica.R

/** CSS image dimensions are logical pixels; clamp width while preserving each image's aspect ratio. */
internal fun markdownImageSize(image: MarkdownInlineImage, intrinsic: Size, maxWidth: Float): Size {
    val naturalWidth = intrinsic.width.takeIf { it.isFinite() && it > 0 }
    val naturalHeight = intrinsic.height.takeIf { it.isFinite() && it > 0 }
    val ratio = if (naturalWidth != null && naturalHeight != null) naturalWidth / naturalHeight else 1f
    val width = image.width?.toFloat() ?: image.height?.let { it * ratio } ?: naturalWidth ?: minOf(160f, maxWidth)
    val height = image.height?.toFloat() ?: (width / ratio)
    val scale = minOf(1f, maxWidth.coerceAtLeast(1f) / width)
    return Size((width * scale).coerceAtLeast(1f), (height * scale).coerceAtLeast(1f))
}

@Composable
internal fun markdownImageInlineContent(
    images: List<MarkdownInlineImage>,
    maxWidth: Dp,
    context: MarkdownImageContext?,
    baseUrl: String? = null,
): Map<String, InlineTextContent> {
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val result = mutableMapOf<String, InlineTextContent>()
    images.forEach { image ->
        key(image.key) {
            val destination = remember(image.destination, context, baseUrl) { resolveMarkdownImage(image.destination, context, baseUrl) }
            val painter = if (destination != null) rememberAsyncImagePainter(
                remember(destination, platformContext) {
                    ImageRequest.Builder(platformContext).data(destination).size(coil3.size.Size.ORIGINAL).build()
                }
            ) else null
            val state = painter?.state?.collectAsState()?.value
            val intrinsic = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize ?: Size.Unspecified
            val size = if (state is AsyncImagePainter.State.Success) markdownImageSize(image, intrinsic, maxWidth.value)
                else Size(maxWidth.value.coerceAtLeast(1f), 96f)
            val placeholder = with(density) {
                Placeholder(size.width.dp.toSp(), size.height.dp.toSp(), PlaceholderVerticalAlign.Center)
            }
            result[image.key] = InlineTextContent(placeholder) {
                if (state is AsyncImagePainter.State.Success) {
                    Image(
                        painter = painter, contentDescription = image.description,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    )
                } else {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (image.description.isNotBlank()) Text(image.description, style = MaterialTheme.typography.labelMedium)
                            when {
                                destination == null -> Text(stringResource(R.string.markdown_image_unavailable), style = MaterialTheme.typography.bodySmall)
                                state is AsyncImagePainter.State.Error -> TextButton(onClick = { painter.restart() }) {
                                    Text(stringResource(R.string.markdown_image_retry), style = MaterialTheme.typography.bodySmall)
                                }
                                else -> Text(stringResource(R.string.markdown_image_loading), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    return result
}
