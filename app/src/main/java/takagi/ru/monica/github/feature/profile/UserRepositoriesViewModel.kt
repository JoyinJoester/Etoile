package takagi.ru.monica.github.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class UserRepositoriesUiState(
    val items: List<GithubRepository> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Boolean = false
) {
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface UserRepositoriesAction {
    data object Refresh : UserRepositoriesAction
    data object Retry : UserRepositoriesAction
    data object LoadMore : UserRepositoriesAction
}

class UserRepositoriesViewModel(
    private val repository: GithubUserRepositoriesRepository,
    private val sessions: Flow<GithubSession>? = null,
    private val expectedAccountId: Long? = null
) : ViewModel() {
    private val _state = MutableStateFlow(UserRepositoriesUiState(isLoading = sessions == null))
    val state: StateFlow<UserRepositoriesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true

    private var accountId: Long? = null
    private var sessionReady = sessions == null
    private var requestVersion = 0L

    init {
        if (sessions == null) load(reset = true)
        else viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { sessions.collect(::onSessionChanged) }
    }

    fun onSessionChanged(session: GithubSession) {
        val id = (session as? GithubSession.SignedIn)?.account?.id
        val ready = id != null && (expectedAccountId == null || id == expectedAccountId)
        if (accountId == id && sessionReady == ready) return
        accountId = id
        sessionReady = ready
        requestVersion++
        loadJob?.cancel()
        failedReset = true
        _state.value = UserRepositoriesUiState(isLoading = false)
        if (ready) load(reset = true)
    }

    fun onAction(action: UserRepositoriesAction) {
        when (action) {
            UserRepositoriesAction.Refresh -> load(reset = true, refreshing = true)
            UserRepositoriesAction.Retry -> load(
                reset = failedReset,
                refreshing = failedReset && _state.value.items.isNotEmpty()
            )
            UserRepositoriesAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        if (!sessionReady) return
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        val version = ++requestVersion
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
            val result = repository.repositories(requestedPage)
            coroutineContext.ensureActive()
            if (version != requestVersion || !sessionReady) return@launch
            result.fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubRepository::id),
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
        private val repository: GithubUserRepositoriesRepository,
        private val sessions: Flow<GithubSession>? = null,
        private val expectedAccountId: Long? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UserRepositoriesViewModel::class.java))
            return UserRepositoriesViewModel(repository, sessions, expectedAccountId) as T
        }
    }
}
