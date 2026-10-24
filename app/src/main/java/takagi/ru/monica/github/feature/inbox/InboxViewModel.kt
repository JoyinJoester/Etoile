package takagi.ru.monica.github.feature.inbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

enum class InboxFilter { UNREAD, MENTIONS, REVIEWS, READ }

/** Only the read view needs threads the server marks as read, so it needs a different query. */
val InboxFilter.includeRead: Boolean get() = this == InboxFilter.READ

@Immutable
data class InboxUiState(
    val items: List<GithubNotification> = emptyList(),
    val selectedFilter: InboxFilter = InboxFilter.UNREAD,
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
    // unreadIds is the single source of read state: it also drives the row dot and optimistic
    // marking, so a thread leaves the unread views the moment it is opened.
    private val unreadItems: List<GithubNotification>
        get() = items.filter { it.id in unreadIds }

    val visibleItems: List<GithubNotification>
        get() = when (selectedFilter) {
            InboxFilter.UNREAD -> unreadItems
            InboxFilter.MENTIONS -> unreadItems.filter { it.reason == GithubNotificationReason.MENTION }
            InboxFilter.REVIEWS -> unreadItems.filter { it.reason == GithubNotificationReason.REVIEW_REQUESTED }
            InboxFilter.READ -> items.filterNot { it.id in unreadIds }
        }

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
    private var sessionAccountId: Long? = null
    private var sessionRevision = 0L
    private var loadVersion = 0L
    private var nextReadOperation = 0L
    private var serverUnreadIds: Set<String> = emptySet()
    private val pendingReads = mutableMapOf<Long, Map<String, String>>()
    private val confirmedReads = mutableSetOf<Pair<String, String>>()
    private val completedThreads = mutableMapOf<String, String>()
    private val readJobs = mutableMapOf<Long, Job>()

    fun onSessionChanged(session: GithubSession) {
        sessionRevision++
        loadVersion++
        loadJob?.cancel()
        cancelTriageJobs()
        readJobs.values.toList().forEach { it.cancel() }
        readJobs.clear()
        pendingReads.clear()
        confirmedReads.clear()
        completedThreads.clear()
        serverUnreadIds = emptySet()
        sessionAccountId = null
        when (session) {
            GithubSession.Loading -> {
                _state.value = InboxUiState(isLoading = true)
            }
            GithubSession.SignedOut -> {
                loadJob?.cancel()
                cancelTriageJobs()
                _state.value = InboxUiState(requiresAuthentication = true)
            }
            is GithubSession.Error -> {
                cancelTriageJobs()
                _state.value = InboxUiState(error = true)
            }
            is GithubSession.SignedIn -> {
                sessionAccountId = session.account.id
                _state.value = InboxUiState(requiresAuthentication = false)
                refresh(preserveExisting = false)
            }
        }
    }

    fun onAction(action: InboxAction) {
        if (sessionAccountId == null) return
        when (action) {
            is InboxAction.SelectFilter -> {
                val reloadRequired = _state.value.selectedFilter.includeRead != action.filter.includeRead
                _state.update { it.copy(selectedFilter = action.filter) }
                if (reloadRequired) refresh(preserveExisting = false)
            }
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
        if (sessionAccountId == null) return
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        val includeRead = current.selectedFilter.includeRead
        loadJob?.cancel()
        val revision = sessionRevision
        val version = ++loadVersion
        if (reset && !preserveExisting) serverUnreadIds = emptySet()
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
            val result = repository.notifications(page = requestedPage, includeRead = includeRead)
            currentCoroutineContext().ensureActive()
            if (revision != sessionRevision || version != loadVersion) return@launch
            result.fold(
                onSuccess = { page ->
                    _state.update { state ->
                        val pageUnreadIds = page.items
                            .filter(GithubNotification::unread)
                            .mapTo(linkedSetOf(), GithubNotification::id)
                        val pageIds = page.items.mapTo(hashSetOf(), GithubNotification::id)
                        serverUnreadIds = if (reset) pageUnreadIds else (serverUnreadIds - pageIds) + pageUnreadIds
                        val items = page.mergeItems(state.items, reset, GithubNotification::id)
                            .filterNot { completedThreads[it.id] == it.updatedAt }
                        state.copy(
                            items = items,
                            unreadIds = unreadIdsFor(items),
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
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        read(mapOf(id to item.updatedAt)) { repository.markRead(id) }
    }

    private fun markAllRead() {
        if (_state.value.unreadIds.isEmpty()) return
        // Include pending individual reads: a failed earlier request cannot undo this bulk success.
        val targets = _state.value.items.filter { it.id in serverUnreadIds }
            .associate { it.id to it.updatedAt }
        read(targets) { repository.markAllRead() }
    }

    /** Keep the server snapshot separate from pending mutations so refresh/rollback cannot undo each other. */
    private fun unreadIdsFor(items: List<GithubNotification>): Set<String> = items.asSequence()
        .filter { item ->
            item.id in serverUnreadIds && (item.id to item.updatedAt) !in confirmedReads &&
                pendingReads.values.none { it[item.id] == item.updatedAt }
        }
        .map(GithubNotification::id).toSet()

    private fun read(targets: Map<String, String>, request: suspend () -> Result<Unit>) {
        val revision = sessionRevision
        val operation = ++nextReadOperation
        pendingReads[operation] = targets
        _state.update { it.copy(unreadIds = unreadIdsFor(it.items)) }
        readJobs[operation] = viewModelScope.launch {
            try {
                currentCoroutineContext().ensureActive()
                if (revision != sessionRevision) return@launch
                val result = request()
                currentCoroutineContext().ensureActive()
                if (revision != sessionRevision) return@launch
                pendingReads.remove(operation)
                if (result.isSuccess) confirmedReads.addAll(targets.map { it.key to it.value })
                _state.update {
                    it.copy(unreadIds = unreadIdsFor(it.items), actionError = result.isFailure)
                }
            } finally {
                if (revision == sessionRevision) readJobs.remove(operation)
            }
        }
    }

    private fun triage(id: String, request: suspend () -> Result<Unit>) {
        val current = _state.value
        if (current.items.none { it.id == id } || id in current.triageBusyIds) return
        val revision = sessionRevision
        val updatedAt = current.items.first { it.id == id }.updatedAt
        _state.update {
            it.copy(
                triageBusyIds = it.triageBusyIds + id,
                triageErrorIds = it.triageErrorIds - id
            )
        }
        val job = viewModelScope.launch {
            try {
                currentCoroutineContext().ensureActive()
                if (revision != sessionRevision) return@launch
                val result = request()
                currentCoroutineContext().ensureActive()
                if (revision != sessionRevision) return@launch
                result.fold(
                    onSuccess = {
                        completedThreads[id] = updatedAt
                        _state.update { state ->
                            val items = state.items.filterNot { it.id == id && it.updatedAt == updatedAt }
                            state.copy(
                                items = items,
                                unreadIds = unreadIdsFor(items),
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
                if (revision == sessionRevision) {
                    _state.update { state ->
                        state.copy(triageBusyIds = state.triageBusyIds - id)
                    }
                    triageJobs.remove(id)
                }
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
