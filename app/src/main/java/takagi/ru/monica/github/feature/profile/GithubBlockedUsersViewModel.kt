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
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.mergeItems
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure
import takagi.ru.monica.github.feature.repository.githubWriteFailure

@Immutable
data class GithubBlockedUsersUiState(
    val users: List<GithubUserSummary> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val requiresAuthentication: Boolean = true,
    val error: RepositoryWriteFailure? = null
) {
    val canLoadMore: Boolean
        get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface GithubBlockedUsersAction {
    data object Refresh : GithubBlockedUsersAction
    data object Retry : GithubBlockedUsersAction
    data object LoadMore : GithubBlockedUsersAction
}

class GithubBlockedUsersViewModel(
    private val repository: GithubPublicUserRepository,
    private val sessions: Flow<GithubSession>? = null
) : ViewModel() {
    private val _state = MutableStateFlow(GithubBlockedUsersUiState())
    val state: StateFlow<GithubBlockedUsersUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true
    private var accountId: Long? = null
    private var requestVersion = 0L
    private var sessionLoading = false

    init {
        if (sessions != null) viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.collect(::onSessionChanged)
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val id = (session as? GithubSession.SignedIn)?.account?.id
        val loading = session is GithubSession.Loading
        if (id == accountId && sessionLoading == loading) return
        sessionLoading = loading
        accountId = id
        requestVersion++
        loadJob?.cancel()
        failedReset = true
        _state.value = GithubBlockedUsersUiState(isLoading = session is GithubSession.Loading)
        if (id != null) load(reset = true)
    }

    fun onAction(action: GithubBlockedUsersAction) {
        when (action) {
            GithubBlockedUsersAction.Refresh -> load(
                reset = true,
                preserveExisting = _state.value.users.isNotEmpty()
            )
            GithubBlockedUsersAction.Retry -> load(
                reset = failedReset,
                preserveExisting = failedReset && _state.value.users.isNotEmpty()
            )
            GithubBlockedUsersAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean, preserveExisting: Boolean = false) {
        if (accountId == null) return
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        val version = ++requestVersion
        _state.update {
            it.copy(
                users = if (reset && !preserveExisting) emptyList() else it.users,
                nextPage = if (reset) null else it.nextPage,
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                requiresAuthentication = false,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.blockedUsers(page)
            coroutineContext.ensureActive()
            if (version != requestVersion || accountId == null) return@launch
            result.fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            users = result.mergeItems(state.users, reset, GithubUserSummary::login),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    failedReset = reset
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = githubWriteFailure(error)
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val repository: GithubPublicUserRepository,
        private val sessions: Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GithubBlockedUsersViewModel::class.java))
            return GithubBlockedUsersViewModel(repository, sessions) as T
        }
    }
}
