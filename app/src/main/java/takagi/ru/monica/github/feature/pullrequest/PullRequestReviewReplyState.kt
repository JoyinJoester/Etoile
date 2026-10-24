package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import takagi.ru.monica.github.domain.GithubReviewCommentReplyDraft

@Immutable
data class PullRequestReviewReplyUiState(
    val threadId: Long? = null,
    val body: String = "",
    val isEditorOpen: Boolean = false,
    val pendingThreadId: Long? = null,
    val isSubmitting: Boolean = false,
    val validationError: Boolean = false,
    val submitError: Boolean = false,
    val sent: Boolean = false
)

/** Only draft primitives survive task restoration; a pending POST or confirmation never does. */
internal class PullRequestReviewReplyDraftStore(
    private val saved: SavedStateHandle,
    private val scope: String
) {
    fun read(accountId: Long?): PullRequestReviewReplyUiState {
        if (accountId == null) return PullRequestReviewReplyUiState()
        val key = "$scope:$accountId"
        val id = saved.get<Long>("$key:thread")?.takeIf { it > 0 } ?: return PullRequestReviewReplyUiState()
        return PullRequestReviewReplyUiState(
            threadId = id,
            body = saved.get<String>("$key:body").orEmpty().take(GithubReviewCommentReplyDraft.MAX_BODY_LENGTH),
            isEditorOpen = saved["$key:open"] ?: false
        )
    }

    fun save(accountId: Long?, draft: PullRequestReviewReplyUiState) {
        if (accountId == null) return
        val key = "$scope:$accountId"
        if (draft.threadId == null) {
            saved.remove<Long>("$key:thread")
            saved.remove<String>("$key:body")
            saved.remove<Boolean>("$key:open")
        } else {
            saved["$key:thread"] = draft.threadId
            saved["$key:body"] = draft.body
            saved["$key:open"] = draft.isEditorOpen
        }
    }
}
