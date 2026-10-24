package takagi.ru.monica.gitlab.feature

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.gitlab.domain.*

data class GitLabConversationState(
    val account: GitLabAccount? = null,
    val kind: GitLabConversationKind,
    val item: GitLabConversation? = null,
    val loading: Boolean = false,
    val error: GitLabReadError? = null,
    val notes: List<GitLabNote> = emptyList(),
    val notesNext: Int? = null,
    val notesLoading: Boolean = false,
    val notesError: GitLabReadError? = null,
    val showDiff: Boolean = false,
    val diffs: List<GitLabDiff> = emptyList(),
    val diffsNext: Int? = null,
    val diffsLoading: Boolean = false,
    val diffsError: GitLabReadError? = null,
    val editing: Boolean = false,
    val draft: GitLabConversationDraft = GitLabConversationDraft("", ""),
    val comment: String = "",
    val writing: Boolean = false,
    val writeError: GitLabReadError? = null,
    val writeSucceeded: Boolean = false
)

class GitLabConversationViewModel(
    private val key: String,
    private val projectId: Long,
    private val kind: GitLabConversationKind,
    private val number: Long,
    auth: GitLabAuthRepository,
    private val repository: GitLabWorkRepository,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabConversationState(kind = kind))
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var detailVersion = 0L; private var notesVersion = 0L; private var diffsVersion = 0L
    private var detailJob: Job? = null; private var notesJob: Job? = null; private var diffsJob: Job? = null; private var writeJob: Job? = null
    private val prefix = "$key:$projectId:${kind.name}:$number:"

    init {
        viewModelScope.launch {
            auth.accounts.collect { accounts ->
                val account = accounts.accounts.firstOrNull { it.key == key && accounts.activeKey == key }
                if (account != state.value.account) {
                    epoch++; cancelReads(); writeJob?.cancel()
                    mutableState.value = GitLabConversationState(kind = kind, account = account)
                    if (account != null) {
                        mutableState.update { it.copy(comment = saved[prefix + "comment"] ?: "", editing = saved[prefix + "editing"] ?: false,
                            draft = GitLabConversationDraft(saved[prefix + "title"] ?: "", saved[prefix + "body"] ?: "")) }
                        refresh()
                    }
                }
            }
        }
    }
    fun refresh() {
        if (state.value.account == null || state.value.writing || state.value.loading) return
        cancelReads()
        val version = detailVersion; val revision = epoch
        mutableState.update { it.copy(loading = true, error = null) }
        detailJob = viewModelScope.launch {
            val result = repository.conversation(key, projectId, kind, number)
            currentCoroutineContext().ensureActive()
            if (version != detailVersion || revision != epoch) return@launch
            result.fold(onSuccess = { item ->
                mutableState.update { it.copy(item = item, loading = false,
                    diffs = if (it.item?.sha == item.sha) it.diffs else emptyList(),
                    diffsNext = if (it.item?.sha == item.sha) it.diffsNext else null) }
                notes(); if (state.value.showDiff) diffs()
            }, onFailure = { error -> mutableState.update { it.copy(loading = false, error = error.gitLabError(),
                item = if (error.gitLabError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)) null else it.item,
                notes = if (error.gitLabError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)) emptyList() else it.notes,
                diffs = if (error.gitLabError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)) emptyList() else it.diffs) } })
        }
    }
    fun notes(more: Boolean = false) {
        val before = state.value
        if (before.account == null || before.writing || before.notesLoading || before.item == null) return
        val page = if (more) before.notesNext ?: return else 1
        notesJob?.cancel(); val version = ++notesVersion; val revision = epoch
        mutableState.update { it.copy(notesLoading = true, notesError = null) }
        notesJob = viewModelScope.launch {
            val result = repository.notes(key, projectId, kind, number, page)
            currentCoroutineContext().ensureActive()
            if (version != notesVersion || revision != epoch) return@launch
            result.fold(onSuccess = { response -> mutableState.update { it.copy(notes = ((if (more) it.notes else emptyList()) + response.items).distinctBy(GitLabNote::id),
                notesNext = response.nextPage, notesLoading = false) } },
                onFailure = { error -> mutableState.update { it.copy(notesLoading = false, notesError = error.gitLabError()) } })
        }
    }
    fun showDiff(value: Boolean) {
        if (kind != GitLabConversationKind.MERGE_REQUEST) return
        mutableState.update { it.copy(showDiff = value) }
        if (value && state.value.diffs.isEmpty()) diffs()
    }
    fun diffs(more: Boolean = false) {
        val before = state.value
        if (kind != GitLabConversationKind.MERGE_REQUEST || before.account == null || before.writing || before.diffsLoading || before.item == null) return
        val page = if (more) before.diffsNext ?: return else 1
        diffsJob?.cancel(); val version = ++diffsVersion; val revision = epoch
        mutableState.update { it.copy(diffsLoading = true, diffsError = null) }
        diffsJob = viewModelScope.launch {
            val result = repository.diffs(key, projectId, number, before.item.sha, page)
            currentCoroutineContext().ensureActive()
            if (version != diffsVersion || revision != epoch) return@launch
            result.fold(onSuccess = { response -> mutableState.update { it.copy(diffs = ((if (more) it.diffs else emptyList()) + response.items).distinctBy { diff -> diff.oldPath to diff.newPath },
                diffsNext = response.nextPage, diffsLoading = false) } },
                onFailure = { error -> mutableState.update { it.copy(diffsLoading = false, diffsError = error.gitLabError()) } })
        }
    }
    fun editing(value: Boolean) {
        if (state.value.writing || state.value.item == null) return
        if (value && !state.value.editing && state.value.draft.title.isEmpty()) {
            val item = state.value.item!!
            mutableState.update { it.copy(draft = GitLabConversationDraft(item.title, item.description)) }
        }
        mutableState.update { it.copy(editing = value) }; saveDraft()
    }
    fun draftChanged(draft: GitLabConversationDraft) {
        if (!state.value.writing) { mutableState.update { it.copy(draft = draft.copy(title = draft.title.take(255), description = draft.description.take(65_536))) }; saveDraft() }
    }
    fun commentChanged(value: String) {
        if (!state.value.writing) { mutableState.update { it.copy(comment = value.take(65_536)) }; saved[prefix + "comment"] = state.value.comment }
    }
    fun onAction(action: GitLabConversationAction) {
        when (action) {
            GitLabConversationAction.Refresh -> refresh()
            is GitLabConversationAction.Notes -> notes(action.more)
            is GitLabConversationAction.Diffs -> diffs(action.more)
            is GitLabConversationAction.ShowDiff -> showDiff(action.show)
            is GitLabConversationAction.Editing -> editing(action.open)
            is GitLabConversationAction.Draft -> draftChanged(action.value.copy(allowQuickActions = false))
            is GitLabConversationAction.Comment -> commentChanged(action.value)
            is GitLabConversationAction.Save -> save(action.confirmedQuickActions)
            is GitLabConversationAction.Send -> sendComment(action.confirmedQuickActions)
            is GitLabConversationAction.ChangeState -> changeState(action.expected)
            is GitLabConversationAction.Merge -> merge(action.target)
        }
    }
    fun save(confirmedQuickActions: Boolean = false) {
        val before = state.value
        if (!before.editing || !before.draft.isValid(kind, false) || before.item?.state == "merged") return
        if (gitLabHasQuickActions(before.draft.description) && !confirmedQuickActions) return
        write { repository.edit(key, projectId, kind, number, before.draft.copy(allowQuickActions = confirmedQuickActions)).gitLabApply { item ->
            mutableState.update { it.copy(item = item, editing = false, draft = GitLabConversationDraft("", "")) }; saveDraft()
        } }
    }
    fun sendComment(confirmedQuickActions: Boolean = false) {
        val before = state.value
        if (before.comment.isBlank() || before.item == null || before.item.discussionLocked) return
        if (gitLabHasQuickActions(before.comment) && !confirmedQuickActions) return
        write(refreshNotes = false) { repository.comment(key, projectId, kind, number, before.comment, confirmedQuickActions).gitLabApply { note ->
            mutableState.update { it.copy(comment = "", notes = (listOf(note) + it.notes).distinctBy(GitLabNote::id)) }
            saved[prefix + "comment"] = ""
        } }
    }
    fun changeState(expectedState: String) {
        val item = state.value.item ?: return
        if (item.state != expectedState || expectedState !in setOf("opened", "closed")) return
        write { repository.changeState(key, projectId, kind, number, close = expectedState == "opened").gitLabApply { result -> mutableState.update { it.copy(item = result) } } }
    }
    fun merge(target: GitLabMergeTarget) {
        val item = state.value.item ?: return
        if (kind != GitLabConversationKind.MERGE_REQUEST || !item.canMerge || target != GitLabMergeTarget(number, item.sha, item.sourceBranch, item.targetBranch)) return
        write { repository.merge(key, projectId, target).gitLabApply { result ->
            mutableState.update { it.copy(item = result, editing = false) }; saveDraft()
        } }
    }
    private fun write(refreshNotes: Boolean = true, block: suspend () -> Result<Unit>) {
        if (state.value.account == null || state.value.item == null || state.value.writing) return
        cancelReads(); val revision = epoch
        mutableState.update { it.copy(writing = true, writeError = null, writeSucceeded = false) }
        writeJob = viewModelScope.launch {
            val result = block()
            currentCoroutineContext().ensureActive()
            if (revision != epoch) return@launch
            mutableState.update { it.copy(writing = false, writeError = result.exceptionOrNull()?.gitLabError(), writeSucceeded = result.isSuccess) }
            if (result.isSuccess) { if (refreshNotes) notes(); if (state.value.showDiff) diffs() }
        }
    }
    private fun cancelReads() {
        detailVersion++; notesVersion++; diffsVersion++
        detailJob?.cancel(); notesJob?.cancel(); diffsJob?.cancel()
        mutableState.update { it.copy(loading = false, notesLoading = false, diffsLoading = false) }
    }
    private fun saveDraft() {
        saved[prefix + "editing"] = state.value.editing; saved[prefix + "title"] = state.value.draft.title; saved[prefix + "body"] = state.value.draft.description
    }
}
