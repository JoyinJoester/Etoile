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
    val kind: PullRequestDiffLineKind
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
    val lines = sourceLines.take(safeLineLimit).map { source ->
        val text = if (source.length > safeCharacterLimit) {
            characterTruncated = true
            source.take(safeCharacterLimit) + "…"
        } else {
            source
        }
        PullRequestDiffLine(text = text, kind = source.pullRequestDiffLineKind())
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
