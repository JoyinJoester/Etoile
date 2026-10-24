package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.json.*
import takagi.ru.monica.github.domain.*

sealed interface ReviewWorkspaceConfirmation {
    data class ReplaceEditor(val target: GithubReviewLineComment) : ReviewWorkspaceConfirmation
    data class Remove(val index: Int) : ReviewWorkspaceConfirmation
    data object Clear : ReviewWorkspaceConfirmation
    data object DiscardLine : ReviewWorkspaceConfirmation
    data class Resolve(val id: String, val resolved: Boolean) : ReviewWorkspaceConfirmation
    data object Submit : ReviewWorkspaceConfirmation
}

@Immutable
data class PullRequestReviewWorkspaceState(
    val commitSha: String? = null,
    val comments: List<GithubReviewLineComment> = emptyList(),
    val body: String = "",
    val event: GithubReviewEvent = GithubReviewEvent.COMMENT,
    val editor: GithubReviewLineComment? = null,
    val editingIndex: Int? = null,
    val isOpen: Boolean = false,
    val isLineOpen: Boolean = false,
    val isSending: Boolean = false,
    val validationError: Boolean = false,
    val sendError: Boolean = false,
    val stale: Boolean = false,
    val sent: Boolean = false,
    val confirmation: ReviewWorkspaceConfirmation? = null,
    val threads: List<GithubReviewThreadStatus> = emptyList(),
    val nextCursor: String? = null,
    val loadingThreads: Boolean = false,
    val threadsError: Boolean = false,
    val threadBusy: Set<String> = emptySet(),
    val threadErrors: Set<String> = emptySet()
) {
    val hasDraft: Boolean get() = comments.isNotEmpty() || body.isNotBlank() || editor != null
}

sealed interface PullRequestReviewWorkspaceAction {
    data class OpenLine(val path: String, val line: Int, val side: GithubDiffSide) : PullRequestReviewWorkspaceAction
    data class LineBody(val body: String) : PullRequestReviewWorkspaceAction
    data object CloseLine : PullRequestReviewWorkspaceAction
    data object ResumeLine : PullRequestReviewWorkspaceAction
    data object QueueLine : PullRequestReviewWorkspaceAction
    data object SendLine : PullRequestReviewWorkspaceAction
    data object Open : PullRequestReviewWorkspaceAction
    data object Close : PullRequestReviewWorkspaceAction
    data class Body(val body: String) : PullRequestReviewWorkspaceAction
    data class Event(val event: GithubReviewEvent) : PullRequestReviewWorkspaceAction
    data class Edit(val index: Int) : PullRequestReviewWorkspaceAction
    data class Remove(val index: Int) : PullRequestReviewWorkspaceAction
    data object Clear : PullRequestReviewWorkspaceAction
    data object DiscardLine : PullRequestReviewWorkspaceAction
    data object Submit : PullRequestReviewWorkspaceAction
    data object Confirm : PullRequestReviewWorkspaceAction
    data object Cancel : PullRequestReviewWorkspaceAction
    data class Resolve(val id: String, val resolved: Boolean) : PullRequestReviewWorkspaceAction
    data class LoadThreads(val reset: Boolean = true) : PullRequestReviewWorkspaceAction
}

internal class ReviewWorkspaceDraftStore(private val saved: SavedStateHandle, private val scope: String) {
    fun save(account: Long?, state: PullRequestReviewWorkspaceState) {
        if (account == null) return
        val key = "$scope:$account"
        if (!state.hasDraft) { saved.remove<String>(key); return }
        saved[key] = buildJsonObject {
            put("sha", state.commitSha); put("body", state.body); put("event", state.event.name); put("open", state.isOpen); put("lineOpen", state.isLineOpen)
            put("comments", JsonArray(state.comments.map(::encode)))
            state.editor?.let { put("editor", encode(it)) }
            state.editingIndex?.let { put("index", it) }
        }.toString()
    }

    fun read(account: Long?): PullRequestReviewWorkspaceState {
        if (account == null) return PullRequestReviewWorkspaceState()
        return runCatching {
            val value = Json.parseToJsonElement(saved.get<String>("$scope:$account") ?: return PullRequestReviewWorkspaceState()).jsonObject
            val sha = value.getValue("sha").jsonPrimitive.content.also { require(it.isNotBlank() && it != "null" && it.length <= 128) }
            val comments = value.getValue("comments").jsonArray.map { decode(it.jsonObject).validated() }
            val editor = value["editor"]?.jsonObject?.let(::decode)
            val body = value.getValue("body").jsonPrimitive.content
            val index = value["index"]?.jsonPrimitive?.intOrNull?.takeIf { it in comments.indices }
            require(comments.size <= GithubReviewSubmissionDraft.MAX_COMMENTS &&
                body.length + comments.filterIndexed { i, _ -> i != index }.sumOf { it.body.length } + (editor?.body?.length ?: 0) <= GithubReviewSubmissionDraft.MAX_TOTAL_TEXT)
            PullRequestReviewWorkspaceState(sha, comments, body, GithubReviewEvent.valueOf(value.getValue("event").jsonPrimitive.content),
                editor, index, value["open"]?.jsonPrimitive?.booleanOrNull ?: false,
                value["lineOpen"]?.jsonPrimitive?.booleanOrNull ?: false)
        }.getOrDefault(PullRequestReviewWorkspaceState())
    }

    private fun encode(comment: GithubReviewLineComment) = buildJsonObject {
        put("path", comment.path); put("line", comment.line); put("side", comment.side.name); put("body", comment.body)
    }
    private fun decode(value: JsonObject) = GithubReviewLineComment(value.getValue("path").jsonPrimitive.content,
        value.getValue("line").jsonPrimitive.int, GithubDiffSide.valueOf(value.getValue("side").jsonPrimitive.content),
        value.getValue("body").jsonPrimitive.content).also {
        require(it.path.isNotBlank() && it.line > 0 && it.path.length <= 4096)
    }
}
