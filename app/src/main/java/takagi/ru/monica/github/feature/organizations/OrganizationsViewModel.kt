package takagi.ru.monica.github.feature.organizations

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
import takagi.ru.monica.github.domain.GithubOrganization
import takagi.ru.monica.github.domain.GithubOrganizationsRepository
import takagi.ru.monica.github.domain.GithubSession
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
    private val repository: GithubOrganizationsRepository,
    private val sessions: Flow<GithubSession>? = null,
    private val expectedAccountId: Long? = null
) : ViewModel() {
    private val _state = MutableStateFlow(OrganizationsUiState(isLoading = sessions == null))
    val state: StateFlow<OrganizationsUiState> = _state.asStateFlow()
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
        _state.value = OrganizationsUiState(isLoading = false)
        if (ready) load(reset = true)
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
            val result = repository.myOrganizations(requestedPage)
            coroutineContext.ensureActive()
            if (version != requestVersion || !sessionReady) return@launch
            result.fold(
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
        private val repository: GithubOrganizationsRepository,
        private val sessions: Flow<GithubSession>? = null,
        private val expectedAccountId: Long? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OrganizationsViewModel::class.java))
            return OrganizationsViewModel(repository, sessions, expectedAccountId) as T
        }
    }
}
