package takagi.ru.monica.github.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.*

/** Owns review draft writes separately from conversation replies. All callbacks run on the UI scope. */
internal class PullRequestReviewWorkspace(
    private val owner: String,
    private val name: String,
    private val number: Int,
    private val repository: GithubPullRequestsRepository,
    private val scope: CoroutineScope,
    saved: SavedStateHandle,
    private val detail: () -> PullRequestDetailUiState,
    private val publish: (PullRequestReviewWorkspaceState) -> Unit,
    private val onComment: (GithubPullRequestReviewComment) -> Unit,
    private val onReview: (GithubPullRequestReview) -> Unit
) {
    private var state = PullRequestReviewWorkspaceState()
    private var account: Long? = null
    private var revision = 0
    private var writeJob: Job? = null
    private var readJob: Job? = null
    private val threadJobs = mutableMapOf<String, Job>()
    private val store = ReviewWorkspaceDraftStore(saved, "review-workspace:$owner/$name#$number")
    private val visitedCursors = mutableSetOf<String>()

    private fun update(value: PullRequestReviewWorkspaceState) {
        state = value
        store.save(account, value)
        publish(value)
    }

    fun sessionChanged(id: Long?) {
        if (id == account) return
        store.save(account, state)
        revision++
        writeJob?.cancel(); readJob?.cancel()
        threadJobs.values.forEach(Job::cancel); threadJobs.clear(); visitedCursors.clear()
        account = id
        update(store.read(id))
        if (id != null) loadThreads(true)
    }

    private fun canWrite(): Boolean = account != null && detail().canReplyToReview &&
        detail().pullRequest?.state == GithubPullRequestState.OPEN && detail().pullRequest?.isMerged == false

    fun contextChanged() {
        if (account != null && state.hasDraft) {
            update(state.copy(stale = !canWrite() || state.commitSha != null && state.commitSha != detail().pullRequest?.head?.sha))
        }
    }

    private fun validTarget(target: GithubReviewLineComment): Boolean = detail().files.any { file ->
        file.filename == target.path && file.patch?.let { patch ->
            parsePullRequestDiff(patch).lines.any {
                (if (target.side == GithubDiffSide.LEFT) it.oldLine else it.newLine) == target.line
            }
        } == true
    }

    fun action(action: PullRequestReviewWorkspaceAction) {
        if (action is PullRequestReviewWorkspaceAction.LoadThreads) { loadThreads(action.reset); return }
        if (action == PullRequestReviewWorkspaceAction.Cancel) { update(state.copy(confirmation = null)); return }
        if (account == null || state.isSending) return
        when (action) {
            is PullRequestReviewWorkspaceAction.OpenLine -> {
                val target = GithubReviewLineComment(action.path, action.line, action.side, "")
                if (!canWrite() || !validTarget(target)) return
                if (state.editor?.body?.isNotBlank() == true && state.editor?.copy(body = "") != target) {
                    update(state.copy(confirmation = ReviewWorkspaceConfirmation.ReplaceEditor(target)))
                } else openLine(target)
            }
            is PullRequestReviewWorkspaceAction.LineBody -> if (state.editor != null && withinBudget(action.body, editor = true)) {
                update(state.copy(editor = state.editor?.copy(body = action.body), validationError = false, sendError = false))
            }
            PullRequestReviewWorkspaceAction.CloseLine -> update(state.copy(isLineOpen = false))
            PullRequestReviewWorkspaceAction.ResumeLine -> if (state.editor != null) update(state.copy(isLineOpen = true))
            PullRequestReviewWorkspaceAction.QueueLine -> queueLine()
            PullRequestReviewWorkspaceAction.SendLine -> send(batch = false)
            PullRequestReviewWorkspaceAction.Open -> update(state.copy(isOpen = true, sent = false,
                commitSha = if (state.hasDraft) state.commitSha else detail().pullRequest?.head?.sha,
                stale = if (state.hasDraft) state.stale else false))
            PullRequestReviewWorkspaceAction.Close -> update(state.copy(isOpen = false))
            is PullRequestReviewWorkspaceAction.Body -> if (withinBudget(action.body, editor = false)) {
                update(state.copy(body = action.body, commitSha = state.commitSha ?: detail().pullRequest?.head?.sha, validationError = false, sendError = false))
            }
            is PullRequestReviewWorkspaceAction.Event -> update(state.copy(event = action.event, validationError = false))
            is PullRequestReviewWorkspaceAction.Edit -> {
                val comment = state.comments.getOrNull(action.index) ?: return
                if (state.editor?.body?.isNotBlank() == true) return // Resume/queue the separate line draft first.
                update(state.copy(editor = comment, editingIndex = action.index, isLineOpen = true))
            }
            is PullRequestReviewWorkspaceAction.Remove -> if (action.index in state.comments.indices) {
                update(state.copy(confirmation = ReviewWorkspaceConfirmation.Remove(action.index)))
            }
            PullRequestReviewWorkspaceAction.Clear -> update(state.copy(confirmation = ReviewWorkspaceConfirmation.Clear))
            PullRequestReviewWorkspaceAction.DiscardLine -> update(state.copy(confirmation = ReviewWorkspaceConfirmation.DiscardLine))
            PullRequestReviewWorkspaceAction.Submit -> {
                if (!canWrite() || state.editor?.body?.isNotBlank() == true) { update(state.copy(validationError = true)); return }
                val draft = draft() ?: return
                if (draft.event != GithubReviewEvent.COMMENT && detail().viewerLogin == detail().pullRequest?.author?.login) {
                    update(state.copy(validationError = true)); return
                }
                update(state.copy(confirmation = ReviewWorkspaceConfirmation.Submit))
            }
            is PullRequestReviewWorkspaceAction.Resolve -> {
                val thread = state.threads.firstOrNull { it.id == action.id } ?: return
                if (action.id in state.threadBusy || thread.isResolved == action.resolved ||
                    !(if (action.resolved) thread.viewerCanResolve else thread.viewerCanUnresolve)) return
                update(state.copy(confirmation = ReviewWorkspaceConfirmation.Resolve(action.id, action.resolved)))
            }
            PullRequestReviewWorkspaceAction.Confirm -> confirm()
        }
    }

    private fun withinBudget(text: String, editor: Boolean): Boolean {
        val other = if (editor) state.body.length else state.editor?.body?.length ?: 0
        val queued = state.comments.filterIndexed { index, _ -> index != state.editingIndex }.sumOf { it.body.length }
        return text.length + other + queued <= GithubReviewSubmissionDraft.MAX_TOTAL_TEXT
    }

    private fun openLine(target: GithubReviewLineComment) {
        val sha = detail().pullRequest?.head?.sha ?: return
        if (state.commitSha != null && state.commitSha != sha && state.hasDraft) {
            update(state.copy(stale = true)); return
        }
        val same = state.editor?.copy(body = "") == target
        update(state.copy(commitSha = sha, editor = if (same) state.editor else target,
            editingIndex = if (same) state.editingIndex else null, isLineOpen = true, sent = false,
            confirmation = null, validationError = false, sendError = false, stale = false))
    }

    private fun queueLine() {
        if (!canWrite() || !state.isLineOpen) return
        val comment = runCatching { state.editor?.validated() }.getOrNull()
        if (comment == null || !validTarget(comment) || state.commitSha != detail().pullRequest?.head?.sha ||
            state.editingIndex == null && state.comments.size >= GithubReviewSubmissionDraft.MAX_COMMENTS) {
            update(state.copy(validationError = true)); return
        }
        val comments = state.comments.toMutableList()
        state.editingIndex?.let { comments[it] = comment } ?: comments.add(comment)
        update(state.copy(comments = comments, editor = null, editingIndex = null, isLineOpen = false,
            validationError = false, sendError = false))
    }

    private fun draft(): GithubReviewSubmissionDraft? = GithubReviewSubmissionDraft.fromInput(
        state.commitSha ?: detail().pullRequest?.head?.sha.orEmpty(), state.event, state.body, state.comments
    ).getOrElse { update(state.copy(validationError = true)); null }

    private fun confirm() {
        val confirmation = state.confirmation ?: return
        update(state.copy(confirmation = null)) // Consume before invoking any write.
        when (confirmation) {
            is ReviewWorkspaceConfirmation.ReplaceEditor -> if (canWrite() && validTarget(confirmation.target)) {
                update(state.copy(editor = null, editingIndex = null)); openLine(confirmation.target)
            }
            is ReviewWorkspaceConfirmation.Remove -> {
                if (state.editor != null) return
                update(state.copy(comments = state.comments.filterIndexed { index, _ -> index != confirmation.index }))
            }
            ReviewWorkspaceConfirmation.Clear -> update(PullRequestReviewWorkspaceState(
                isOpen = state.isOpen, threads = state.threads, nextCursor = state.nextCursor,
                loadingThreads = state.loadingThreads, threadsError = state.threadsError,
                threadBusy = state.threadBusy, threadErrors = state.threadErrors))
            ReviewWorkspaceConfirmation.DiscardLine -> update(state.copy(editor = null, editingIndex = null, isLineOpen = false))
            ReviewWorkspaceConfirmation.Submit -> send(batch = true)
            is ReviewWorkspaceConfirmation.Resolve -> resolve(confirmation.id, confirmation.resolved)
        }
    }

    private fun send(batch: Boolean) {
        if (!canWrite() || state.isSending || state.confirmation != null) return
        if (batch && (!state.isOpen || state.editor?.body?.isNotBlank() == true)) return
        if (!batch && (!state.isLineOpen || state.editingIndex != null)) return
        val submission = if (batch) draft() ?: return else null
        val line = if (!batch) runCatching { state.editor?.validated() }.getOrNull() else null
        if (!batch && (line == null || !validTarget(line))) { update(state.copy(validationError = true)); return }
        val sha = state.commitSha ?: detail().pullRequest?.head?.sha ?: return
        val version = revision
        val event = submission?.event
        if (event != null && event != GithubReviewEvent.COMMENT && detail().viewerLogin == detail().pullRequest?.author?.login) return
        update(state.copy(isSending = true, validationError = false, sendError = false, stale = false, sent = false))
        writeJob = scope.launch {
            val snapshot = repository.reviewSnapshot(owner, name, number)
            if (!isActive || version != revision) return@launch
            val current = snapshot.getOrNull()
            if (current == null) { update(state.copy(isSending = false, sendError = true)); return@launch }
            if (current.head.sha != sha || current.isLocked || current.isMerged || current.state != GithubPullRequestState.OPEN ||
                event != null && event != GithubReviewEvent.COMMENT && current.author.login == detail().viewerLogin) {
                update(state.copy(isSending = false, stale = true)); return@launch
            }
            if (batch) {
                val result = repository.submitReviewBatch(owner, name, number, checkNotNull(submission))
                if (!isActive || version != revision) return@launch
                result.fold(onSuccess = { review ->
                    update(state.copy(commitSha = null, comments = emptyList(), body = "", editor = null, editingIndex = null,
                        isLineOpen = false, isOpen = false, isSending = false, sent = true))
                    onReview(review)
                    loadThreads(true)
                }, onFailure = { update(state.copy(isSending = false, sendError = true)) })
            } else {
                val result = repository.createLineComment(owner, name, number, sha, checkNotNull(line))
                if (!isActive || version != revision) return@launch
                result.fold(onSuccess = { comment ->
                    update(state.copy(editor = null, editingIndex = null, isLineOpen = false, isSending = false, sent = true))
                    onComment(comment)
                    loadThreads(true)
                }, onFailure = { update(state.copy(isSending = false, sendError = true)) })
            }
        }
    }

    private fun loadThreads(reset: Boolean) {
        if (account == null || !reset && (state.loadingThreads || state.nextCursor == null)) return
        val cursor = if (reset) null else state.nextCursor
        readJob?.cancel()
        if (reset) visitedCursors.clear()
        val version = revision
        update(state.copy(loadingThreads = true, threadsError = false))
        readJob = scope.launch {
            val result = repository.reviewThreadStatuses(owner, name, number, cursor)
            if (!isActive || version != revision) return@launch
            result.fold(onSuccess = { page ->
                if (page.nextCursor != null && (page.nextCursor == cursor || page.nextCursor in visitedCursors)) {
                    update(state.copy(loadingThreads = false, threadsError = true)); return@fold
                }
                cursor?.let(visitedCursors::add)
                val existing = if (reset) emptyList() else state.threads
                update(state.copy(threads = (page.items + existing.filter { old -> page.items.none { it.id == old.id } }),
                    nextCursor = page.nextCursor, loadingThreads = false, threadsError = false))
            }, onFailure = { update(state.copy(loadingThreads = false, threadsError = true)) })
        }
    }

    private fun resolve(id: String, resolved: Boolean) {
        val thread = state.threads.firstOrNull { it.id == id } ?: return
        if (id in state.threadBusy || thread.isResolved == resolved || !(if (resolved) thread.viewerCanResolve else thread.viewerCanUnresolve)) return
        readJob?.cancel() // A read started before this mutation must not restore the old status.
        update(state.copy(loadingThreads = false, threadBusy = state.threadBusy + id, threadErrors = state.threadErrors - id))
        val version = revision
        threadJobs[id] = scope.launch {
            val result = repository.setReviewThreadResolved(id, resolved)
            if (!isActive || version != revision) return@launch
            readJob?.cancel()
            result.fold(onSuccess = { updated ->
                update(state.copy(threads = state.threads.map { if (it.id == id) updated else it },
                    loadingThreads = false, threadBusy = state.threadBusy - id))
            }, onFailure = { update(state.copy(loadingThreads = false, threadBusy = state.threadBusy - id, threadErrors = state.threadErrors + id)) })
            threadJobs.remove(id)
        }
    }
}
