package takagi.ru.monica.gitlab.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.gitlab.domain.*

data class GitLabProjectsUiState(
    val account: GitLabAccount? = null,
    val searchInput: String = "",
    val query: String = "",
    val items: List<GitLabProject> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: GitLabReadError? = null
)

sealed interface GitLabProjectsAction {
    data class SearchChanged(val value: String) : GitLabProjectsAction
    data object Search : GitLabProjectsAction
    data object Refresh : GitLabProjectsAction
    data object LoadMore : GitLabProjectsAction
    data object Retry : GitLabProjectsAction
}

class GitLabProjectsViewModel(
    private val accountKey: String,
    auth: GitLabAuthRepository,
    private val repository: GitLabProjectsRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabProjectsUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var loadedPages = 0
    private var retryRefresh = false

    init {
        viewModelScope.launch {
            auth.accounts.collect { accounts ->
                val account = accounts.accounts.firstOrNull { it.key == accountKey && it.key == accounts.activeKey }
                if (account != state.value.account) {
                    job?.cancel(); generation++
                    loadedPages = 0; retryRefresh = false
                    mutableState.value = GitLabProjectsUiState(account = account)
                    if (account != null) load(reset = true)
                }
            }
        }
    }

    fun onAction(action: GitLabProjectsAction) {
        when (action) {
            is GitLabProjectsAction.SearchChanged -> mutableState.update { it.copy(searchInput = action.value.take(200)) }
            GitLabProjectsAction.Search -> {
                mutableState.update { it.copy(query = it.searchInput.trim()) }
                load(reset = true)
            }
            GitLabProjectsAction.Refresh -> if (!state.value.isLoading && !state.value.isLoadingMore) load(reset = true, refreshLoaded = true)
            GitLabProjectsAction.LoadMore -> load(reset = state.value.items.isEmpty())
            GitLabProjectsAction.Retry -> load(reset = retryRefresh || state.value.items.isEmpty(), refreshLoaded = retryRefresh)
        }
    }

    private fun load(reset: Boolean, refreshLoaded: Boolean = false) {
        val before = state.value
        if (before.account == null || (!reset && (before.isLoading || before.isLoadingMore))) return
        val page = if (reset) 1 else before.nextPage ?: return
        val windowPages = if (refreshLoaded) loadedPages.coerceAtLeast(1) else 1
        if (reset && !refreshLoaded) loadedPages = 0
        retryRefresh = false
        job?.cancel()
        val version = ++generation
        val query = before.query
        mutableState.update { it.copy(items = if (reset && !refreshLoaded) emptyList() else it.items,
            nextPage = if (reset && !refreshLoaded) null else it.nextPage, isLoading = reset, isLoadingMore = !reset, error = null) }
        job = viewModelScope.launch {
            val result = if (refreshLoaded) refreshGitLabPages(windowPages) { requested -> repository.projects(accountKey, query, requested) }
                else repository.projects(accountKey, query, page).map { it to 1 }
            currentCoroutineContext().ensureActive()
            if (version != generation) return@launch
            result.fold(onSuccess = { (response, count) ->
                loadedPages = if (reset) count else loadedPages + count
                mutableState.update { it.copy(
                    items = ((if (reset) emptyList() else it.items) + response.items).distinctBy(GitLabProject::id),
                    nextPage = response.nextPage, isLoading = false, isLoadingMore = false) }
            }, onFailure = { error ->
                retryRefresh = refreshLoaded
                val denied = refreshLoaded && error.readError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)
                if (denied) loadedPages = 0
                mutableState.update { it.copy(isLoading = false, isLoadingMore = false, error = error.readError(),
                    items = if (denied) emptyList() else it.items, nextPage = if (denied) null else it.nextPage) }
            })
        }
    }
}

data class GitLabProjectUiState(
    val account: GitLabAccount? = null,
    val project: GitLabProject? = null,
    val refInput: String = "",
    val ref: String = "",
    val revision: GitLabRevision? = null,
    val path: String = "",
    val entries: List<GitLabTreeEntry> = emptyList(),
    val nextPage: Int? = null,
    val filePath: String? = null,
    val file: GitLabFile? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: GitLabReadError? = null
)

sealed interface GitLabProjectAction {
    data class RefChanged(val value: String) : GitLabProjectAction
    data object ApplyRef : GitLabProjectAction
    data class Open(val entry: GitLabTreeEntry) : GitLabProjectAction
    data object Up : GitLabProjectAction
    data object Refresh : GitLabProjectAction
    data object Retry : GitLabProjectAction
    data object LoadMore : GitLabProjectAction
}

