package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubWebhookDelivery

@Immutable
data class RepositoryWebhookDeliveriesUiState(
    val owner: String,
    val name: String,
    val webhookId: Long,
    val viewerId: Long? = null,
    val canManage: Boolean = false,
    val items: List<GithubWebhookDelivery> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadFailure: RepositoryWriteFailure? = null,
    val redeliveringId: Long? = null,
    val redeliveryFailure: RepositoryWriteFailure? = null,
    val acceptedDeliveryId: Long? = null
) {
    val fullName: String get() = "$owner/$name"
    val isReading: Boolean get() = isLoading || isLoadingMore || isRefreshing
    val canLoadMore: Boolean get() = nextCursor != null && !isReading
    val canRedeliver: Boolean get() = canManage && redeliveringId == null && !isReading
}

sealed interface RepositoryWebhookDeliveriesAction {
    data object Refresh : RepositoryWebhookDeliveriesAction
    data object Retry : RepositoryWebhookDeliveriesAction
    data object LoadMore : RepositoryWebhookDeliveriesAction
    data class Redeliver(val deliveryId: Long) : RepositoryWebhookDeliveriesAction
}

class RepositoryWebhookDeliveriesViewModel(
    private val owner: String,
    private val name: String,
    private val webhookId: Long,
    private val repository: GithubRepositoryDetailsRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryWebhookDeliveriesUiState(owner, name, webhookId))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var roleJob: Job? = null
    private var writeJob: Job? = null
    private var sessionKnown = false
    private var sessionVersion = 0L
    private var failedCursor: String? = null

    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    fun onSessionChanged(session: GithubSession) {
        if (session == GithubSession.Loading) {
            if (sessionKnown) {
                sessionKnown = false
                sessionVersion++
                loadJob?.cancel()
                roleJob?.cancel()
                writeJob?.cancel()
                failedCursor = null
                _state.value = RepositoryWebhookDeliveriesUiState(owner, name, webhookId)
            }
            return
        }
        val id = (session as? GithubSession.SignedIn)?.account?.id
        if (sessionKnown && id == _state.value.viewerId) return
        sessionKnown = true
        sessionVersion++
        loadJob?.cancel()
        roleJob?.cancel()
        writeJob?.cancel()
        failedCursor = null
        _state.value = RepositoryWebhookDeliveriesUiState(
            owner, name, webhookId, viewerId = id, isLoading = id != null,
            loadFailure = if (id == null) RepositoryWriteFailure.Forbidden else null
        )
        if (id != null) {
            loadRole()
            load(reset = true)
        }
    }

    fun onAction(action: RepositoryWebhookDeliveriesAction) {
        if (_state.value.viewerId == null) return
        when (action) {
            RepositoryWebhookDeliveriesAction.Refresh -> {
                if (!_state.value.isReading) {
                    loadRole()
                    load(reset = true)
                }
            }
            RepositoryWebhookDeliveriesAction.Retry -> {
                if (_state.value.loadFailure != null && !_state.value.isReading) {
                    load(reset = failedCursor == null, retry = true)
                }
            }
            RepositoryWebhookDeliveriesAction.LoadMore -> {
                if (_state.value.canLoadMore) load(reset = false)
            }
            is RepositoryWebhookDeliveriesAction.Redeliver -> redeliver(action.deliveryId)
        }
    }

    private fun loadRole() {
        roleJob?.cancel()
        val version = sessionVersion
        _state.update { it.copy(canManage = false) }
        roleJob = viewModelScope.launch {
            val canManage = repository.details(owner, name).getOrNull()?.viewerRole?.canAdmin == true
            if (isActive && version == sessionVersion) _state.update { it.copy(canManage = canManage) }
        }
    }

    private fun load(reset: Boolean, retry: Boolean = false) {
        val current = _state.value
        val cursor = if (reset) null else if (retry) failedCursor else current.nextCursor
        if (!reset && cursor == null) return
        val version = sessionVersion
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = reset && it.items.isEmpty(),
                isRefreshing = reset && it.items.isNotEmpty(),
                isLoadingMore = !reset,
                loadFailure = null
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.webhookDeliveries(owner, name, webhookId, cursor = cursor)
            if (!isActive || version != sessionVersion) return@launch
            result.fold(
                onSuccess = { page ->
                    failedCursor = null
                    _state.update {
                        it.copy(
                            items = (if (reset) page.items else it.items + page.items)
                                .distinctBy(GithubWebhookDelivery::id),
                            nextCursor = page.nextCursor?.takeUnless { next -> next == cursor },
                            isLoading = false, isRefreshing = false, isLoadingMore = false,
                            loadFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    failedCursor = cursor
                    _state.update {
                        it.copy(
                            isLoading = false, isRefreshing = false, isLoadingMore = false,
                            loadFailure = githubWriteFailure(error)
                        )
                    }
                }
            )
        }
    }

    private fun redeliver(deliveryId: Long) {
        val current = _state.value
        if (!current.canRedeliver || current.items.none { it.id == deliveryId }) return
        val version = sessionVersion
        _state.update {
            it.copy(redeliveringId = deliveryId, redeliveryFailure = null, acceptedDeliveryId = null)
        }
        writeJob = viewModelScope.launch {
            val result = repository.redeliverWebhook(owner, name, webhookId, deliveryId)
            if (!isActive || version != sessionVersion) return@launch
            result.fold(
                onSuccess = {
                    // HTTP 202 only accepts a request. Refresh the history without claiming delivery succeeded.
                    _state.update { it.copy(redeliveringId = null, acceptedDeliveryId = deliveryId) }
                    load(reset = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(redeliveringId = null, redeliveryFailure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val webhookId: Long,
        private val repository: GithubRepositoryDetailsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryWebhookDeliveriesViewModel::class.java))
            return RepositoryWebhookDeliveriesViewModel(owner, name, webhookId, repository, sessions) as T
        }
    }
}
