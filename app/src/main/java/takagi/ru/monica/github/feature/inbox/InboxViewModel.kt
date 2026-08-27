package takagi.ru.monica.github.feature.inbox

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
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubNotificationReason
import takagi.ru.monica.github.domain.GithubNotificationsRepository
import takagi.ru.monica.github.domain.mergeItems
import takagi.ru.monica.github.domain.GithubSession

enum class InboxFilter { ALL, MENTIONS, REVIEWS }

@Immutable
data class InboxUiState(
    val items: List<GithubNotification> = emptyList(),
    val selectedFilter: InboxFilter = InboxFilter.ALL,
    val unreadIds: Set<String> = emptySet(),
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val requiresAuthentication: Boolean = true,
    val error: Boolean = false,
    val refreshError: Boolean = false,
    val actionError: Boolean = false,
    val triageBusyIds: Set<String> = emptySet(),
    val triageErrorIds: Set<String> = emptySet()
) {
    val visibleItems: List<GithubNotification>
        get() = when (selectedFilter) {
            InboxFilter.ALL -> items
            InboxFilter.MENTIONS -> items.filter { it.reason == GithubNotificationReason.MENTION }
            InboxFilter.REVIEWS -> items.filter { it.reason == GithubNotificationReason.REVIEW_REQUESTED }
        }

    val needsAttentionCount: Int
        get() = items.count { it.reason == GithubNotificationReason.REVIEW_REQUESTED || it.reason == GithubNotificationReason.ASSIGN }

    val assignedCount: Int
        get() = items.count { it.reason == GithubNotificationReason.ASSIGN }

    val canLoadMore: Boolean
        get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore

    val isTriaging: Boolean get() = triageBusyIds.isNotEmpty()
}

sealed interface InboxAction {
    data class SelectFilter(val filter: InboxFilter) : InboxAction
    data class OpenNotification(val id: String) : InboxAction
    data class MarkDone(val id: String) : InboxAction
    data class Unsubscribe(val id: String) : InboxAction
    data object MarkAllRead : InboxAction
    data object Refresh : InboxAction
    data object LoadMore : InboxAction
}

class InboxViewModel(
    private val repository: GithubNotificationsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private val triageJobs = mutableMapOf<String, Job>()
    private var sessionLogin: String? = null

    fun onSessionChanged(session: GithubSession) {
        when (session) {
            GithubSession.Loading -> {
                cancelTriageJobs()
                sessionLogin = null
                _state.update {
                    it.copy(
                        isLoading = true,
                        isRefreshing = false,
                        error = false,
                        refreshError = false,
                        actionError = false,
                        triageBusyIds = emptySet()
                    )
                }
            }
            GithubSession.SignedOut -> {
                loadJob?.cancel()
                cancelTriageJobs()
                sessionLogin = null
                _state.value = InboxUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> {
                cancelTriageJobs()
                sessionLogin = null
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = true,
                        refreshError = false,
                        actionError = false,
                        triageBusyIds = emptySet(),
                        requiresAuthentication = false
                    )
                }
            }
            is GithubSession.SignedIn -> {
                if (sessionLogin != null && sessionLogin != session.account.login) {
                    cancelTriageJobs()
                }
                sessionLogin = session.account.login
                refresh(preserveExisting = false)
            }
        }
    }

    fun onAction(action: InboxAction) {
        when (action) {
            is InboxAction.SelectFilter -> _state.update { it.copy(selectedFilter = action.filter) }
            is InboxAction.OpenNotification -> markRead(action.id)
            is InboxAction.MarkDone -> triage(action.id) { repository.markDone(action.id) }
            is InboxAction.Unsubscribe -> triage(action.id) {
                repository.unsubscribeAndMarkDone(action.id)
            }
            InboxAction.MarkAllRead -> markAllRead()
            InboxAction.Refresh -> refresh(preserveExisting = _state.value.items.isNotEmpty())
            InboxAction.LoadMore -> load(reset = false)
        }
    }

    private fun refresh(preserveExisting: Boolean) {
        load(reset = true, preserveExisting = preserveExisting)
    }

    private fun load(reset: Boolean, preserveExisting: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !preserveExisting) emptyList() else it.items,
                unreadIds = if (reset && !preserveExisting) emptySet() else it.unreadIds,
                nextPage = if (reset) null else it.nextPage,
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                requiresAuthentication = false,
                error = false,
                refreshError = false,
                actionError = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.notifications(page = requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        val pageUnreadIds = page.items
                            .filter(GithubNotification::unread)
                            .mapTo(linkedSetOf(), GithubNotification::id)
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubNotification::id),
                            unreadIds = if (reset) pageUnreadIds else state.unreadIds + pageUnreadIds,
                            nextPage = page.nextPage,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = false,
                            refreshError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = true,
                            refreshError = reset
                        )
                    }
                }
            )
        }
    }

    private fun markRead(id: String) {
        if (id !in _state.value.unreadIds) return
        _state.update { it.copy(unreadIds = it.unreadIds - id) }
        viewModelScope.launch {
            repository.markRead(id).fold(
                onSuccess = { _state.update { it.copy(actionError = false) } },
                onFailure = {
                    _state.update { state ->
                        state.copy(unreadIds = state.unreadIds + id, actionError = true)
                    }
                }
            )
        }
    }

    private fun markAllRead() {
        val previous = _state.value.unreadIds
        if (previous.isEmpty()) return
        _state.update { it.copy(unreadIds = emptySet()) }
        viewModelScope.launch {
            repository.markAllRead().fold(
                onSuccess = { _state.update { it.copy(actionError = false) } },
                onFailure = {
                    _state.update { state ->
                        state.copy(unreadIds = previous, actionError = true)
                    }
                }
            )
        }
    }

    private fun triage(id: String, request: suspend () -> Result<Unit>) {
        val current = _state.value
        if (current.items.none { it.id == id } || id in current.triageBusyIds) return
        _state.update {
            it.copy(
                triageBusyIds = it.triageBusyIds + id,
                triageErrorIds = it.triageErrorIds - id
            )
        }
        val job = viewModelScope.launch {
            try {
                request().fold(
                    onSuccess = {
                        _state.update { state ->
                            state.copy(
                                items = state.items.filterNot { it.id == id },
                                unreadIds = state.unreadIds - id,
                                triageErrorIds = state.triageErrorIds - id
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state ->
                            state.copy(triageErrorIds = state.triageErrorIds + id)
                        }
                    }
                )
            } finally {
                _state.update { state ->
                    state.copy(triageBusyIds = state.triageBusyIds - id)
                }
                triageJobs.remove(id)
            }
        }
        triageJobs[id] = job
    }

    private fun cancelTriageJobs() {
        triageJobs.values.toList().forEach { it.cancel() }
        triageJobs.clear()
    }

    class Factory(private val repository: GithubNotificationsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InboxViewModel::class.java))
            return InboxViewModel(repository) as T
        }
    }
}
