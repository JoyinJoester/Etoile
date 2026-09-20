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
    val filter: MyConversationsFilter = MyConversationsFilter.OPEN,
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
    data object Refresh : MyConversationsAction
    data class SelectFilter(val filter: MyConversationsFilter) : MyConversationsAction
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
    private var requestVersion = 0

    fun onSessionChanged(session: GithubSession) {
        when (session) {
            GithubSession.Loading -> {
                requestVersion++
                loadJob?.cancel()
                currentLogin = null
                _state.value = MyConversationsUiState(isLoading = true)
            }
            GithubSession.SignedOut -> {
                currentLogin = null
                requestVersion++
                loadJob?.cancel()
                _state.value = MyConversationsUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> {
                requestVersion++
                loadJob?.cancel()
                currentLogin = null
                _state.value = MyConversationsUiState(requiresAuthentication = false, error = true)
            }
            is GithubSession.SignedIn -> {
                currentLogin = session.account.login
                load(login = session.account.login, reset = true)
            }
        }
    }

    fun onAction(action: MyConversationsAction) {
        when (action) {
            MyConversationsAction.Refresh -> load(login = currentLogin, reset = true)
            is MyConversationsAction.SelectFilter -> {
                if (action.filter == _state.value.filter) return
                _state.update { it.copy(filter = action.filter) }
                load(login = currentLogin, reset = true)
            }
            MyConversationsAction.Retry -> load(login = currentLogin, reset = _state.value.items.isEmpty())
            MyConversationsAction.LoadMore -> load(login = currentLogin, reset = false)
        }
    }

    private fun load(login: String?, reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        val user = login ?: return
        val version = ++requestVersion
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                nextPage = if (reset) null else it.nextPage,
                isLoading = reset,
                isLoadingMore = !reset,
                requiresAuthentication = false,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            val query = listOfNotNull(current.filter.qualifier, "involves:$user").joinToString(" ")
            val result = when (kind) {
                MyConversationsKind.ISSUES -> searchRepository.issues(query, requestedPage)
                MyConversationsKind.PULL_REQUESTS -> searchRepository.pullRequests(query, requestedPage)
            }
            if (version != requestVersion) return@launch
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

enum class MyConversationsFilter(val qualifier: String?) {
    OPEN("is:open"), CLOSED("is:closed"), ALL(null)
}
