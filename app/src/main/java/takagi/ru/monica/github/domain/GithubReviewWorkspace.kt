package takagi.ru.monica.github.domain

enum class GithubDiffSide { LEFT, RIGHT }

data class GithubReviewLineComment(val path: String, val line: Int, val side: GithubDiffSide, val body: String) {
    fun validated(): GithubReviewLineComment {
        require(path.isNotBlank() && path.length <= 4096 && path.none { it == '\u0000' || it == '\n' || it == '\r' })
        require(line > 0)
        val text = body.trim()
        require(text.isNotEmpty() && text.length <= 65_536)
        return copy(body = text)
    }
}

class GithubReviewSubmissionDraft private constructor(
    val commitSha: String,
    val event: GithubReviewEvent,
    val body: String?,
    val comments: List<GithubReviewLineComment>
) {
    companion object {
        const val MAX_COMMENTS = 50
        const val MAX_TOTAL_TEXT = 65_536

        fun fromInput(commitSha: String, event: GithubReviewEvent, body: String, comments: List<GithubReviewLineComment>) = runCatching {
            require(commitSha.isNotBlank() && commitSha.length <= 128)
            require(comments.size <= MAX_COMMENTS)
            val normalized = comments.map { it.validated() }
            val summary = body.trim().takeIf(String::isNotEmpty)
            require((summary?.length ?: 0) + normalized.sumOf { it.body.length } <= MAX_TOTAL_TEXT)
            if (event == GithubReviewEvent.REQUEST_CHANGES) require(summary != null)
            if (event == GithubReviewEvent.COMMENT) require(summary != null || normalized.isNotEmpty())
            GithubReviewSubmissionDraft(commitSha.trim(), event, summary, normalized)
        }
    }
}

/** GraphQL thread identity is distinct from the REST root comment database ID. */
data class GithubReviewThreadStatus(
    val id: String,
    val rootCommentId: Long,
    val isResolved: Boolean,
    val isOutdated: Boolean,
    val viewerCanResolve: Boolean,
    val viewerCanUnresolve: Boolean
)

data class GithubReviewThreadPage(val items: List<GithubReviewThreadStatus>, val nextCursor: String?)
