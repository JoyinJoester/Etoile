package takagi.ru.monica.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes as Elements
import org.intellij.markdown.MarkdownTokenTypes as Tokens
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal data class PreparedMarkdownDocument(
    val content: String,
    val images: List<MarkdownInlineImage>,
    val references: Map<String, String>,
)

/** CPU-heavy parsing must not occupy the UI thread while a screen is entering or returning. */
internal suspend fun prepareMarkdownDocument(markdown: String, darkTheme: Boolean): PreparedMarkdownDocument =
    withContext(Dispatchers.Default) {
        val content = prepareMarkdownContent(markdown, darkTheme)
        currentCoroutineContext().ensureActive()
        val images = markdownInlineImages(content)
        currentCoroutineContext().ensureActive()
        val references = markdownReferenceLinks(content)
        PreparedMarkdownDocument(content, images, references)
    }

private val markdownTextEscape = Regex("""\\[!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]|&(?:[A-Za-z][A-Za-z0-9]+|#[0-9]{1,8}|#[xX][a-fA-F0-9]{1,8});""")

internal fun decodeMarkdownText(text: String): String = markdownTextEscape.replace(text) {
    if (it.value.startsWith('\\')) it.value.drop(1) else Parser.unescapeEntities(it.value, false)
}

private fun ASTNode.isCode() = type == Elements.CODE_SPAN || type == Elements.CODE_FENCE || type == Elements.CODE_BLOCK

/** Convert supported HTML semantics, never interpreting code or decoding entities before Markdown parsing. */
internal fun prepareMarkdownContent(markdown: String, darkTheme: Boolean = false): String {
    if ('<' !in markdown) return markdown
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    val replacements = mutableListOf<Pair<IntRange, String>>()
    val links = ArrayDeque<String>()
    var consumedUntil = 0
    fun visit(node: ASTNode) {
        if (node.endOffset <= consumedUntil) return
        if (node.isCode()) return
        if (node.type == Elements.HTML_BLOCK || node.type == Tokens.HTML_TAG) {
            val source = markdown.substring(node.startOffset, node.endOffset)
            if (node.type == Tokens.HTML_TAG && Regex("(?i)^<code(?:\\s[^>]*)?>$").matches(source)) {
                val closing = Regex("(?i)</code\\s*>").find(markdown, node.endOffset)
                if (closing != null) {
                    consumedUntil = closing.range.last + 1
                    val fragment = Jsoup.parseBodyFragment(markdown.substring(node.startOffset, consumedUntil))
                    replacements += (node.startOffset until consumedUntil) to fragment.body().childNodes().joinToString("") { htmlMarkdown(it, darkTheme) }
                    return
                }
            }
            val replacement = if (node.type == Elements.HTML_BLOCK) {
                Jsoup.parseBodyFragment(source).body().childNodes().joinToString("") { htmlMarkdown(it, darkTheme) }
            } else {
                // Inline HTML opening/closing tags are separate AST leaves. Keep their surrounding Markdown intact.
                val name = Regex("^<\\s*(/?)\\s*([a-zA-Z0-9]+)").find(source)
                val closing = name?.groupValues?.get(1) == "/"
                when (name?.groupValues?.get(2)?.lowercase()) {
                    "a" -> if (closing) {
                        links.removeLastOrNull()?.let { "](<$it>)" }.orEmpty()
                    } else {
                        val href = Jsoup.parseBodyFragment(source).selectFirst("a")?.attr("href").orEmpty()
                        links.addLast(markdownDestination(href))
                        "["
                    }
                    "b", "strong" -> "**"
                    "i", "em" -> "*"
                    "s", "del", "strike" -> "~~"
                    "br" -> "  \n"
                    "img" -> Jsoup.parseBodyFragment(source).selectFirst("img")?.let { htmlMarkdown(it, darkTheme) }.orEmpty()
                    "hr" -> "\n\n---\n\n"
                    "p", "div", "details", "summary" -> "\n\n"
                    else -> ""
                }
            }
            replacements += (node.startOffset until node.endOffset) to replacement
        } else node.children.forEach(::visit)
    }
    visit(tree)
    return StringBuilder(markdown).apply {
        replacements.asReversed().forEach { (range, replacement) -> replace(range.first, range.last + 1, replacement) }
    }.toString()
}

