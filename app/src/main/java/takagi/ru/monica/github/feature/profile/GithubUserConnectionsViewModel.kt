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
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class GithubUserConnectionsUiState(
    val login: String,
    val kind: GithubUserConnectionKind,
    val users: List<GithubUserSummary> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val refreshError: Boolean = false
) {
    val canLoadMore: Boolean
        get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface GithubUserConnectionsAction {
    data object Refresh : GithubUserConnectionsAction
    data object Retry : GithubUserConnectionsAction
    data object LoadMore : GithubUserConnectionsAction
}

class GithubUserConnectionsViewModel(
    private val login: String,
    private val kind: GithubUserConnectionKind,
    private val repository: GithubPublicUserRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GithubUserConnectionsUiState(login, kind))
    val state: StateFlow<GithubUserConnectionsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true, preserveExisting = false)
    }

    fun onAction(action: GithubUserConnectionsAction) {
        when (action) {
            GithubUserConnectionsAction.Refresh -> load(
                reset = true,
                preserveExisting = _state.value.users.isNotEmpty()
            )
            GithubUserConnectionsAction.Retry -> {
                val state = _state.value
                load(
                    reset = state.users.isEmpty() || state.refreshError,
                    preserveExisting = state.users.isNotEmpty()
                )
            }
            GithubUserConnectionsAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean, preserveExisting: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                users = if (reset && !preserveExisting) emptyList() else it.users,
                nextPage = if (reset) null else it.nextPage,
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                error = false,
                refreshError = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.connections(login, kind, page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            users = result.mergeItems(state.users, reset, GithubUserSummary::login),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = false,
                            refreshError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = true,
                            refreshError = reset
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val login: String,
        private val kind: GithubUserConnectionKind,
        private val repository: GithubPublicUserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GithubUserConnectionsViewModel::class.java))
            return GithubUserConnectionsViewModel(login, kind, repository) as T
        }
    }
}
