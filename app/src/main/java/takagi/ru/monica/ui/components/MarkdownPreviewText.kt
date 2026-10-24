package takagi.ru.monica.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.markdownInlineContent

/**
 * Markdown/GFM 文档阅读组件，支持常见 README HTML 混排。
 * 使用独立的阅读字号与图片尺寸，配色跟随当前主题。
 *
 * 内部基于 multiplatform-markdown-renderer，图片经 Coil3 异步加载。
 *
 * [imageBitmaps] 与 [maxElements] 为旧签名兼容参数，已不再参与渲染。
 */
@Composable
fun MarkdownPreviewText(
    markdown: String,
    imageBitmaps: Map<String, Bitmap> = emptyMap(),
    onOpenExternalLink: ((String) -> Unit)? = null,
    renderImages: Boolean = true,
    maxElements: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    imageContext: MarkdownImageContext? = null,
    documentBaseUrl: String? = null,
    imageBaseUrl: String? = documentBaseUrl
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // A new document owns a new state; a cancelled old parse cannot replace newer content.
    val documentState = key(markdown, darkTheme) {
        produceState<PreparedMarkdownDocument?>(null) {
            value = prepareMarkdownDocument(markdown, darkTheme)
        }
    }
    val document = documentState.value
    if (document == null) {
        Box(modifier.fillMaxWidth().height(24.dp))
        return
    }
    val content = document.content
    val images = document.images
    val imagesByOffset = remember(images) { images.associateBy { it.offset } }
    val references = document.references
    val typography = remember { markdownReadingTypography() }
    val codeStyle = typography.inlineCode.copy(background = MaterialTheme.colorScheme.surfaceContainerHigh).toSpanStyle()
    val defaultUriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            val target = if (documentBaseUrl != null) resolveMarkdownDocumentLink(uri, documentBaseUrl) ?: return
                else uri.removeSurrounding("<", ">")
            val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(target)?.groupValues?.get(1)?.lowercase()
            if (scheme != null && scheme !in setOf("http", "https", "mailto")) return
            if (onOpenExternalLink == null && scheme == null) return
            if (onOpenExternalLink != null) onOpenExternalLink(target)
            else try {
                defaultUriHandler.openUri(target)
            } catch (_: IllegalArgumentException) {
                android.widget.Toast.makeText(context, takagi.ru.monica.R.string.github_external_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, takagi.ru.monica.R.string.github_external_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                android.widget.Toast.makeText(context, takagi.ru.monica.R.string.github_external_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val annotator = remember(imagesByOffset, renderImages, codeStyle, references, uriHandler) {
        markdownReadingAnnotator(imagesByOffset, renderImages, codeStyle, typography.textLink, references, uriHandler::openUri)
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val inline = if (renderImages) markdownImageInlineContent(images, maxWidth, imageContext, imageBaseUrl) else emptyMap()
            val density = LocalDensity.current
            Markdown(
                content = content,
                colors = markdownColor(),
                typography = typography,
                annotator = annotator,
                inlineContent = markdownInlineContent(inline),
                components = markdownComponents(image = { model ->
                    imagesByOffset[model.node.startOffset]?.let { image ->
                        inline[image.key]?.let { item ->
                            with(density) {
                                Box(Modifier.size(item.placeholder.width.toDp(), item.placeholder.height.toDp())) {
                                    item.children(image.description)
                                }
                            }
                        }
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
