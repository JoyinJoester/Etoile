package takagi.ru.monica.github.feature.organizations

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
import takagi.ru.monica.github.domain.GithubOrganization
import takagi.ru.monica.github.domain.GithubOrganizationsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class OrganizationsUiState(
    val items: List<GithubOrganization> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Boolean = false
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface OrganizationsAction {
    data object Refresh : OrganizationsAction
    data object Retry : OrganizationsAction
    data object LoadMore : OrganizationsAction
}

class OrganizationsViewModel(
    private val repository: GithubOrganizationsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(OrganizationsUiState())
    val state: StateFlow<OrganizationsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true

    init {
        load(reset = true)
    }

    fun onAction(action: OrganizationsAction) {
        when (action) {
            OrganizationsAction.Refresh -> load(reset = true, refreshing = true)
            OrganizationsAction.Retry -> load(
                reset = failedReset,
                refreshing = failedReset && _state.value.items.isNotEmpty()
            )
            OrganizationsAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !refreshing) emptyList() else it.items,
                isLoading = reset && !refreshing,
                isLoadingMore = !reset,
                isRefreshing = refreshing,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.myOrganizations(requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubOrganization::id),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    failedReset = reset
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val repository: GithubOrganizationsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OrganizationsViewModel::class.java))
            return OrganizationsViewModel(repository) as T
        }
    }
}
