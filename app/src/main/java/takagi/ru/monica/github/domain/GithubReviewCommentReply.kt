package takagi.ru.monica.github.domain

class GithubReviewCommentReplyDraft private constructor(val threadId: Long, val body: String) {
    companion object {
        const val MAX_BODY_LENGTH = 65_536

        fun fromInput(threadId: Long, body: String): Result<GithubReviewCommentReplyDraft> = runCatching {
            val normalized = body.trim()
            require(threadId > 0)
            require(normalized.isNotEmpty() && normalized.length <= MAX_BODY_LENGTH)
            GithubReviewCommentReplyDraft(threadId, normalized)
        }
    }
}

data class GithubReviewCommentThread(
    val id: Long,
    val comments: List<GithubPullRequestReviewComment>
) {
    val hasRoot: Boolean get() = comments.any { it.id == id }
}

/** Replies can arrive on a different page; keep orphan replies visible until their root loads. */
fun List<GithubPullRequestReviewComment>.reviewThreads(): List<GithubReviewCommentThread> =
    distinctBy(GithubPullRequestReviewComment::id)
        .groupBy(GithubPullRequestReviewComment::threadId)
        .map { (id, comments) ->
            GithubReviewCommentThread(id, comments.sortedBy { if (it.id == id) 0 else 1 })
        }
