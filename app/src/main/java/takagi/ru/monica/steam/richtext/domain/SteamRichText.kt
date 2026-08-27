package takagi.ru.monica.steam.richtext.domain

import java.net.URI
import java.util.ArrayDeque

internal enum class SteamRichTextStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    CODE,
    QUOTE,
    HEADING,
    HIGHLIGHT,
    SPOILER,
}

internal data class SteamRichTextSpan(
    val start: Int,
    val endExclusive: Int,
    val style: SteamRichTextStyle,
)

internal data class SteamRichTextLink(
    val start: Int,
    val endExclusive: Int,
    val url: String,
)

internal data class SteamRichTextDocument(
    val text: String,
    val spans: List<SteamRichTextSpan> = emptyList(),
    val links: List<SteamRichTextLink> = emptyList(),
)

internal object SteamRichTextParser {
    private val bbcodeUrlPattern = Regex(
        """\[url(?:=([^]]+))?](.*?)\[/url]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val bbcodeTagPattern = Regex(
        """\[(/?)(b|i|u|s|strike|code|quote|spoiler|h[1-6]|list|olist|\*)(?:=[^]]*)?]""",
        RegexOption.IGNORE_CASE,
    )
    private val plainUrlPattern = Regex("""(?:https?://|steam://)[^\s<>{}\[\]]+""", RegexOption.IGNORE_CASE)
    private val htmlBreakPattern = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    fun parse(
        source: String,
        sourceLinks: List<SteamRichTextLink> = emptyList(),
    ): SteamRichTextDocument {
        if (source.isEmpty()) return SteamRichTextDocument("")
        val output = StringBuilder(source.length)
        val spans = mutableListOf<SteamRichTextSpan>()
        val discoveredLinks = mutableListOf<SteamRichTextLink>()
        val boundaryMap = IntArray(source.length + 1) { -1 }
        val styleStarts = SteamRichTextStyle.entries.associateWith { ArrayDeque<Int>() }
        val lineStyles = mutableListOf<SteamRichTextStyle>()
        var index = 0

        fun mapSkipped(start: Int, endExclusive: Int, outputPosition: Int = output.length) {
            for (position in start..endExclusive.coerceAtMost(source.length)) {
                boundaryMap[position] = outputPosition
            }
        }

        fun appendPlain(value: String, sourceStart: Int, sourceEndExclusive: Int) {
            val outputStart = output.length
            mapSkipped(sourceStart, sourceEndExclusive, outputStart)
            output.append(value)
            boundaryMap[sourceEndExclusive.coerceAtMost(source.length)] = output.length
        }

        fun openStyle(style: SteamRichTextStyle) {
            styleStarts.getValue(style).addLast(output.length)
        }

        fun closeStyle(style: SteamRichTextStyle) {
            val starts = styleStarts.getValue(style)
            if (starts.isEmpty()) return
            val start = starts.removeLast()
            if (start < output.length) spans += SteamRichTextSpan(start, output.length, style)
        }

        fun closeLineStyles() {
            lineStyles.asReversed().forEach(::closeStyle)
            lineStyles.clear()
        }

        while (index < source.length) {
            if (boundaryMap[index] < 0) boundaryMap[index] = output.length

            if (source[index] == '\n') {
                closeLineStyles()
                appendPlain("\n", index, index + 1)
                index++
                continue
            }

            val lineStart = index == 0 || source[index - 1] == '\n'
            if (lineStart) {
                val quotePrefix = Regex("""^>+\s?""").find(source.substring(index))
                if (quotePrefix != null) {
                    mapSkipped(index, index + quotePrefix.value.length)
                    openStyle(SteamRichTextStyle.QUOTE)
                    lineStyles += SteamRichTextStyle.QUOTE
                    index += quotePrefix.value.length
                    continue
                }
                val headingPrefix = Regex("""^#{1,6}\s+""").find(source.substring(index))
                if (headingPrefix != null) {
                    mapSkipped(index, index + headingPrefix.value.length)
                    openStyle(SteamRichTextStyle.HEADING)
                    lineStyles += SteamRichTextStyle.HEADING
                    index += headingPrefix.value.length
                    continue
                }
                val listPrefix = Regex("""^[-+*]\s+""").find(source.substring(index))
                if (listPrefix != null) {
                    appendPlain("• ", index, index + listPrefix.value.length)
                    index += listPrefix.value.length
                    continue
                }
            }

            if (source[index] == '\\' && index + 1 < source.length) {
                appendPlain(source[index + 1].toString(), index, index + 2)
                index += 2
                continue
            }

            val bbcodeUrlMatch = bbcodeUrlPattern.find(source, index)
                ?.takeIf { it.range.first == index }
            if (bbcodeUrlMatch != null) {
                val explicitUrl = bbcodeUrlMatch.groupValues[1]
                val rawLabel = bbcodeUrlMatch.groupValues[2]
                val url = decodeEntities(explicitUrl.ifBlank { rawLabel }).trim()
                val labelDocument = parse(decodeEntities(rawLabel))
                val outputStart = output.length
                appendPlain(labelDocument.text.ifBlank { url }, index, bbcodeUrlMatch.range.last + 1)
                labelDocument.spans.forEach { span ->
                    spans += span.copy(
                        start = span.start + outputStart,
                        endExclusive = span.endExclusive + outputStart,
                    )
                }
                if (isSafeLink(url) && outputStart < output.length) {
                    discoveredLinks += SteamRichTextLink(outputStart, output.length, url)
                }
                index = bbcodeUrlMatch.range.last + 1
                continue
            }

            val markdownLinkMatch = markdownLinkAt(source, index)
            if (markdownLinkMatch != null) {
                val labelDocument = parse(markdownLinkMatch.label)
                val url = decodeEntities(markdownLinkMatch.url).trim()
                val outputStart = output.length
                appendPlain(labelDocument.text, index, markdownLinkMatch.endExclusive)
                labelDocument.spans.forEach { span ->
                    spans += span.copy(
                        start = span.start + outputStart,
                        endExclusive = span.endExclusive + outputStart,
                    )
                }
                if (isSafeLink(url) && outputStart < output.length) {
                    discoveredLinks += SteamRichTextLink(outputStart, output.length, url)
                }
                index = markdownLinkMatch.endExclusive
                continue
            }

            val bbcodeTagMatch = bbcodeTagPattern.find(source, index)
                ?.takeIf { it.range.first == index }
            if (bbcodeTagMatch != null) {
                val closing = bbcodeTagMatch.groupValues[1] == "/"
                val tag = bbcodeTagMatch.groupValues[2].lowercase()
                if (tag == "*") {
                    appendPlain("• ", index, bbcodeTagMatch.range.last + 1)
                    index = bbcodeTagMatch.range.last + 1
                    continue
                }
                if (tag == "list" || tag == "olist") {
                    mapSkipped(index, bbcodeTagMatch.range.last + 1)
                    index = bbcodeTagMatch.range.last + 1
                    continue
                }
                val style = bbcodeStyle(tag)
                val canConsume = closing && styleStarts.getValue(style).isNotEmpty() ||
                    !closing && hasBbcodeClosingTag(source, bbcodeTagMatch.range.last + 1, tag)
                if (canConsume) {
                    mapSkipped(index, bbcodeTagMatch.range.last + 1)
                    if (closing) closeStyle(style) else openStyle(style)
                    index = bbcodeTagMatch.range.last + 1
                    continue
                }
            }

            val htmlBreakMatch = htmlBreakPattern.find(source, index)
                ?.takeIf { it.range.first == index }
            if (htmlBreakMatch != null) {
                closeLineStyles()
                appendPlain("\n", index, htmlBreakMatch.range.last + 1)
                index = htmlBreakMatch.range.last + 1
                continue
            }

            val markdownMarker = markdownMarkerAt(source, index, styleStarts)
            if (markdownMarker != null) {
                val (marker, style) = markdownMarker
                val starts = styleStarts.getValue(style)
                val canConsume = starts.isNotEmpty() || hasUnescapedMarker(source, index + marker.length, marker)
                if (canConsume) {
                    mapSkipped(index, index + marker.length)
                    if (starts.isEmpty()) openStyle(style) else closeStyle(style)
                    index += marker.length
                    continue
                }
            }

            val entity = htmlEntityAt(source, index)
            if (entity != null) {
                appendPlain(entity.second, index, index + entity.first.length)
                index += entity.first.length
                continue
            }

            appendPlain(source[index].toString(), index, index + 1)
            index++
        }
        closeLineStyles()
        boundaryMap[source.length] = output.length
        var lastBoundary = 0
        boundaryMap.indices.forEach { position ->
            if (boundaryMap[position] < 0) boundaryMap[position] = lastBoundary
            lastBoundary = boundaryMap[position]
        }

        val remappedSourceLinks = sourceLinks.mapNotNull { link ->
            val start = boundaryMap[link.start.coerceIn(0, source.length)]
            val end = boundaryMap[link.endExclusive.coerceIn(0, source.length)]
            link.takeIf { isSafeLink(it.url) && start < end }
                ?.copy(start = start, endExclusive = end)
        }
        val links = (discoveredLinks + remappedSourceLinks).toMutableList()
        plainUrlPattern.findAll(output).forEach { match ->
            val trimmed = match.value.trimEnd('.', ',', '!', '?', ';', ':', ')', '，', '。', '！', '？', '；', '：')
            val end = match.range.first + trimmed.length
            if (trimmed.isNotEmpty() && isSafeLink(trimmed) &&
                links.none { rangesOverlap(match.range.first, end, it.start, it.endExclusive) }
            ) {
                links += SteamRichTextLink(match.range.first, end, trimmed)
            }
        }
        return SteamRichTextDocument(
            text = output.toString(),
            spans = spans.distinct().sortedWith(compareBy(SteamRichTextSpan::start, SteamRichTextSpan::endExclusive)),
            links = links.distinct().sortedBy(SteamRichTextLink::start),
        )
    }

    private fun markdownMarkerAt(
        source: String,
        index: Int,
        styleStarts: Map<SteamRichTextStyle, ArrayDeque<Int>>,
    ): Pair<String, SteamRichTextStyle>? {
        val candidates = listOf(
            "```" to SteamRichTextStyle.CODE,
            "**" to SteamRichTextStyle.BOLD,
            "__" to SteamRichTextStyle.BOLD,
            "~~" to SteamRichTextStyle.STRIKETHROUGH,
            "==" to SteamRichTextStyle.HIGHLIGHT,
            "`" to SteamRichTextStyle.CODE,
            "*" to SteamRichTextStyle.ITALIC,
            "_" to SteamRichTextStyle.ITALIC,
        )
        return candidates.firstOrNull { (marker, style) ->
            source.startsWith(marker, index) &&
                !(style == SteamRichTextStyle.CODE &&
                    styleStarts.getValue(SteamRichTextStyle.CODE).isNotEmpty() &&
                    marker != activeCodeMarker(source, index)) &&
                !(marker.contains('_') && isEmbeddedInWord(source, index, marker.length))
        }
    }

    private fun markdownLinkAt(source: String, index: Int): MarkdownLinkMatch? {
        if (source.getOrNull(index) != '[') return null
        var labelEnd = index + 1
        var escapedLabelCharacter = false
        while (labelEnd < source.length) {
            val current = source[labelEnd]
            if (escapedLabelCharacter) {
                escapedLabelCharacter = false
            } else {
                when (current) {
                    '\\' -> escapedLabelCharacter = true
                    '\n' -> return null
                    ']' -> break
                }
            }
            labelEnd++
        }
        if (labelEnd <= index + 1 || !source.startsWith("](", labelEnd)) return null

        var cursor = labelEnd + 2
        var nestedParentheses = 0
        var escaped = false
        while (cursor < source.length) {
            val current = source[cursor]
            if (escaped) {
                escaped = false
                cursor++
                continue
            }
            when (current) {
                '\\' -> escaped = true
                '(' -> nestedParentheses++
                ')' -> {
                    if (nestedParentheses == 0) {
                        val target = source.substring(labelEnd + 2, cursor).trim()
                        val url = MARKDOWN_LINK_TARGET.matchEntire(target)
                            ?.groupValues
                            ?.get(1)
                            ?.takeIf(String::isNotBlank)
                            ?: return null
                        return MarkdownLinkMatch(
                            label = source.substring(index + 1, labelEnd),
                            url = url,
                            endExclusive = cursor + 1,
                        )
                    }
                    nestedParentheses--
                }
                '\n' -> return null
            }
            cursor++
        }
        return null
    }

    private fun activeCodeMarker(source: String, index: Int): String =
        if (source.startsWith("```", index)) "```" else "`"

    private fun isEmbeddedInWord(source: String, index: Int, length: Int): Boolean {
        val before = source.getOrNull(index - 1)
        val after = source.getOrNull(index + length)
        return before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true
    }

    private fun hasUnescapedMarker(source: String, start: Int, marker: String): Boolean {
        var cursor = source.indexOf(marker, start)
        while (cursor >= 0) {
            if (cursor == 0 || source[cursor - 1] != '\\') return true
            cursor = source.indexOf(marker, cursor + marker.length)
        }
        return false
    }

    private fun hasBbcodeClosingTag(source: String, start: Int, tag: String): Boolean {
        val names = when (tag) {
            "s", "strike" -> "(?:s|strike)"
            else -> Regex.escape(tag)
        }
        return Regex("""\[/$names]""", RegexOption.IGNORE_CASE).containsMatchIn(source.substring(start))
    }

    private fun bbcodeStyle(tag: String): SteamRichTextStyle = when (tag) {
        "b" -> SteamRichTextStyle.BOLD
        "i" -> SteamRichTextStyle.ITALIC
        "u" -> SteamRichTextStyle.UNDERLINE
        "s", "strike" -> SteamRichTextStyle.STRIKETHROUGH
        "code" -> SteamRichTextStyle.CODE
        "quote" -> SteamRichTextStyle.QUOTE
        "spoiler" -> SteamRichTextStyle.SPOILER
        else -> SteamRichTextStyle.HEADING
    }

    private fun htmlEntityAt(source: String, index: Int): Pair<String, String>? =
        HTML_ENTITIES.entries.firstOrNull { source.startsWith(it.key, index, ignoreCase = true) }
            ?.let { it.key to it.value }

    private fun decodeEntities(value: String): String = HTML_ENTITIES.entries.fold(value) { text, entry ->
        text.replace(entry.key, entry.value, ignoreCase = true)
    }

    private fun isSafeLink(url: String): Boolean = runCatching {
        URI(url).scheme?.lowercase() in SAFE_SCHEMES
    }.getOrDefault(false)

    private fun rangesOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && secondStart < firstEnd

    private val SAFE_SCHEMES = setOf("http", "https", "steam")
    private val MARKDOWN_LINK_TARGET = Regex("""^(\S+?)(?:\s+[\"'][^\"']*[\"'])?$""")
    private val HTML_ENTITIES = linkedMapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
    )

    private data class MarkdownLinkMatch(
        val label: String,
        val url: String,
        val endExclusive: Int,
    )
}
