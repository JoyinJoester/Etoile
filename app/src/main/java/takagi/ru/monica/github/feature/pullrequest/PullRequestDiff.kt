package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.runtime.Immutable

internal const val DEFAULT_PULL_REQUEST_DIFF_MAX_LINES = 240
private const val DEFAULT_PULL_REQUEST_DIFF_MAX_LINE_CHARACTERS = 4_000

internal enum class PullRequestDiffLineKind {
    CONTEXT,
    ADDITION,
    DELETION,
    HUNK,
    METADATA
}

@Immutable
internal data class PullRequestDiffLine(
    val text: String,
    val kind: PullRequestDiffLineKind,
    val oldLine: Int? = null,
    val newLine: Int? = null
)

@Immutable
internal data class PullRequestDiff(
    val lines: List<PullRequestDiffLine>,
    val isTruncated: Boolean
)

internal fun parsePullRequestDiff(
    patch: String,
    maxLines: Int = DEFAULT_PULL_REQUEST_DIFF_MAX_LINES,
    maxLineCharacters: Int = DEFAULT_PULL_REQUEST_DIFF_MAX_LINE_CHARACTERS
): PullRequestDiff {
    if (patch.isEmpty()) return PullRequestDiff(emptyList(), isTruncated = false)

    val safeLineLimit = maxLines.coerceAtLeast(1)
    val safeCharacterLimit = maxLineCharacters.coerceAtLeast(80)
    val sourceLines = patch.lineSequence().take(safeLineLimit + 1).toList()
    var characterTruncated = false
    var oldLine = 0L
    var newLine = 0L
    var oldRemaining = 0L
    var newRemaining = 0L
    val lines = sourceLines.take(safeLineLimit).map { source ->
        val text = if (source.length > safeCharacterLimit) {
            characterTruncated = true
            source.take(safeCharacterLimit) + "…"
        } else {
            source
        }
        val header = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$").matchEntire(source)
        if (source.startsWith("@@")) {
            oldLine = header?.groupValues?.get(1)?.toLongOrNull() ?: 0
            newLine = header?.groupValues?.get(3)?.toLongOrNull() ?: 0
            oldRemaining = header?.groupValues?.get(2)?.takeIf(String::isNotEmpty)?.toLongOrNull() ?: if (header != null) 1 else 0
            newRemaining = header?.groupValues?.get(4)?.takeIf(String::isNotEmpty)?.toLongOrNull() ?: if (header != null) 1 else 0
            PullRequestDiffLine(text, PullRequestDiffLineKind.HUNK)
        } else {
            val inHunk = oldRemaining > 0 || newRemaining > 0
            val kind = if (inHunk) when (source.firstOrNull()) {
                '+' -> PullRequestDiffLineKind.ADDITION
                '-' -> PullRequestDiffLineKind.DELETION
                ' ' -> PullRequestDiffLineKind.CONTEXT
                else -> PullRequestDiffLineKind.METADATA
            } else source.pullRequestDiffLineKind()
            if (inHunk && kind == PullRequestDiffLineKind.METADATA && !source.startsWith("\\ No newline at end of file")) {
                oldRemaining = 0; newRemaining = 0
            }
            val consumesOld = inHunk && (kind == PullRequestDiffLineKind.DELETION || kind == PullRequestDiffLineKind.CONTEXT)
            val consumesNew = inHunk && (kind == PullRequestDiffLineKind.ADDITION || kind == PullRequestDiffLineKind.CONTEXT)
            val valid = (!consumesOld || oldRemaining > 0 && oldLine in 1..Int.MAX_VALUE.toLong()) &&
                (!consumesNew || newRemaining > 0 && newLine in 1..Int.MAX_VALUE.toLong())
            val row = PullRequestDiffLine(text, kind,
                oldLine.takeIf { consumesOld && valid }?.toInt(), newLine.takeIf { consumesNew && valid }?.toInt())
            if (consumesOld) { oldLine++; oldRemaining-- }
            if (consumesNew) { newLine++; newRemaining-- }
            row
        }
    }
    return PullRequestDiff(
        lines = lines,
        isTruncated = sourceLines.size > safeLineLimit || characterTruncated
    )
}

private fun String.pullRequestDiffLineKind(): PullRequestDiffLineKind = when {
    startsWith("@@") -> PullRequestDiffLineKind.HUNK
    startsWith("+++") || startsWith("---") || startsWith("\\") ||
        startsWith("diff --git") || startsWith("index ") -> PullRequestDiffLineKind.METADATA
    startsWith("+") -> PullRequestDiffLineKind.ADDITION
    startsWith("-") -> PullRequestDiffLineKind.DELETION
    else -> PullRequestDiffLineKind.CONTEXT
}