private fun markdownDestination(value: String): String = value.trim()
    .replace("<", "%3C").replace(">", "%3E").replace("\n", "").replace("\r", "")

private fun markdownLabel(value: String): String = value.replace("\\", "\\\\")
    .replace("[", "\\[").replace("]", "\\]").replace("\n", " ")

private fun htmlMarkdown(node: Node, darkTheme: Boolean): String = when (node) {
    // Re-encode HTML entities so &lt;img&gt; stays text until the annotator renders it.
    is TextNode -> Entities.escape(node.wholeText)
    is Element -> {
        fun children() = node.childNodes().joinToString("") { htmlMarkdown(it, darkTheme) }
        when (node.normalName()) {
            "script", "style", "iframe", "object", "source" -> ""
            "img" -> {
                val source = markdownDestination(node.attr("src"))
                val alt = markdownLabel(node.attr("alt"))
                val width = htmlImageDimension(node.attr("width")) ?: 0
                val height = htmlImageDimension(node.attr("height")) ?: 0
                val size = if (width > 0 || height > 0) " \"etoile-size:${width}x${height}\"" else ""
                if (source.isBlank()) alt else "![$alt](<$source>$size)"
            }
            "picture" -> {
                val image = node.selectFirst("img")?.clone()
                if (image == null) "" else {
                    val source = node.select("source").firstOrNull {
                        val media = it.attr("media").lowercase()
                        media.isBlank() || media == "(prefers-color-scheme: ${if (darkTheme) "dark" else "light"})"
                    }?.attr("srcset")?.substringBefore(',')?.trim()?.substringBefore(' ')
                    if (!source.isNullOrBlank()) image.attr("src", source)
                    htmlMarkdown(image, darkTheme)
                }
            }
            "a" -> {
                val label = children()
                if (node.hasAttr("href")) "[$label](<${markdownDestination(node.attr("href"))}>)" else label
            }
            "br" -> "  \n"
            "hr" -> "\n\n---\n\n"
            "b", "strong" -> "**${children()}**"
            "i", "em" -> "*${children()}*"
            "s", "del", "strike" -> "~~${children()}~~"
            "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(node.normalName().last().digitToInt())} ${children()}\n\n"
            "pre" -> {
                val code = node.wholeText().trimEnd('\n', '\r')
                val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0) + 1))
                "\n\n$fence\n$code\n$fence\n\n"
            }
            "code" -> {
                val text = node.wholeText()
                val ticks = "`".repeat((Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1)
                "$ticks $text $ticks"
            }
            "summary" -> "\n\n**${children()}**\n\n"
            "table" -> htmlTableMarkdown(node, darkTheme)
            "ul", "ol" -> "\n\n" + node.children().mapIndexed { index, child ->
                val marker = if (node.normalName() == "ol") "${index + 1}. " else "- "
                marker + htmlMarkdown(child, darkTheme).trim().replace("\n", "\n  ")
            }.joinToString("\n") + "\n\n"
            else -> if (node.isBlock) "\n\n${children()}\n\n" else children()
        }
    }
    else -> ""
}

private fun htmlImageDimension(value: String): Int? = value.trim().removeSuffix("px")
    .toIntOrNull()?.takeIf { it in 1..10000 }

private fun htmlTableMarkdown(table: Element, darkTheme: Boolean): String {
    val rows = table.select("tr").filter { it.closest("table") == table }.map { row ->
        row.children().filter { it.normalName() in setOf("td", "th") }.map { cell ->
            cell.childNodes().joinToString("") { htmlMarkdown(it, darkTheme) }.trim()
                .replace("|", "\\|").replace(Regex("\\s*\\n\\s*"), " ")
        }
    }.filter { it.isNotEmpty() }
    val columns = rows.maxOfOrNull { it.size } ?: return ""
    fun line(cells: List<String>) = "| " + (0 until columns).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"
    return "\n\n" + (listOf(line(rows.first()), line(List(columns) { "---" })) + rows.drop(1).map(::line)).joinToString("\n") + "\n\n"
}

