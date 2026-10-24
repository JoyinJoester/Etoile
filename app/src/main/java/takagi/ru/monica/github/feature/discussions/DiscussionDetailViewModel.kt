package takagi.ru.monica.github.feature.discussions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import takagi.ru.monica.github.domain.*

data class DiscussionDetailDraft(
    val editTitle: String? = null,
    val editBody: String? = null,
    val reply: String = "",
    val threadReplies: Map<String, String> = emptyMap(),
    val commentEdits: Map<String, String> = emptyMap()
)

data class DiscussionDetailState(
    val discussion: GithubDiscussion? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val accountReady: Boolean = false,
    val draft: DiscussionDetailDraft = DiscussionDetailDraft(),
    val unconfirmedOperations: Set<String> = emptySet(),
    val readRevision: Long = 0
)

/** Retained destinations observe auth even while their composition is absent. */
class DiscussionDetailViewModel(
    private val owner: String,
    private val name: String,
    private val number: Int,
    private val accountId: Long?,
    private val repository: GithubDiscussionsRepository,
    private val sessions: StateFlow<GithubSession>,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val prefix = "discussion-detail:$accountId:$owner/$name:$number:"
    private val mutable = MutableStateFlow(DiscussionDetailState())
    val state = mutable.asStateFlow()
    private val pending = mutableSetOf<Job>()
    private val writes = mutableMapOf<String, Any>()
    private var loadJob: Job? = null
    private var observed = false
    private var ready = false
    private var generation = 0L

    init { viewModelScope.launch { sessions.collect { synchronizeSession() } } }

    private fun synchronizeSession(): Boolean {
        val allowed = accountId != null && (sessions.value as? GithubSession.SignedIn)?.account?.id == accountId
        if (!observed || ready != allowed) {
            observed = true
            cancelPendingRequests()
            ready = allowed
            mutable.value = DiscussionDetailState(accountReady = allowed,
                draft = if (allowed) restoreDraft() else DiscussionDetailDraft(),
                unconfirmedOperations = if (allowed) restoreUnconfirmed() else emptySet())
            if (allowed) load()
            // A -> B -> A can be conflated by StateFlow while an entry point already
            // observed B synchronously. Reconcile that eager invalidation on the next turn.
            viewModelScope.launch { yield(); synchronizeSession() }
        }
        return allowed
    }

    fun cancelPendingRequests() {
        generation++
        loadJob?.cancel()
        pending.toList().forEach { it.cancel() }
        writes.clear()
        mutable.update { it.copy(loading = false, unconfirmedOperations =
            if (ready && (sessions.value as? GithubSession.SignedIn)?.account?.id == accountId) restoreUnconfirmed() else emptySet()) }
    }

    fun onEntered() {
        if (synchronizeSession() && mutable.value.discussion == null && !mutable.value.loading) load()
    }

    fun load() {
        if (!synchronizeSession() || mutable.value.loading) return
        val ticket = generation
        mutable.update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            try {
                guarded(ticket) { repository.detail(owner, name, number) }.fold(
                    onSuccess = { value -> mutable.update { it.copy(discussion = value, loading = false, readRevision = it.readRevision + 1) } },
                    onFailure = { mutable.update { it.copy(loading = false, failed = true) } })
            } finally {
                if (ticket == generation) mutable.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun <T> guarded(ticket: Long = generation, block: suspend () -> Result<T>): Result<T> = coroutineScope {
        if (!synchronizeSession() || ticket != generation) throw CancellationException("Discussion account changed")
        val requestJob = currentCoroutineContext().job
        pending.add(requestJob)
        try {
            currentCoroutineContext().ensureActive()
            val result = block()
            currentCoroutineContext().ensureActive()
            if (!synchronizeSession() || ticket != generation) throw CancellationException("Discussion account changed")
            result
        } finally { pending.remove(requestJob) }
    }

    private fun discussionId() = mutable.value.discussion?.id
        ?: throw CancellationException("Discussion is not available")

    private suspend fun <T> write(key: String, block: suspend () -> Result<T>): Result<T> {
        var started = false
        val result = guarded {
            if (key in writes) return@guarded Result.failure(IllegalStateException("Discussion operation already in progress"))
            val marker = Any()
            writes[key] = marker
            started = true
            // Survives task recreation and account switches. A cancelled request may
            // already have committed remotely, so only an accepted success clears it.
            saved[prefix + "unconfirmed"] = ArrayList(restoreUnconfirmed() + key)
            try { block() } finally { if (writes[key] === marker) writes.remove(key) }
        }
        if (started) {
            if (result.isSuccess) saved[prefix + "unconfirmed"] = ArrayList(restoreUnconfirmed() - key)
            mutable.update { it.copy(unconfirmedOperations = restoreUnconfirmed()) }
        }
        return result
    }

    private fun restoreUnconfirmed() = saved.get<ArrayList<String>>(prefix + "unconfirmed").orEmpty().toSet()

    /** Explicit local acknowledgement after inspecting the remote state; never resends a request. */
    fun acknowledgeUnconfirmed() {
        if (!synchronizeSession() || writes.isNotEmpty()) return
        saved.remove<ArrayList<String>>(prefix + "unconfirmed")
        mutable.update { it.copy(unconfirmedOperations = emptySet()) }
    }

    suspend fun comments(cursor: String?): Result<GithubDiscussionComments> = guarded { repository.comments(discussionId(), cursor) }
    suspend fun replies(id: String, cursor: String?): Result<GithubDiscussionComments> = guarded { repository.replies(id, cursor) }
    suspend fun editComment(id: String, body: String): Result<GithubDiscussionComment> = write("comment:$id") {
        repository.editComment(id, body)
    }.onSuccess { clearCommentEdit(id) }
    suspend fun deleteComment(id: String): Result<Unit> = write("comment:$id") { repository.deleteComment(id) }
        .onSuccess { clearCommentEdit(id) }
    suspend fun markAnswer(id: String, answered: Boolean): Result<Unit> = write("answer") { repository.markAnswer(id, answered) }

    suspend fun edit(title: String, body: String): Result<GithubDiscussion> = guarded {
        val current = mutable.value.discussion
        if (current?.canEdit != true) return@guarded Result.failure(IllegalStateException("Discussion editing unavailable"))
        write("discussion") { repository.edit(current.id, title, body) }
    }.onSuccess { value ->
        saved.remove<String>(prefix + "edit-title")
        saved.remove<String>(prefix + "edit-body")
        mutable.update { it.copy(discussion = value, draft = it.draft.copy(editTitle = null, editBody = null)) }
    }

    suspend fun reply(body: String): Result<String> = write("reply") { repository.reply(discussionId(), body) }.onSuccess {
        saveReply("")
    }

    suspend fun replyToComment(id: String, body: String): Result<String> = write("reply:$id") {
        repository.replyToComment(discussionId(), id, body)
    }.onSuccess { saveThreadReply(id, "") }

    fun saveEdit(title: String, body: String) {
        if (!synchronizeSession() || title.length > 256 || body.length > 65536) return
        saved[prefix + "edit-title"] = title
        saved[prefix + "edit-body"] = body
        mutable.update { it.copy(draft = it.draft.copy(editTitle = title, editBody = body)) }
    }

    fun saveReply(body: String) {
        if (!synchronizeSession() || body.length > 65536) return
        saved[prefix + "reply"] = body
        mutable.update { it.copy(draft = it.draft.copy(reply = body)) }
    }

    fun saveThreadReply(id: String, body: String) {
        if (!synchronizeSession() || id.isBlank() || body.length > 65536) return
        val drafts = mutable.value.draft.threadReplies.toMutableMap()
        if (body.isEmpty()) drafts.remove(id) else drafts[id] = body
        saved[prefix + "threads"] = ArrayList(drafts.flatMap { listOf(it.key, it.value) })
        mutable.update { it.copy(draft = it.draft.copy(threadReplies = drafts)) }
    }

    fun saveCommentEdit(id: String, body: String) {
        if (!synchronizeSession() || id.isBlank() || body.length > 65536) return
        val drafts = mutable.value.draft.commentEdits + (id to body)
        saved[prefix + "comment-edits"] = ArrayList(drafts.flatMap { listOf(it.key, it.value) })
        mutable.update { it.copy(draft = it.draft.copy(commentEdits = drafts)) }
    }

    private fun clearCommentEdit(id: String) {
        if (!synchronizeSession()) return
        val drafts = mutable.value.draft.commentEdits - id
        saved[prefix + "comment-edits"] = ArrayList(drafts.flatMap { listOf(it.key, it.value) })
        mutable.update { it.copy(draft = it.draft.copy(commentEdits = drafts)) }
    }

    private fun restoreDraft() = DiscussionDetailDraft(
        editTitle = saved[prefix + "edit-title"], editBody = saved[prefix + "edit-body"],
        reply = saved[prefix + "reply"] ?: "",
        threadReplies = saved.get<ArrayList<String>>(prefix + "threads").orEmpty().chunked(2)
            .filter { it.size == 2 }.associate { it[0] to it[1] },
        commentEdits = saved.get<ArrayList<String>>(prefix + "comment-edits").orEmpty().chunked(2)
            .filter { it.size == 2 }.associate { it[0] to it[1] }
    )
}
