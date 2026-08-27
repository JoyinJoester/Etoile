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
import takagi.ru.monica.github.domain.GithubActionsLog
import takagi.ru.monica.github.domain.GithubActionsRepository
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.mergeItems
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowRunAction

@Immutable
data class ActionsRunDetailUiState(
    val owner: String,
    val name: String,
    val runId: Long,
    val run: GithubWorkflowRun? = null,
    val jobs: List<GithubWorkflowJob> = emptyList(),
    val nextJobsPage: Int? = null,
    val isLoadingRun: Boolean = true,
    val isLoadingJobs: Boolean = true,
    val isLoadingMoreJobs: Boolean = false,
    val runError: Boolean = false,
    val jobsError: Boolean = false,
    val isPerformingAction: Boolean = false,
    val actionError: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMoreJobs: Boolean get() = nextJobsPage != null && !isLoadingJobs && !isLoadingMoreJobs
}

sealed interface ActionsRunDetailAction {
    data object RetryRun : ActionsRunDetailAction
    data object RetryJobs : ActionsRunDetailAction
    data object LoadMoreJobs : ActionsRunDetailAction
    data class PerformRunAction(val action: GithubWorkflowRunAction) : ActionsRunDetailAction
}

class ActionsRunDetailViewModel(
    private val owner: String,
    private val name: String,
    private val runId: Long,
    private val repository: GithubActionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ActionsRunDetailUiState(owner, name, runId))
    val state: StateFlow<ActionsRunDetailUiState> = _state.asStateFlow()
    private var runJob: Job? = null
    private var jobsJob: Job? = null

    init {
        loadRun()
        loadJobs(reset = true)
    }

    fun onAction(action: ActionsRunDetailAction) {
        when (action) {
            ActionsRunDetailAction.RetryRun -> loadRun()
            ActionsRunDetailAction.RetryJobs -> loadJobs(reset = _state.value.jobs.isEmpty())
            ActionsRunDetailAction.LoadMoreJobs -> loadJobs(reset = false)
            is ActionsRunDetailAction.PerformRunAction -> performRunAction(action.action)
        }
    }

    private fun loadRun() {
        runJob?.cancel()
        _state.update { it.copy(isLoadingRun = true, runError = false) }
        runJob = viewModelScope.launch {
            repository.workflowRun(owner, name, runId).fold(
                onSuccess = { run ->
                    _state.update { it.copy(run = run, isLoadingRun = false, runError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingRun = false, runError = true) }
                }
            )
        }
    }

    private fun loadJobs(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreJobs) return
        val requestedPage = if (reset) 1 else current.nextJobsPage ?: return
        jobsJob?.cancel()
        _state.update {
            it.copy(
                jobs = if (reset) emptyList() else it.jobs,
                isLoadingJobs = reset,
                isLoadingMoreJobs = !reset,
                jobsError = false
            )
        }
        jobsJob = viewModelScope.launch {
            repository.jobs(owner, name, runId, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            jobs = page.mergeItems(state.jobs, reset, GithubWorkflowJob::id),
                            nextJobsPage = page.nextPage,
                            isLoadingJobs = false,
                            isLoadingMoreJobs = false,
                            jobsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingJobs = false, isLoadingMoreJobs = false, jobsError = true) }
                }
            )
        }
    }

    private fun performRunAction(action: GithubWorkflowRunAction) {
        if (_state.value.isPerformingAction) return
        _state.update { it.copy(isPerformingAction = true, actionError = false) }
        viewModelScope.launch {
            repository.performRunAction(owner, name, runId, action).fold(
                onSuccess = {
                    _state.update { it.copy(isPerformingAction = false, actionError = false) }
                    loadRun()
                    loadJobs(reset = true)
                },
                onFailure = {
                    _state.update { it.copy(isPerformingAction = false, actionError = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val runId: Long,
        private val repository: GithubActionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsRunDetailViewModel::class.java))
            return ActionsRunDetailViewModel(owner, name, runId, repository) as T
        }
    }
}

@Immutable
data class ActionsJobDetailUiState(
    val owner: String,
    val name: String,
    val jobId: Long,
    val job: GithubWorkflowJob? = null,
    val log: GithubActionsLog? = null,
    val isLoadingJob: Boolean = true,
    val isLoadingLog: Boolean = true,
    val jobError: Boolean = false,
    val logError: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}

sealed interface ActionsJobDetailAction {
    data object RetryJob : ActionsJobDetailAction
    data object RetryLog : ActionsJobDetailAction
}

class ActionsJobDetailViewModel(
    private val owner: String,
    private val name: String,
    private val jobId: Long,
    private val repository: GithubActionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ActionsJobDetailUiState(owner, name, jobId))
    val state: StateFlow<ActionsJobDetailUiState> = _state.asStateFlow()
    private var jobJob: Job? = null
    private var logJob: Job? = null

    init {
        loadJob()
        loadLog()
    }

    fun onAction(action: ActionsJobDetailAction) {
        when (action) {
            ActionsJobDetailAction.RetryJob -> loadJob()
            ActionsJobDetailAction.RetryLog -> loadLog()
        }
    }

    private fun loadJob() {
        jobJob?.cancel()
        _state.update { it.copy(isLoadingJob = true, jobError = false) }
        jobJob = viewModelScope.launch {
            repository.job(owner, name, jobId).fold(
                onSuccess = { job ->
                    _state.update { it.copy(job = job, isLoadingJob = false, jobError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingJob = false, jobError = true) }
                }
            )
        }
    }

    private fun loadLog() {
        logJob?.cancel()
        _state.update { it.copy(isLoadingLog = true, logError = false) }
        logJob = viewModelScope.launch {
            repository.jobLog(owner, name, jobId).fold(
                onSuccess = { log ->
                    _state.update { it.copy(log = log, isLoadingLog = false, logError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingLog = false, logError = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val jobId: Long,
        private val repository: GithubActionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsJobDetailViewModel::class.java))
            return ActionsJobDetailViewModel(owner, name, jobId, repository) as T
        }
    }
}
