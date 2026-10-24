package takagi.ru.monica.ui.components

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes

internal fun markdownReadingAnnotator(
    images: Map<Int, MarkdownInlineImage>,
    renderImages: Boolean,
    codeStyle: SpanStyle,
    linkStyles: TextLinkStyles = TextLinkStyles(),
    references: Map<String, String> = emptyMap(),
    onOpenLink: (String) -> Unit = {},
): MarkdownAnnotator {
    lateinit var annotator: MarkdownAnnotator
    val listener = LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.let { onOpenLink(it.url) } }
    annotator = markdownAnnotator { source, node ->
        when (node.type) {
            MarkdownElementTypes.IMAGE -> {
                if (renderImages) images[node.startOffset]?.let { appendInlineContent(it.key, it.description.ifBlank { "Image" }) }
                true
            }
            MarkdownElementTypes.CODE_SPAN -> {
                withStyle(codeStyle) { append(markdownCodeSpan(source.substring(node.startOffset, node.endOffset))) }
                true
            }
            MarkdownTokenTypes.TEXT -> {
                append(decodeMarkdownText(source.substring(node.startOffset, node.endOffset)))
                true
            }
            MarkdownElementTypes.INLINE_LINK, MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                val text = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT } ?: label
                val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION || it.type == MarkdownElementTypes.AUTOLINK }
                val target = destination?.let { decodeMarkdownText(source.substring(it.startOffset, it.endOffset).removeSurrounding("<", ">")) }
                    ?: label?.let { references[markdownReferenceLabel(source.substring(it.startOffset, it.endOffset))] }
                if (target != null && text != null) {
                    withLink(LinkAnnotation.Url(target, linkStyles, listener)) {
                        buildMarkdownAnnotatedString(
                            source, text.children.drop(1).dropLast(1),
                            DefaultAnnotatorSettings(linkStyles, codeStyle, annotator),
                        )
                    }
                    true
                } else false
            }
            else -> false
        }
    }
    return annotator
}
