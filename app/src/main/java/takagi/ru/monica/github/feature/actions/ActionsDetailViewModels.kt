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
import takagi.ru.monica.github.domain.GithubArtifactOutput
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubWorkflowArtifact
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
    val actionError: Boolean = false,
    val artifacts: List<GithubWorkflowArtifact> = emptyList(),
    val nextArtifactsPage: Int? = null,
    val isLoadingArtifacts: Boolean = true,
    val isLoadingMoreArtifacts: Boolean = false,
    val artifactsError: Boolean = false,
    val downloadingArtifactId: Long? = null,
    val artifactDownloadFailed: Boolean = false,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMoreJobs: Boolean get() = nextJobsPage != null && !isLoadingJobs && !isLoadingMoreJobs
    val canLoadMoreArtifacts: Boolean get() = nextArtifactsPage != null && !isLoadingArtifacts && !isLoadingMoreArtifacts

    /** Only one archive is streamed at a time, so the picker cannot queue two half-written files. */
    val isDownloadingArtifact: Boolean get() = downloadingArtifactId != null

    /** Re-running and cancelling a run both require push access. */
    val canManageRun: Boolean get() = viewerLogin != null && viewerRole.canPush
}

sealed interface ActionsRunDetailAction {
    data object RetryRun : ActionsRunDetailAction
    data object RetryJobs : ActionsRunDetailAction
    data object LoadMoreJobs : ActionsRunDetailAction
    data class PerformRunAction(val action: GithubWorkflowRunAction) : ActionsRunDetailAction
    data object RetryArtifacts : ActionsRunDetailAction
    data object LoadMoreArtifacts : ActionsRunDetailAction
    data class DownloadArtifact(val artifactId: Long, val open: () -> GithubArtifactOutput) :
        ActionsRunDetailAction
}

class ActionsRunDetailViewModel(
    private val owner: String,
    private val name: String,
    private val runId: Long,
    private val repository: GithubActionsRepository,
    private val detailsRepository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ActionsRunDetailUiState(owner, name, runId))
    val state: StateFlow<ActionsRunDetailUiState> = _state.asStateFlow()
    private var runJob: Job? = null
    private var jobsJob: Job? = null
    private var artifactsJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null

    init {
        loadRun()
        loadJobs(reset = true)
        loadArtifacts(reset = true)
    }

    fun onAction(action: ActionsRunDetailAction) {
        when (action) {
            ActionsRunDetailAction.RetryRun -> loadRun()
            ActionsRunDetailAction.RetryJobs -> loadJobs(reset = _state.value.jobs.isEmpty())
            ActionsRunDetailAction.LoadMoreJobs -> loadJobs(reset = false)
            is ActionsRunDetailAction.PerformRunAction -> performRunAction(action.action)
            ActionsRunDetailAction.RetryArtifacts -> loadArtifacts(reset = _state.value.artifacts.isEmpty())
            ActionsRunDetailAction.LoadMoreArtifacts -> loadArtifacts(reset = false)
            is ActionsRunDetailAction.DownloadArtifact -> downloadArtifact(action)
        }
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

    private fun loadArtifacts(reset: Boolean) {
        val current = _state.value
        if (!reset && !current.canLoadMoreArtifacts) return
        val requestedPage = if (reset) 1 else current.nextArtifactsPage ?: return
        artifactsJob?.cancel()
        _state.update {
            it.copy(
                artifacts = if (reset) emptyList() else it.artifacts,
                isLoadingArtifacts = reset,
                isLoadingMoreArtifacts = !reset,
                artifactsError = false
            )
        }
        artifactsJob = viewModelScope.launch {
            repository.artifacts(owner, name, runId, requestedPage).fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            artifacts = page.mergeItems(state.artifacts, reset, GithubWorkflowArtifact::id),
                            nextArtifactsPage = page.nextPage,
                            isLoadingArtifacts = false,
                            isLoadingMoreArtifacts = false,
                            artifactsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingArtifacts = false,
                            isLoadingMoreArtifacts = false,
                            artifactsError = true
                        )
                    }
                }
            )
        }
    }

    /** A failed transfer can leave partial bytes behind, so the caller is told the download did not finish. */
    private fun downloadArtifact(action: ActionsRunDetailAction.DownloadArtifact) {
        if (_state.value.isDownloadingArtifact) return
        _state.update { it.copy(downloadingArtifactId = action.artifactId, artifactDownloadFailed = false) }
        viewModelScope.launch {
            repository.downloadArtifact(owner, name, action.artifactId, action.open).fold(
                onSuccess = {
                    _state.update { it.copy(downloadingArtifactId = null, artifactDownloadFailed = false) }
                },
                onFailure = {
                    _state.update { it.copy(downloadingArtifactId = null, artifactDownloadFailed = true) }
                }
            )
        }
    }

    private fun performRunAction(action: GithubWorkflowRunAction) {
        if (!_state.value.canManageRun || _state.value.isPerformingAction) return
        _state.update { it.copy(isPerformingAction = true, actionError = false) }
        viewModelScope.launch {
            repository.performRunAction(owner, name, runId, action).fold(
                onSuccess = {
                    _state.update { it.copy(isPerformingAction = false, actionError = false) }
                    loadRun()
                    loadJobs(reset = true)
                    loadArtifacts(reset = true)
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
        private val repository: GithubActionsRepository,
        private val detailsRepository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsRunDetailViewModel::class.java))
            return ActionsRunDetailViewModel(owner, name, runId, repository, detailsRepository) as T
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
