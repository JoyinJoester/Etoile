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
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class RepositoryBranchesUiState(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val items: List<GithubBranch> = emptyList(),
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val filteredItems: List<GithubBranch>
        get() = query.trim().takeIf(String::isNotBlank)?.let { normalized ->
            items.filter { it.name.contains(normalized, ignoreCase = true) }
        } ?: items
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface RepositoryBranchesAction {
    data object Retry : RepositoryBranchesAction
    data object LoadMore : RepositoryBranchesAction
    data class Search(val query: String) : RepositoryBranchesAction
}

class RepositoryBranchesViewModel(
    private val owner: String,
    private val name: String,
    defaultBranch: String,
    private val repository: GithubRepositoryContentsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryBranchesUiState(owner, name, defaultBranch))
    val state: StateFlow<RepositoryBranchesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: RepositoryBranchesAction) {
        when (action) {
            RepositoryBranchesAction.Retry -> load(reset = _state.value.items.isEmpty())
            RepositoryBranchesAction.LoadMore -> load(reset = false)
            is RepositoryBranchesAction.Search -> _state.update { it.copy(query = action.query) }
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.branches(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset, GithubBranch::name),
                            nextPage = result.nextPage,
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
        private val defaultBranch: String,
        private val repository: GithubRepositoryContentsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryBranchesViewModel::class.java))
            return RepositoryBranchesViewModel(owner, name, defaultBranch, repository) as T
        }
    }
}