internal data class MarkdownInlineImage(
    val offset: Int,
    val destination: String,
    val description: String,
    val width: Int? = null,
    val height: Int? = null,
) {
    val key: String get() = "etoile-image-$offset"
}

/** Each image gets its own placeholder; the upstream renderer shares one size per paragraph. */
internal fun markdownInlineImages(content: String): List<MarkdownInlineImage> {
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
    fun ASTNode.find(type: org.intellij.markdown.IElementType): ASTNode? =
        if (this.type == type) this else children.firstNotNullOfOrNull { it.find(type) }
    fun ASTNode.text() = content.substring(startOffset, endOffset)
    fun label(value: String) = decodeMarkdownText(value.removeSurrounding("[", "]"))
        .trim().replace(Regex("\\s+"), " ").lowercase()
    val definitions = mutableMapOf<String, ASTNode>()
    fun definitions(node: ASTNode) {
        if (node.isCode()) return
        if (node.type == Elements.LINK_DEFINITION) {
            node.find(Elements.LINK_LABEL)?.let { definitions.putIfAbsent(label(it.text()), node) }
        } else node.children.forEach(::definitions)
    }
    definitions(tree)
    val images = mutableListOf<MarkdownInlineImage>()
    fun visit(node: ASTNode) {
        if (node.isCode()) return
        if (node.type == Elements.IMAGE) {
            val reference = node.find(Elements.LINK_LABEL)?.let { definitions[label(it.text())] }
            val link = node.children.firstOrNull { it.type == Elements.INLINE_LINK } ?: node
            val destination = link.children.firstOrNull { it.type == Elements.LINK_DESTINATION || it.type == Elements.AUTOLINK }
                ?: reference?.children?.firstOrNull { it.type == Elements.LINK_DESTINATION || it.type == Elements.AUTOLINK }
            if (destination != null) {
                val title = (node.find(Elements.LINK_TITLE) ?: reference?.find(Elements.LINK_TITLE))?.text().orEmpty()
                // GFM's angle autolink parser can classify a following title as LINK_DESTINATION.
                val size = Regex("etoile-size:(\\d+)x(\\d+)").find(title)
                    ?: Regex("\\s\"etoile-size:(\\d+)x(\\d+)\"\\s*\\)$").find(node.text())
                images += MarkdownInlineImage(
                    offset = node.startOffset,
                    destination = decodeMarkdownText(destination.text().removeSurrounding("<", ">")),
                    description = decodeMarkdownText((node.find(Elements.LINK_TEXT) ?: node.find(Elements.LINK_LABEL))?.text().orEmpty().removeSurrounding("[", "]")),
                    width = size?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 },
                    height = size?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it > 0 },
                )
            }
        } else node.children.forEach(::visit)
    }
    visit(tree)
    return images
}

internal fun markdownCodeSpan(source: String): String {
    val ticks = source.takeWhile { it == '`' }
    val text = source.removePrefix(ticks).removeSuffix(ticks).replace(Regex("\r\n|[\r\n]"), " ")
    return if (text.startsWith(' ') && text.endsWith(' ') && text.isNotBlank()) text.drop(1).dropLast(1) else text
}

internal fun markdownReferenceLabel(value: String): String = decodeMarkdownText(value.removeSurrounding("[", "]"))
    .trim().replace(Regex("\\s+"), " ").lowercase()

internal fun markdownReferenceLinks(content: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    fun visit(node: ASTNode) {
        if (node.isCode()) return
        if (node.type == Elements.LINK_DEFINITION) {
            val label = node.children.firstOrNull { it.type == Elements.LINK_LABEL }
            val destination = node.children.firstOrNull { it.type == Elements.LINK_DESTINATION || it.type == Elements.AUTOLINK }
            if (label != null && destination != null) {
                result.putIfAbsent(
                    markdownReferenceLabel(content.substring(label.startOffset, label.endOffset)),
                    decodeMarkdownText(content.substring(destination.startOffset, destination.endOffset).removeSurrounding("<", ">")),
                )
            }
        } else node.children.forEach(::visit)
    }
    visit(MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content))
    return result
}
