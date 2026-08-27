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
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class RepositoryCollaboratorsUiState(
    val owner: String,
    val name: String,
    val items: List<GithubCollaborator> = emptyList(),
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val filteredItems: List<GithubCollaborator>
        get() = query.trim().takeIf(String::isNotBlank)?.let { value ->
            items.filter { it.user.login.contains(value, ignoreCase = true) }
        } ?: items
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface RepositoryCollaboratorsAction {
    data object Retry : RepositoryCollaboratorsAction
    data object LoadMore : RepositoryCollaboratorsAction
    data class Search(val query: String) : RepositoryCollaboratorsAction
}

class RepositoryCollaboratorsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryCollaboratorsUiState(owner, name))
    val state: StateFlow<RepositoryCollaboratorsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: RepositoryCollaboratorsAction) {
        when (action) {
            RepositoryCollaboratorsAction.Retry -> load(reset = _state.value.items.isEmpty())
            RepositoryCollaboratorsAction.LoadMore -> load(reset = false)
            is RepositoryCollaboratorsAction.Search -> _state.update { it.copy(query = action.query) }
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
            repository.collaborators(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset) { it.user.login },
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
            require(modelClass.isAssignableFrom(RepositoryCollaboratorsViewModel::class.java))
            return RepositoryCollaboratorsViewModel(owner, name, repository) as T
        }
    }
}
