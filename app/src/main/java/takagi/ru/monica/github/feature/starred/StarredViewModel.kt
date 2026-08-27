package takagi.ru.monica.github.feature.starred

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
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarCategory
import takagi.ru.monica.github.domain.GithubStarCategoryStore
import takagi.ru.monica.github.domain.GithubStarsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class CategorizedStar(
    val repository: GithubRepository,
    val category: GithubStarCategory
)

@Immutable
data class StarredUiState(
    val repositories: List<CategorizedStar> = emptyList(),
    val selectedCategory: GithubStarCategory = GithubStarCategory.ALL,
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val requiresAuthentication: Boolean = true,
    val error: Boolean = false
) {
    val visibleRepositories: List<CategorizedStar>
        get() = repositories.filter { item ->
            val matchesCategory = selectedCategory == GithubStarCategory.ALL || item.category == selectedCategory
            val matchesQuery = query.isBlank() || item.repository.fullName.contains(query, ignoreCase = true) || item.repository.description.orEmpty().contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }

    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface StarredAction {
    data class QueryChanged(val query: String) : StarredAction
    data class CategorySelected(val category: GithubStarCategory) : StarredAction
    data class RepositoryCategorized(val repositoryId: Long, val category: GithubStarCategory) : StarredAction
    data object Refresh : StarredAction
    data object LoadMore : StarredAction
}

class StarredViewModel(
    private val repository: GithubStarsRepository,
    private val categoryStore: GithubStarCategoryStore
) : ViewModel() {
    private val _state = MutableStateFlow(StarredUiState())
    val state: StateFlow<StarredUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onSessionChanged(session: GithubSession) {
        when (session) {
            GithubSession.Loading -> _state.update { it.copy(isLoading = true, error = false) }
            GithubSession.SignedOut -> {
                loadJob?.cancel()
                _state.value = StarredUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> _state.update { it.copy(isLoading = false, requiresAuthentication = false, error = true) }
            is GithubSession.SignedIn -> refresh()
        }
    }

    fun onAction(action: StarredAction) {
        when (action) {
            is StarredAction.QueryChanged -> _state.update { it.copy(query = action.query) }
            is StarredAction.CategorySelected -> _state.update { it.copy(selectedCategory = action.category) }
            is StarredAction.RepositoryCategorized -> categorize(action.repositoryId, action.category)
            StarredAction.Refresh -> refresh()
            StarredAction.LoadMore -> load(reset = false)
        }
    }

    private fun refresh() {
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                repositories = if (reset) emptyList() else it.repositories,
                nextPage = if (reset) null else it.nextPage,
                isLoading = reset,
                isLoadingMore = !reset,
                requiresAuthentication = false,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.starredRepositories(requestedPage).fold(
                onSuccess = { page ->
                    val categorizedPage = GithubPage(
                        items = page.items.map { repository ->
                            CategorizedStar(repository, categoryStore.category(repository.id))
                        },
                        nextPage = page.nextPage
                    )
                    _state.update { state ->
                        state.copy(
                            repositories = categorizedPage.mergeItems(
                                state.repositories,
                                reset,
                                { it.repository.id }
                            ),
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

    private fun categorize(repositoryId: Long, category: GithubStarCategory) {
        categoryStore.setCategory(repositoryId, category)
        _state.update { state ->
            state.copy(repositories = state.repositories.map { item ->
                if (item.repository.id == repositoryId) item.copy(category = category) else item
            })
        }
    }

    class Factory(
        private val repository: GithubStarsRepository,
        private val categoryStore: GithubStarCategoryStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StarredViewModel::class.java))
            return StarredViewModel(repository, categoryStore) as T
        }
    }
}
