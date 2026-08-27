package takagi.ru.monica.github.feature.mywork

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
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class MyConversationsUiState(
    val items: List<GithubIssueSearchResult> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val requiresAuthentication: Boolean = true,
    val error: Boolean = false
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface MyConversationsAction {
    data object Retry : MyConversationsAction
    data object LoadMore : MyConversationsAction
}

class MyConversationsViewModel(
    private val searchRepository: GithubGlobalSearchRepository,
    private val kind: MyConversationsKind
) : ViewModel() {
    private val _state = MutableStateFlow(MyConversationsUiState())
    val state: StateFlow<MyConversationsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var currentLogin: String? = null

    fun onSessionChanged(session: GithubSession) {
        when (session) {
            GithubSession.Loading -> _state.update { it.copy(isLoading = true, error = false) }
            GithubSession.SignedOut -> {
                currentLogin = null
                loadJob?.cancel()
                _state.value = MyConversationsUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> _state.update { it.copy(isLoading = false, requiresAuthentication = false, error = true) }
            is GithubSession.SignedIn -> {
                currentLogin = session.account.login
                load(login = session.account.login, reset = true)
            }
        }
    }

    fun onAction(action: MyConversationsAction) {
        when (action) {
            MyConversationsAction.Retry -> load(login = currentLogin, reset = _state.value.items.isEmpty())
            MyConversationsAction.LoadMore -> load(login = currentLogin, reset = false)
        }
    }

    private fun load(login: String?, reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        val user = login ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                requiresAuthentication = false,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            val query = "is:open involves:$user"
            val result = when (kind) {
                MyConversationsKind.ISSUES -> searchRepository.issues(query, requestedPage)
                MyConversationsKind.PULL_REQUESTS -> searchRepository.pullRequests(query, requestedPage)
            }
            result.fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubIssueSearchResult::id),
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
        private val searchRepository: GithubGlobalSearchRepository,
        private val kind: MyConversationsKind
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MyConversationsViewModel::class.java))
            return MyConversationsViewModel(searchRepository, kind) as T
        }
    }
}

enum class MyConversationsKind { ISSUES, PULL_REQUESTS }
