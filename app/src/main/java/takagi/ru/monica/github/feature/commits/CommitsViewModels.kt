package takagi.ru.monica.github.feature.commits

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
import takagi.ru.monica.github.domain.GithubCommit
import takagi.ru.monica.github.domain.GithubCommitDetails
import takagi.ru.monica.github.domain.GithubCommitsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class CommitsUiState(
    val owner: String,
    val name: String,
    val ref: String,
    val items: List<GithubCommit> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface CommitsAction {
    data object Retry : CommitsAction
    data object LoadMore : CommitsAction
}

class CommitsViewModel(
    private val owner: String,
    private val name: String,
    private val ref: String,
    private val repository: GithubCommitsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CommitsUiState(owner = owner, name = name, ref = ref))
    val state: StateFlow<CommitsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: CommitsAction) {
        when (action) {
            CommitsAction.Retry -> load(reset = _state.value.items.isEmpty())
            CommitsAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                error = false,
                items = if (reset) emptyList() else it.items
            )
        }
        loadJob = viewModelScope.launch {
            repository.commits(owner, name, ref, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubCommit::sha),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val ref: String,
        private val repository: GithubCommitsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CommitsViewModel::class.java))
            return CommitsViewModel(owner, name, ref, repository) as T
        }
    }
}

@Immutable
data class CommitDetailUiState(
    val owner: String,
    val name: String,
    val sha: String,
    val details: GithubCommitDetails? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}

sealed interface CommitDetailAction {
    data object Retry : CommitDetailAction
}

class CommitDetailViewModel(
    private val owner: String,
    private val name: String,
    private val sha: String,
    private val repository: GithubCommitsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CommitDetailUiState(owner, name, sha))
    val state: StateFlow<CommitDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: CommitDetailAction) {
        when (action) {
            CommitDetailAction.Retry -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = viewModelScope.launch {
            repository.commit(owner, name, sha).fold(
                onSuccess = { details ->
                    _state.update { it.copy(details = details, isLoading = false, error = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val sha: String,
        private val repository: GithubCommitsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CommitDetailViewModel::class.java))
            return CommitDetailViewModel(owner, name, sha, repository) as T
        }
    }
}
