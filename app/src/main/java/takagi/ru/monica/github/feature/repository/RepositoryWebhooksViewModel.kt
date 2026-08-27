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
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class RepositoryWebhooksUiState(
    val owner: String,
    val name: String,
    val items: List<GithubRepositoryWebhook> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface RepositoryWebhooksAction {
    data object Retry : RepositoryWebhooksAction
    data object LoadMore : RepositoryWebhooksAction
}

class RepositoryWebhooksViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryWebhooksUiState(owner, name))
    val state: StateFlow<RepositoryWebhooksUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: RepositoryWebhooksAction) {
        when (action) {
            RepositoryWebhooksAction.Retry -> load(reset = _state.value.items.isEmpty())
            RepositoryWebhooksAction.LoadMore -> load(reset = false)
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
            repository.webhooks(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset, GithubRepositoryWebhook::id),
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
        private val repository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryWebhooksViewModel::class.java))
            return RepositoryWebhooksViewModel(owner, name, repository) as T
        }
    }
}
