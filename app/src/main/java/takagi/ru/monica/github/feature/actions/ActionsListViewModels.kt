package takagi.ru.monica.github.feature.actions

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
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
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
    data class SetWorkflowEnabled(val workflowId: Long, val enabled: Boolean) : ActionsWorkflowsAction
    data class DispatchWorkflow(val workflowId: Long, val ref: String, val inputs: Map<String, String>) : ActionsWorkflowsAction
}

class ActionsWorkflowsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubActionsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ActionsWorkflowsUiState(owner, name))
    val state: StateFlow<ActionsWorkflowsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null
    private var failedReset = true

    init {
        load(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login == sessionLogin) return
        sessionLogin = login
        _state.update { it.copy(viewerLogin = login) }
        loadViewerRole(signedIn = login != null)
    }

    private fun loadViewerRole(signedIn: Boolean) {
        viewerRoleJob?.cancel()
        if (!signedIn) {
            _state.update { it.copy(viewerRole = GithubCollaboratorRole.UNKNOWN) }
            return
        }
        viewerRoleJob = viewModelScope.launch {
            val role = detailsRepository.details(owner, name)
                .getOrNull()
                ?.viewerRole
                ?: GithubCollaboratorRole.UNKNOWN
            _state.update { it.copy(viewerRole = role) }
        }
    }

    fun onAction(action: ActionsWorkflowsAction) {
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
            is ActionsWorkflowsAction.SetWorkflowEnabled -> setWorkflowEnabled(action.workflowId, action.enabled)
            is ActionsWorkflowsAction.DispatchWorkflow -> dispatchWorkflow(action)
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
        loadJob = viewModelScope.launch {
            val result = if (forceRefresh) {
                repository.refreshWorkflows(owner, name, requestedPage)
            } else {
                repository.workflows(owner, name, requestedPage)
            }
            result.fold(
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

    private fun setWorkflowEnabled(workflowId: Long, enabled: Boolean) {
        if (!_state.value.canManageWorkflows) return
        if (workflowId in _state.value.workflowBusyIds) return
        _state.update {
            it.copy(
                workflowBusyIds = it.workflowBusyIds + workflowId,
                workflowErrorIds = it.workflowErrorIds - workflowId
            )
        }
        viewModelScope.launch {
            repository.setWorkflowEnabled(owner, name, workflowId, enabled).fold(
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
        if (!_state.value.canManageWorkflows) return
        if (action.workflowId in _state.value.dispatchBusyIds) return
        _state.update {
            it.copy(
                dispatchBusyIds = it.dispatchBusyIds + action.workflowId,
                dispatchErrorIds = it.dispatchErrorIds - action.workflowId
            )
        }
        viewModelScope.launch {
            repository.dispatchWorkflow(owner, name, action.workflowId, action.ref, action.inputs).fold(
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
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsWorkflowsViewModel::class.java))
            return ActionsWorkflowsViewModel(owner, name, repository, detailsRepository) as T
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
    val error: Boolean = false
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
    workflowName: String,
    private val repository: GithubActionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        WorkflowRunsUiState(owner, name, workflowId, workflowName)
    )
    val state: StateFlow<WorkflowRunsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true

    init {
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
        loadJob = viewModelScope.launch {
            val result = if (forceRefresh) {
                repository.refreshWorkflowRuns(owner, name, workflowId, requestedPage)
            } else {
                repository.workflowRuns(owner, name, workflowId, requestedPage)
            }
            result.fold(
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
        private val repository: GithubActionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkflowRunsViewModel::class.java))
            return WorkflowRunsViewModel(owner, name, workflowId, workflowName, repository) as T
        }
    }
}
