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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubWebhookEdit
import takagi.ru.monica.github.domain.mergeItems
import takagi.ru.monica.github.domain.GithubSession

@Immutable
data class RepositoryWebhooksUiState(
    val owner: String,
    val name: String,
    val canManage: Boolean = false,
    val items: List<GithubRepositoryWebhook> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUpdatingWebhook: Boolean = false,
    val webhookFailure: RepositoryWriteFailure? = null,
    val error: Boolean = false,
    val failedPage: Int? = null,
    val viewerLogin: String? = null,
    val viewerAccountId: Long? = null
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface RepositoryWebhooksAction {
    data object Refresh : RepositoryWebhooksAction
    data object Retry : RepositoryWebhooksAction
    data object LoadMore : RepositoryWebhooksAction
    data class SetEnabled(val id: Long, val enabled: Boolean) : RepositoryWebhooksAction
    data class Delete(val id: Long) : RepositoryWebhooksAction
}

class RepositoryWebhooksViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository,
    private val viewerCanAdmin: Boolean,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
) : ViewModel() {
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val _state = MutableStateFlow(
        RepositoryWebhooksUiState(owner, name, canManage = viewerCanAdmin)
    )
    val state: StateFlow<RepositoryWebhooksUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    // Writes run independently of paging so a toggle never cancels an in-flight page and vice versa.
    private var webhookJob: Job? = null
    private var accountId: Long? = null
    private var sessionInitialized = false

    init {
        sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } }
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && account?.id == accountId) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = RepositoryWebhooksUiState(owner, name, isLoading = account != null, viewerLogin = account?.login, viewerAccountId = account?.id)
        if (account == null) return
        requests.launch {
            val role = repository.details(owner, name).currentRepositoryResult().getOrNull()?.viewerRole
            _state.update { it.copy(canManage = role?.canAdmin == true) }
            load(reset = true)
        }
    }

    init {
        load(reset = true)
    }

    fun onAction(action: RepositoryWebhooksAction) {
        when (action) {
            RepositoryWebhooksAction.Refresh -> load(reset = true, refreshing = true)
            RepositoryWebhooksAction.Retry -> load(reset = _state.value.failedPage == 1 || _state.value.items.isEmpty())
            RepositoryWebhooksAction.LoadMore -> load(reset = false)
            is RepositoryWebhooksAction.SetEnabled -> setEnabled(action.id, action.enabled)
            is RepositoryWebhooksAction.Delete -> delete(action.id)
        }
    }

    private fun setEnabled(id: Long, enabled: Boolean) {
        if (!_state.value.canManage || _state.value.isUpdatingWebhook) return
        // A switch that already matches the server has nothing to send; GitHub would accept it anyway.
        val current = _state.value.items.firstOrNull { it.id == id } ?: return
        if (current.isActive == enabled) return
        loadJob?.cancel()
        _state.update { it.copy(isUpdatingWebhook = true, webhookFailure = null,
            isLoading = false, isLoadingMore = false, isRefreshing = false) }
        webhookJob?.cancel()
        webhookJob = requests.launch {
            repository.updateWebhook(owner, name, id, GithubWebhookEdit(active = enabled)).currentRepositoryResult().fold(
                onSuccess = { confirmed ->
                    _state.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.id == id) it.copy(isActive = confirmed.isActive) else it
                            },
                            isUpdatingWebhook = false,
                            webhookFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isUpdatingWebhook = false, webhookFailure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    private fun delete(id: Long) {
        if (!_state.value.canManage || _state.value.isUpdatingWebhook || _state.value.items.none { it.id == id }) return
        loadJob?.cancel()
        _state.update { it.copy(isUpdatingWebhook = true, webhookFailure = null,
            isLoading = false, isLoadingMore = false, isRefreshing = false) }
        webhookJob?.cancel()
        webhookJob = requests.launch {
            repository.deleteWebhook(owner, name, id).currentRepositoryResult().fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.id == id },
                            isUpdatingWebhook = false,
                            webhookFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isUpdatingWebhook = false, webhookFailure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        val current = _state.value
        if ((sessionInitialized && accountId == null) || current.isUpdatingWebhook) return
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !refreshing && current.failedPage != 1) emptyList() else it.items,
                isLoading = reset && !refreshing,
                isLoadingMore = !reset,
                isRefreshing = refreshing,
                error = false,
                failedPage = null
            )
        }
        loadJob = requests.launch {
            repository.webhooks(owner, name, page = page).currentRepositoryResult().fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset, GithubRepositoryWebhook::id),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = false,
                            failedPage = null
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = true, failedPage = page) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubRepositoryDetailsRepository,
        private val viewerCanAdmin: Boolean,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryWebhooksViewModel::class.java))
            return RepositoryWebhooksViewModel(owner, name, repository, viewerCanAdmin, sessions) as T
        }
    }
}
