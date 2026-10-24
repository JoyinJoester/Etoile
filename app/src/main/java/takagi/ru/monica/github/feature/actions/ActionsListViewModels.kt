package takagi.ru.monica.github.feature.actions

import takagi.ru.monica.github.data.runAction
import takagi.ru.monica.github.feature.repository.currentRepositoryResult
import takagi.ru.monica.github.feature.repository.matchesGithubAccount
import takagi.ru.monica.github.data.InMemoryGithubActionJournal
import takagi.ru.monica.github.data.githubRunCatching
import takagi.ru.monica.github.domain.GithubActionJournal
import takagi.ru.monica.github.domain.GithubActionKind
import takagi.ru.monica.github.domain.GithubActionOperation
import takagi.ru.monica.github.domain.GithubActionScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubActionsRepository
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowState
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class ActionsWorkflowsUiState(
    val owner: String,
    val name: String,
    val items: List<GithubWorkflow> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val workflowBusyIds: Set<Long> = emptySet(),
    val workflowErrorIds: Set<Long> = emptySet(),
    val dispatchBusyIds: Set<Long> = emptySet(),
    val dispatchErrorIds: Set<Long> = emptySet(),
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN,
    val viewerAccountId: Long? = null,
    val journalReady: Boolean = false,
    val journalUnavailable: Boolean = false,
    val unconfirmedOperations: Set<GithubActionOperation> = emptySet()
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore

    /** Enabling and dispatching workflows both require push access. */
    val canManageWorkflows: Boolean get() = viewerLogin != null && viewerRole.canPush
}

sealed interface ActionsWorkflowsAction {
    data object Retry : ActionsWorkflowsAction
    data object Refresh : ActionsWorkflowsAction
    data object LoadMore : ActionsWorkflowsAction
    data class SetWorkflowEnabled(val workflowId: Long, val enabled: Boolean, val expectedAccountId: Long? = null, val repeatConfirmed: Boolean = false) : ActionsWorkflowsAction
    data class DispatchWorkflow(val workflowId: Long, val ref: String, val inputs: Map<String, String>, val expectedAccountId: Long? = null, val repeatConfirmed: Boolean = false) : ActionsWorkflowsAction
    data class Acknowledge(val operation: GithubActionOperation, val expectedAccountId: Long?) : ActionsWorkflowsAction
}

class ActionsWorkflowsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubActionsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
    private val journal: GithubActionJournal = InMemoryGithubActionJournal()
) : ViewModel() {
    private val sessionSource = sessions
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(ActionsWorkflowsUiState(owner, name))
    val state: StateFlow<ActionsWorkflowsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null
    private var failedReset = true
    private var journalJob: Job? = null

    private fun scope(id: Long? = accountId) = GithubActionScope(id ?: 0L, owner, name)
    private fun isCurrent(id: Long?) = accountId == id && sessionSource.matchesGithubAccount(accountId, id)
    private fun observeJournal() {
        journalJob?.cancel()
        val id = accountId
        if (sessionSource != null && id == null) return
        journalJob = requests.launch {
            journal.observe(scope(id)).catch {
                if (isCurrent(id)) _state.update { it.copy(journalReady = false, journalUnavailable = true) }
            }.collect { pending ->
                if (isCurrent(id)) _state.update { it.copy(journalReady = true, journalUnavailable = false, unconfirmedOperations = pending) }
            }
        }
    }

    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        if (sessions == null) observeJournal()
        load(reset = true)
    }



    private fun loadViewerRole(signedIn: Boolean) {
        viewerRoleJob?.cancel()
        if (!signedIn) {
            _state.update { it.copy(viewerRole = GithubCollaboratorRole.UNKNOWN) }
            return
        }
        viewerRoleJob = requests.launch {
            val role = detailsRepository.details(owner, name)
                .currentRepositoryResult().getOrNull()
                ?.viewerRole
                ?: GithubCollaboratorRole.UNKNOWN
            _state.update { it.copy(viewerRole = role) }
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = ActionsWorkflowsUiState(owner, name, viewerAccountId = account?.id, viewerLogin = account?.login)
        observeJournal()
        loadViewerRole(signedIn = account != null)
        load(reset = true)
    }

    fun onAction(action: ActionsWorkflowsAction) {
        if ((action == ActionsWorkflowsAction.Refresh || action == ActionsWorkflowsAction.Retry) && !_state.value.journalReady) observeJournal()
        when (action) {
            ActionsWorkflowsAction.Retry -> load(
                reset = failedReset,
                preserveExisting = failedReset && _state.value.items.isNotEmpty(),
                forceRefresh = failedReset
            )
            ActionsWorkflowsAction.Refresh -> load(
                reset = true,
                preserveExisting = _state.value.items.isNotEmpty(),
                forceRefresh = true
            )
            ActionsWorkflowsAction.LoadMore -> load(reset = false)
            is ActionsWorkflowsAction.SetWorkflowEnabled -> setWorkflowEnabled(action.workflowId, action.enabled, action.expectedAccountId, action.repeatConfirmed)
            is ActionsWorkflowsAction.DispatchWorkflow -> dispatchWorkflow(action)
            is ActionsWorkflowsAction.Acknowledge -> {
                if (!sessionSource.matchesGithubAccount(accountId, action.expectedAccountId) || !_state.value.journalReady) return
                val id = accountId
                requests.launch {
                    if (!isCurrent(id)) return@launch
                    githubRunCatching { journal.acknowledge(scope(id), action.operation) }
                }
            }
        }
    }

    private fun load(
        reset: Boolean,
        preserveExisting: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !preserveExisting) emptyList() else it.items,
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = requests.launch {
            val result = if (forceRefresh) {
                repository.refreshWorkflows(owner, name, requestedPage)
            } else {
                repository.workflows(owner, name, requestedPage)
            }
            result.currentRepositoryResult().fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubWorkflow::id),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    failedReset = reset
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = true)
                    }
                }
            )
        }
    }

    private fun setWorkflowEnabled(workflowId: Long, enabled: Boolean, expectedAccountId: Long?, repeatConfirmed: Boolean) {
        if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return
        val operation = GithubActionOperation(GithubActionKind.TOGGLE, workflowId)
        if (!_state.value.canManageWorkflows || !_state.value.journalReady) return
        if (operation in _state.value.unconfirmedOperations && !repeatConfirmed) return
        if (workflowId in _state.value.workflowBusyIds) return
        val targetRepository = if (sessionSource == null) repository else repository.forAccount(expectedAccountId)
        val id = accountId
        _state.update {
            it.copy(
                workflowBusyIds = it.workflowBusyIds + workflowId,
                workflowErrorIds = it.workflowErrorIds - workflowId
            )
        }
        requests.launch {
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            val result = journal.runAction(scope(id), operation, repeatConfirmed, { isCurrent(id) }) {
                targetRepository.setWorkflowEnabled(owner, name, workflowId, enabled)
            }.currentRepositoryResult()
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            items = state.items.map { workflow ->
                                if (workflow.id == workflowId) {
                                    workflow.copy(
                                        state = if (enabled) GithubWorkflowState.ACTIVE
                                        else GithubWorkflowState.DISABLED_MANUALLY
                                    )
                                } else workflow
                            },
                            workflowBusyIds = state.workflowBusyIds - workflowId,
                            workflowErrorIds = state.workflowErrorIds - workflowId
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            workflowBusyIds = it.workflowBusyIds - workflowId,
                            workflowErrorIds = it.workflowErrorIds + workflowId
                        )
                    }
                }
            )
        }
    }

    private fun dispatchWorkflow(action: ActionsWorkflowsAction.DispatchWorkflow) {
        if (!sessionSource.matchesGithubAccount(accountId, action.expectedAccountId)) return
        val operation = GithubActionOperation(GithubActionKind.DISPATCH, action.workflowId)
        if (!_state.value.canManageWorkflows || !_state.value.journalReady) return
        if (operation in _state.value.unconfirmedOperations && !action.repeatConfirmed) return
        if (action.workflowId in _state.value.dispatchBusyIds) return
        val targetRepository = if (sessionSource == null) repository else repository.forAccount(action.expectedAccountId)
        val id = accountId
        _state.update {
            it.copy(
                dispatchBusyIds = it.dispatchBusyIds + action.workflowId,
                dispatchErrorIds = it.dispatchErrorIds - action.workflowId
            )
        }
        requests.launch {
            if (!sessionSource.matchesGithubAccount(accountId, action.expectedAccountId)) return@launch
            val result = journal.runAction(scope(id), operation, action.repeatConfirmed, { isCurrent(id) }) {
                targetRepository.dispatchWorkflow(owner, name, action.workflowId, action.ref, action.inputs)
            }.currentRepositoryResult()
            if (!sessionSource.matchesGithubAccount(accountId, action.expectedAccountId)) return@launch
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(dispatchBusyIds = it.dispatchBusyIds - action.workflowId)
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            dispatchBusyIds = it.dispatchBusyIds - action.workflowId,
                            dispatchErrorIds = it.dispatchErrorIds + action.workflowId
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubActionsRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
        private val journal: GithubActionJournal = InMemoryGithubActionJournal()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsWorkflowsViewModel::class.java))
            return ActionsWorkflowsViewModel(owner, name, repository, detailsRepository, sessions, journal) as T
        }
    }
}

@Immutable
data class WorkflowRunsUiState(
    val owner: String,
    val name: String,
    val workflowId: Long,
    val workflowName: String,
    val items: List<GithubWorkflowRun> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val viewerAccountId: Long? = null
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
}

sealed interface WorkflowRunsAction {
    data object Retry : WorkflowRunsAction
    data object Refresh : WorkflowRunsAction
    data object LoadMore : WorkflowRunsAction
}

class WorkflowRunsViewModel(
    private val owner: String,
    private val name: String,
    private val workflowId: Long,
    private val workflowName: String,
    private val repository: GithubActionsRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
) : ViewModel() {
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(
        WorkflowRunsUiState(owner, name, workflowId, workflowName)
    )
    val state: StateFlow<WorkflowRunsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true

    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        load(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = WorkflowRunsUiState(owner, name, workflowId, workflowName, viewerAccountId = account?.id)
        load(reset = true)
    }

    fun onAction(action: WorkflowRunsAction) {
        when (action) {
            WorkflowRunsAction.Retry -> load(
                reset = failedReset,
                preserveExisting = failedReset && _state.value.items.isNotEmpty(),
                forceRefresh = failedReset
            )
            WorkflowRunsAction.Refresh -> load(
                reset = true,
                preserveExisting = _state.value.items.isNotEmpty(),
                forceRefresh = true
            )
            WorkflowRunsAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(
        reset: Boolean,
        preserveExisting: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !preserveExisting) emptyList() else it.items,
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = requests.launch {
            val result = if (forceRefresh) {
                repository.refreshWorkflowRuns(owner, name, workflowId, requestedPage)
            } else {
                repository.workflowRuns(owner, name, workflowId, requestedPage)
            }
            result.currentRepositoryResult().fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubWorkflowRun::id),
                            nextPage = page.nextPage,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    failedReset = reset
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = true)
                    }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val workflowId: Long,
        private val workflowName: String,
        private val repository: GithubActionsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkflowRunsViewModel::class.java))
            return WorkflowRunsViewModel(owner, name, workflowId, workflowName, repository, sessions) as T
        }
    }
}
