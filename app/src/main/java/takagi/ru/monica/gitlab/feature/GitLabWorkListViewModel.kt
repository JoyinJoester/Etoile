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

data class GitLabWorkListState(
    val account: GitLabAccount? = null,
    val section: GitLabWorkSection = GitLabWorkSection.ISSUES,
    val filter: String = "opened",
    val searchInput: String = "",
    val query: String = "",
    val conversations: List<GitLabConversation> = emptyList(),
    val pipelines: List<GitLabPipeline> = emptyList(),
    val nextPage: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: GitLabReadError? = null,
    val editorOpen: Boolean = false,
    val draft: GitLabConversationDraft = GitLabConversationDraft("", ""),
    val writing: Boolean = false,
    val writeError: GitLabReadError? = null,
    val created: GitLabConversation? = null,
    val createdPipeline: GitLabPipeline? = null
) {
    val kind get() = if (section == GitLabWorkSection.ISSUES) GitLabConversationKind.ISSUE else GitLabConversationKind.MERGE_REQUEST
}

class GitLabWorkListViewModel(
    private val key: String,
    private val projectId: Long,
    private val initialSection: GitLabWorkSection,
    auth: GitLabAuthRepository,
    private val repository: GitLabWorkRepository,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabWorkListState(section = initialSection))
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var readVersion = 0L
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var loadedPages = 0
    private var retryRefresh = false

    init {
        viewModelScope.launch {
            auth.accounts.collect { accounts ->
                val account = accounts.accounts.firstOrNull { it.key == key && accounts.activeKey == key }
                if (account != state.value.account) {
                    epoch++; readVersion++; readJob?.cancel(); writeJob?.cancel()
                    loadedPages = 0; retryRefresh = false
                    mutableState.value = GitLabWorkListState(account = account, section = initialSection)
                    if (account != null) { restoreDraft(); load() }
                }
            }
        }
    }

    fun sectionChanged(section: GitLabWorkSection) {
        if (state.value.writing || state.value.section == section) return
        saveDraft()
        mutableState.update { GitLabWorkListState(account = it.account, section = section) }
        restoreDraft(); load()
    }
    fun searchChanged(value: String) { mutableState.update { it.copy(searchInput = value.take(if (it.section == GitLabWorkSection.PIPELINES) 1024 else 200)) } }
    fun search() { if (!state.value.writing) { mutableState.update { it.copy(query = it.searchInput.trim()) }; load() } }
    fun filterChanged(value: String) {
        val allowed = if (state.value.kind == GitLabConversationKind.ISSUE) setOf("opened", "closed", "all") else setOf("opened", "closed", "merged", "all")
        if (state.value.writing || value !in allowed) return
        mutableState.update { it.copy(filter = value) }; load()
    }
    fun refresh() { if (!state.value.writing && !state.value.loading && !state.value.loadingMore) load(refreshLoaded = true) }
    fun more() { if (!state.value.loading && !state.value.loadingMore && !state.value.writing) load(more = true) }
    fun retry() { if (retryRefresh) refresh() else if (state.value.conversations.isNotEmpty() || state.value.pipelines.isNotEmpty()) more() else refresh() }
    fun editor(open: Boolean) { if (!state.value.writing) { mutableState.update { it.copy(editorOpen = open) }; saveDraft() } }
    fun draftChanged(draft: GitLabConversationDraft) {
        if (state.value.writing) return
        mutableState.update { it.copy(draft = draft.copy(title = draft.title.take(255), description = draft.description.take(65_536),
            sourceBranch = draft.sourceBranch.take(1024), targetBranch = draft.targetBranch.take(1024))) }; saveDraft()
    }
    fun consumeCreated() { mutableState.update { it.copy(created = null, createdPipeline = null) } }

    fun onAction(action: GitLabWorkListAction) {
        when (action) {
            is GitLabWorkListAction.Section -> sectionChanged(action.value)
            is GitLabWorkListAction.SearchChanged -> searchChanged(action.value)
            GitLabWorkListAction.Search -> search()
            is GitLabWorkListAction.Filter -> filterChanged(action.value)
            GitLabWorkListAction.Refresh -> refresh()
            GitLabWorkListAction.More -> more()
            GitLabWorkListAction.Retry -> retry()
            is GitLabWorkListAction.Editor -> editor(action.open)
            is GitLabWorkListAction.Draft -> draftChanged(action.value.copy(allowQuickActions = false))
            is GitLabWorkListAction.Create -> create(action.confirmedQuickActions)
            is GitLabWorkListAction.Run -> runPipeline(action.ref)
        }
    }
    fun create(confirmedQuickActions: Boolean = false) {
        val before = state.value
        if (before.account == null || before.writing || before.section == GitLabWorkSection.PIPELINES || !before.draft.isValid(before.kind, true)) return
        if (gitLabHasQuickActions(before.draft.description) && !confirmedQuickActions) return
        write { repository.create(key, projectId, before.kind, before.draft.copy(allowQuickActions = confirmedQuickActions)).gitLabApply { created ->
            mutableState.update { it.copy(created = created, editorOpen = false, draft = GitLabConversationDraft("", "")) }
            saveDraft()
        } }
    }
    fun runPipeline(ref: String) {
        val before = state.value
        if (before.section != GitLabWorkSection.PIPELINES || ref != before.searchInput.trim() || ref.isBlank() || ref.length > 1024) return
        write { repository.runPipeline(key, projectId, ref).gitLabApply { result -> mutableState.update { it.copy(createdPipeline = result) } } }
    }

    private fun write(block: suspend () -> Result<Unit>) {
        if (state.value.account == null || state.value.writing) return
        readJob?.cancel(); readVersion++
        val revision = epoch
        mutableState.update { it.copy(writing = true, writeError = null, loading = false, loadingMore = false) }
        writeJob = viewModelScope.launch {
            val result = block()
            currentCoroutineContext().ensureActive()
            if (revision != epoch) return@launch
            mutableState.update { it.copy(writing = false, writeError = result.exceptionOrNull()?.gitLabError()) }
            if (result.isSuccess) load(refreshLoaded = true)
        }
    }

    private fun load(more: Boolean = false, refreshLoaded: Boolean = false) {
        val before = state.value
        if (before.account == null) return
        val page = if (more) before.nextPage ?: return else 1
        val windowPages = if (refreshLoaded) loadedPages.coerceAtLeast(1) else 1
        if (!more && !refreshLoaded) loadedPages = 0
        retryRefresh = false
        readJob?.cancel()
        val revision = epoch
        val version = ++readVersion
        mutableState.update { it.copy(conversations = if (more || refreshLoaded) it.conversations else emptyList(), pipelines = if (more || refreshLoaded) it.pipelines else emptyList(),
            nextPage = if (more || refreshLoaded) it.nextPage else null, loading = !more, loadingMore = more, error = null) }
        readJob = viewModelScope.launch {
            if (before.section == GitLabWorkSection.PIPELINES) {
                val result = if (refreshLoaded) refreshGitLabPages(windowPages) { requested -> repository.pipelines(key, projectId, before.query, requested) }
                    else repository.pipelines(key, projectId, before.query, page).map { it to 1 }
                currentCoroutineContext().ensureActive()
                if (revision != epoch || version != readVersion) return@launch
                result.fold(onSuccess = { (response, count) ->
                    loadedPages = if (more) loadedPages + count else count
                    mutableState.update { it.copy(pipelines = ((if (more) it.pipelines else emptyList()) + response.items).distinctBy(GitLabPipeline::id),
                        nextPage = response.nextPage, loading = false, loadingMore = false) }
                }, onFailure = { failure -> retryRefresh = refreshLoaded; readFailed(failure) })
            } else {
                val result = if (refreshLoaded) refreshGitLabPages(windowPages) { requested -> repository.conversations(key, projectId, before.kind, before.filter, before.query, requested) }
                    else repository.conversations(key, projectId, before.kind, before.filter, before.query, page).map { it to 1 }
                currentCoroutineContext().ensureActive()
                if (revision != epoch || version != readVersion) return@launch
                result.fold(onSuccess = { (response, count) ->
                    loadedPages = if (more) loadedPages + count else count
                    mutableState.update { it.copy(conversations = ((if (more) it.conversations else emptyList()) + response.items).distinctBy(GitLabConversation::id),
                        nextPage = response.nextPage, loading = false, loadingMore = false) }
                }, onFailure = { failure -> retryRefresh = refreshLoaded; readFailed(failure) })
            }
        }
    }
    private fun readFailed(error: Throwable) {
        val denied = retryRefresh && error.gitLabError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)
        if (denied) loadedPages = 0
        mutableState.update { it.copy(error = error.gitLabError(), loading = false, loadingMore = false,
            conversations = if (denied) emptyList() else it.conversations,
            pipelines = if (denied) emptyList() else it.pipelines, nextPage = if (denied) null else it.nextPage) }
    }
    private fun prefix() = "$key:$projectId:${state.value.section.name}:"
    private fun saveDraft() {
        val prefix = prefix(); val current = state.value
        saved[prefix + "open"] = current.editorOpen
        saved[prefix + "title"] = current.draft.title; saved[prefix + "body"] = current.draft.description
        saved[prefix + "source"] = current.draft.sourceBranch; saved[prefix + "target"] = current.draft.targetBranch
    }
    private fun restoreDraft() {
        val prefix = prefix()
        mutableState.update { it.copy(editorOpen = saved[prefix + "open"] ?: false, draft = GitLabConversationDraft(
            saved[prefix + "title"] ?: "", saved[prefix + "body"] ?: "", saved[prefix + "source"] ?: "", saved[prefix + "target"] ?: "")) }
    }
}

internal fun Throwable.gitLabError() = (this as? GitLabReadException)?.reason ?: GitLabReadError.NETWORK

internal suspend fun <T> Result<T>.gitLabApply(block: (T) -> Unit): Result<Unit> {
    currentCoroutineContext().ensureActive()
    return map(block)
}
