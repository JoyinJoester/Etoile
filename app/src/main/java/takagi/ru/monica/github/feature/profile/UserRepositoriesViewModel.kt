package takagi.ru.monica.github.feature.profile

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
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class UserRepositoriesUiState(
    val items: List<GithubRepository> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface UserRepositoriesAction {
    data object Retry : UserRepositoriesAction
    data object LoadMore : UserRepositoriesAction
}

class UserRepositoriesViewModel(
    private val repository: GithubUserRepositoriesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(UserRepositoriesUiState())
    val state: StateFlow<UserRepositoriesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: UserRepositoriesAction) {
        when (action) {
            UserRepositoriesAction.Retry -> load(reset = _state.value.items.isEmpty())
            UserRepositoriesAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
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
            repository.repositories(requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubRepository::id),
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
        private val repository: GithubUserRepositoriesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UserRepositoriesViewModel::class.java))
            return UserRepositoriesViewModel(repository) as T
        }
    }
}
