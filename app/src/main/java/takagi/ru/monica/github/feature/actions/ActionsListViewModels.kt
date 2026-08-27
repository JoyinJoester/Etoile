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
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val workflowBusyIds: Set<Long> = emptySet(),
    val workflowErrorIds: Set<Long> = emptySet(),
    val dispatchBusyIds: Set<Long> = emptySet(),
    val dispatchErrorIds: Set<Long> = emptySet()
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface ActionsWorkflowsAction {
    data object Retry : ActionsWorkflowsAction
    data object LoadMore : ActionsWorkflowsAction
    data class SetWorkflowEnabled(val workflowId: Long, val enabled: Boolean) : ActionsWorkflowsAction
    data class DispatchWorkflow(val workflowId: Long, val ref: String, val inputs: Map<String, String>) : ActionsWorkflowsAction
}

class ActionsWorkflowsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubActionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ActionsWorkflowsUiState(owner, name))
    val state: StateFlow<ActionsWorkflowsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: ActionsWorkflowsAction) {
        when (action) {
            ActionsWorkflowsAction.Retry -> load(reset = _state.value.items.isEmpty())
            ActionsWorkflowsAction.LoadMore -> load(reset = false)
            is ActionsWorkflowsAction.SetWorkflowEnabled -> setWorkflowEnabled(action.workflowId, action.enabled)
            is ActionsWorkflowsAction.DispatchWorkflow -> dispatchWorkflow(action)
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.workflows(owner, name, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubWorkflow::id),
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

    private fun setWorkflowEnabled(workflowId: Long, enabled: Boolean) {
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
        private val repository: GithubActionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsWorkflowsViewModel::class.java))
            return ActionsWorkflowsViewModel(owner, name, repository) as T
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
    val isLoadingMore: Boolean = false,
    val error: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface WorkflowRunsAction {
    data object Retry : WorkflowRunsAction
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

    init {
        load(reset = true)
    }

    fun onAction(action: WorkflowRunsAction) {
        when (action) {
            WorkflowRunsAction.Retry -> load(reset = _state.value.items.isEmpty())
            WorkflowRunsAction.LoadMore -> load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val requestedPage = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.workflowRuns(owner, name, workflowId, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubWorkflowRun::id),
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
