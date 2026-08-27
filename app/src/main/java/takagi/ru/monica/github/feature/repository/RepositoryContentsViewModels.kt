package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class RepositoryFilesUiState(
    val owner: String,
    val name: String,
    val ref: String,
    val path: String,
    val branches: GithubPage<GithubBranch> = GithubPage(emptyList(), null),
    val tags: GithubPage<GithubTag> = GithubPage(emptyList(), null),
    val items: List<GithubContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingBranches: Boolean = true,
    val isLoadingTags: Boolean = false,
    val error: Boolean = false,
    val branchesError: Boolean = false,
    val tagsError: Boolean = false,
    val tagsLoaded: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}

sealed interface RepositoryFilesAction {
    data object Retry : RepositoryFilesAction
    data object RetryBranches : RepositoryFilesAction
    data object LoadTags : RepositoryFilesAction
    data object LoadMoreBranches : RepositoryFilesAction
    data object LoadMoreTags : RepositoryFilesAction
}

class RepositoryFilesViewModel(
    private val owner: String,
    private val name: String,
    private val ref: String,
    private val path: String,
    private val repository: GithubRepositoryContentsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryFilesUiState(owner, name, ref, path))
    val state: StateFlow<RepositoryFilesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var branchesJob: Job? = null
    private var tagsJob: Job? = null

    init {
        loadBranches()
        load()
    }

    fun onAction(action: RepositoryFilesAction) {
        when (action) {
            RepositoryFilesAction.Retry -> load()
            RepositoryFilesAction.RetryBranches -> loadBranches()
            RepositoryFilesAction.LoadTags -> if (!_state.value.tagsLoaded || _state.value.tagsError) loadTags()
            RepositoryFilesAction.LoadMoreBranches -> _state.value.branches.nextPage?.let { loadBranches(it, append = true) }
            RepositoryFilesAction.LoadMoreTags -> _state.value.tags.nextPage?.let { loadTags(it, append = true) }
        }
    }

    private fun loadBranches(page: Int = 1, append: Boolean = false) {
        branchesJob?.cancel()
        _state.update { it.copy(isLoadingBranches = true, branchesError = false) }
        branchesJob = viewModelScope.launch {
            repository.branches(owner, name, page = page).fold(
                onSuccess = { branches ->
                    _state.update {
                        it.copy(
                            branches = GithubPage(
                                items = branches.mergeItems(
                                    existing = if (append) it.branches.items else emptyList(),
                                    reset = !append,
                                    keySelector = GithubBranch::name
                                ),
                                nextPage = branches.nextPage
                            ),
                            isLoadingBranches = false,
                            branchesError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingBranches = false, branchesError = true) }
                }
            )
        }
    }

    private fun loadTags(page: Int = 1, append: Boolean = false) {
        tagsJob?.cancel()
        _state.update { it.copy(isLoadingTags = true, tagsError = false, tagsLoaded = true) }
        tagsJob = viewModelScope.launch {
            repository.tags(owner, name, page = page).fold(
                onSuccess = { tags ->
                    _state.update {
                        it.copy(
                            tags = GithubPage(
                                items = tags.mergeItems(
                                    existing = if (append) it.tags.items else emptyList(),
                                    reset = !append,
                                    keySelector = GithubTag::name
                                ),
                                nextPage = tags.nextPage
                            ),
                            isLoadingTags = false,
                            tagsError = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoadingTags = false, tagsError = true) } }
            )
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = viewModelScope.launch {
            repository.directory(owner, name, path, ref).fold(
                onSuccess = { items ->
                    _state.update {
                        it.copy(
                            items = items.sortedWith(
                                compareBy<GithubContentItem> { item -> item.type != GithubContentType.DIRECTORY }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { item -> item.name }
                            ),
                            isLoading = false,
                            error = false
                        )
                    }
                },
                onFailure = { _state.update { it.copy(isLoading = false, error = true) } }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val ref: String,
        private val path: String,
        private val repository: GithubRepositoryContentsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryFilesViewModel::class.java))
            return RepositoryFilesViewModel(owner, name, ref, path, repository) as T
        }
    }
}

@Immutable
data class RepositoryFileUiState(
    val owner: String,
    val name: String,
    val ref: String,
    val path: String,
    val content: GithubFileContent? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val fileName: String get() = path.substringAfterLast('/')
}

sealed interface RepositoryFileAction {
    data object Retry : RepositoryFileAction
}

class RepositoryFileViewModel(
    private val owner: String,
    private val name: String,
    private val ref: String,
    private val path: String,
    private val repository: GithubRepositoryContentsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryFileUiState(owner, name, ref, path))
    val state: StateFlow<RepositoryFileUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: RepositoryFileAction) {
        when (action) {
            RepositoryFileAction.Retry -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = viewModelScope.launch {
            repository.file(owner, name, path, ref).fold(
                onSuccess = { content ->
                    _state.update { it.copy(content = content, isLoading = false, error = false) }
                },
                onFailure = { _state.update { it.copy(isLoading = false, error = true) } }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val ref: String,
        private val path: String,
        private val repository: GithubRepositoryContentsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryFileViewModel::class.java))
            return RepositoryFileViewModel(owner, name, ref, path, repository) as T
        }
    }
}