class GitLabProjectViewModel(
    private val accountKey: String,
    private val projectId: Long,
    auth: GitLabAuthRepository,
    private val repository: GitLabProjectsRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabProjectUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            auth.accounts.collect { accounts ->
                val account = accounts.accounts.firstOrNull { it.key == accountKey && it.key == accounts.activeKey }
                if (account != state.value.account) {
                    job?.cancel(); generation++
                    mutableState.value = GitLabProjectUiState(account = account)
                    if (account != null) load()
                }
            }
        }
    }

    fun onAction(action: GitLabProjectAction) {
        val before = state.value
        when (action) {
            is GitLabProjectAction.RefChanged -> mutableState.update { it.copy(refInput = action.value.take(1024)) }
            GitLabProjectAction.ApplyRef -> {
                val ref = before.refInput.trim()
                if (ref.isEmpty() || before.project == null || before.account == null) return
                mutableState.update { it.copy(ref = ref, revision = null, path = "", filePath = null, file = null) }
                load()
            }
            is GitLabProjectAction.Open -> {
                if (before.isLoading || before.isLoadingMore || action.entry !in before.entries || before.revision == null) return
                when (action.entry.type) {
                    GitLabEntryType.DIRECTORY -> mutableState.update { it.copy(path = action.entry.path, filePath = null, file = null) }
                    GitLabEntryType.FILE -> mutableState.update { it.copy(filePath = action.entry.path, file = null) }
                    GitLabEntryType.SUBMODULE -> return
                }
                load()
            }
            GitLabProjectAction.Up -> {
                if (before.filePath == null && before.path.isEmpty()) return
                mutableState.update { it.copy(path = if (it.filePath != null) it.path else it.path.substringBeforeLast('/', ""), filePath = null, file = null) }
                load()
            }
            GitLabProjectAction.Refresh -> {
                mutableState.update { it.copy(project = null, revision = null, file = null) }
                load()
            }
            GitLabProjectAction.Retry -> load(more = before.entries.isNotEmpty() && before.nextPage != null)
            GitLabProjectAction.LoadMore -> load(more = true)
        }
    }

    private fun load(more: Boolean = false) {
        val before = state.value
        if (before.account == null || (more && (before.isLoading || before.isLoadingMore || before.filePath != null))) return
        val page = if (more) before.nextPage ?: return else 1
        job?.cancel()
        val version = ++generation
        mutableState.update { it.copy(entries = if (more) it.entries else emptyList(),
            nextPage = if (more) it.nextPage else null, isLoading = !more, isLoadingMore = more, error = null) }
        job = viewModelScope.launch {
            try {
                fun checkCurrent() { currentCheck(version) }
                val project = before.project ?: repository.project(accountKey, projectId).getOrThrow()
                currentCoroutineContext().ensureActive(); checkCurrent()
                val ref = before.ref.ifBlank { project.defaultBranch.orEmpty() }
                mutableState.update { it.copy(project = project, ref = ref, refInput = ref) }
                if (ref.isEmpty()) {
                    mutableState.update { it.copy(isLoading = false, isLoadingMore = false) }
                    return@launch
                }
                val revision = before.revision ?: repository.revision(accountKey, projectId, ref).getOrThrow()
                currentCoroutineContext().ensureActive(); checkCurrent()
                mutableState.update { it.copy(revision = revision) }
                if (before.filePath != null) {
                    val file = repository.file(accountKey, projectId, revision.sha, before.filePath).getOrThrow()
                    currentCoroutineContext().ensureActive(); checkCurrent()
                    mutableState.update { it.copy(file = file, isLoading = false, isLoadingMore = false) }
                } else {
                    val response = repository.tree(accountKey, projectId, revision.sha, before.path, page).getOrThrow()
                    currentCoroutineContext().ensureActive(); checkCurrent()
                    mutableState.update { it.copy(entries = ((if (more) it.entries else emptyList()) + response.items).distinctBy(GitLabTreeEntry::path),
                        nextPage = response.nextPage, isLoading = false, isLoadingMore = false) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (version == generation) mutableState.update { it.copy(isLoading = false, isLoadingMore = false, error = error.readError()) }
            }
        }
    }

    private fun currentCheck(version: Long) {
        if (version != generation) throw kotlinx.coroutines.CancellationException()
    }
}

private fun Throwable.readError() = (this as? GitLabReadException)?.reason ?: GitLabReadError.NETWORK

internal class GitLabViewModelFactory<T : ViewModel>(private val type: Class<T>, private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V {
        require(modelClass.isAssignableFrom(type))
        return create() as V
    }
}
