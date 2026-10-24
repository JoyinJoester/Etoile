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
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN,
    val viewerAccountId: Long? = null,
    val journalReady: Boolean = false,
    val journalUnavailable: Boolean = false,
    val unconfirmedAction: Boolean = false
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
    data class PerformRunAction(val action: GithubWorkflowRunAction, val expectedAccountId: Long? = null, val repeatConfirmed: Boolean = false) : ActionsRunDetailAction
    data class Acknowledge(val expectedAccountId: Long?) : ActionsRunDetailAction
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
    private val detailsRepository: GithubRepositoryDetailsRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
    private val journal: GithubActionJournal = InMemoryGithubActionJournal()
) : ViewModel() {
    private val sessionSource = sessions
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(ActionsRunDetailUiState(owner, name, runId))
    val state: StateFlow<ActionsRunDetailUiState> = _state.asStateFlow()
    private var runJob: Job? = null
    private var jobsJob: Job? = null
    private var artifactsJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null
    private var journalJob: Job? = null
    private val operation = GithubActionOperation(GithubActionKind.RUN, runId)
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
                if (isCurrent(id)) _state.update { it.copy(journalReady = true, journalUnavailable = false, unconfirmedAction = operation in pending) }
            }
        }
    }

    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        if (sessions == null) observeJournal()
        loadRun()
        loadJobs(reset = true)
        loadArtifacts(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = ActionsRunDetailUiState(owner, name, runId, viewerAccountId = account?.id, viewerLogin = account?.login)
        observeJournal()
        loadViewerRole(signedIn = account != null)
        loadRun()
        loadJobs(reset = true)
        loadArtifacts(reset = true)
    }

    fun onAction(action: ActionsRunDetailAction) {
        if (action == ActionsRunDetailAction.RetryRun && !_state.value.journalReady) observeJournal()
        when (action) {
            ActionsRunDetailAction.RetryRun -> loadRun()
            ActionsRunDetailAction.RetryJobs -> loadJobs(reset = _state.value.jobs.isEmpty())
            ActionsRunDetailAction.LoadMoreJobs -> loadJobs(reset = false)
            is ActionsRunDetailAction.PerformRunAction -> performRunAction(action.action, action.expectedAccountId, action.repeatConfirmed)
            is ActionsRunDetailAction.Acknowledge -> {
                if (!sessionSource.matchesGithubAccount(accountId, action.expectedAccountId) || !_state.value.journalReady) return
                val id = accountId
                requests.launch {
                    if (!isCurrent(id)) return@launch
                    githubRunCatching { journal.acknowledge(scope(id), operation) }
                }
            }
            ActionsRunDetailAction.RetryArtifacts -> loadArtifacts(reset = _state.value.artifacts.isEmpty())
            ActionsRunDetailAction.LoadMoreArtifacts -> loadArtifacts(reset = false)
            is ActionsRunDetailAction.DownloadArtifact -> downloadArtifact(action)
        }
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

    private fun loadRun() {
        runJob?.cancel()
        _state.update { it.copy(isLoadingRun = true, runError = false) }
        runJob = requests.launch {
            repository.workflowRun(owner, name, runId).currentRepositoryResult().fold(
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
        jobsJob = requests.launch {
            repository.jobs(owner, name, runId, requestedPage).currentRepositoryResult().fold(
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
        artifactsJob = requests.launch {
            repository.artifacts(owner, name, runId, requestedPage).currentRepositoryResult().fold(
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
        requests.launch {
            repository.downloadArtifact(owner, name, action.artifactId, action.open).currentRepositoryResult().fold(
                onSuccess = {
                    _state.update { it.copy(downloadingArtifactId = null, artifactDownloadFailed = false) }
                },
                onFailure = {
                    _state.update { it.copy(downloadingArtifactId = null, artifactDownloadFailed = true) }
                }
            )
        }
    }

    private fun performRunAction(action: GithubWorkflowRunAction, expectedAccountId: Long?, repeatConfirmed: Boolean) {
        if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return
        if (!_state.value.canManageRun || _state.value.isPerformingAction || !_state.value.journalReady) return
        if (_state.value.unconfirmedAction && !repeatConfirmed) return
        val targetRepository = if (sessionSource == null) repository else repository.forAccount(expectedAccountId)
        val id = accountId
        _state.update { it.copy(isPerformingAction = true, actionError = false) }
        requests.launch {
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            val result = journal.runAction(scope(id), operation, repeatConfirmed, { isCurrent(id) }) {
                targetRepository.performRunAction(owner, name, runId, action)
            }.currentRepositoryResult()
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            result.fold(
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
        private val detailsRepository: GithubRepositoryDetailsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
        private val journal: GithubActionJournal = InMemoryGithubActionJournal()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsRunDetailViewModel::class.java))
            return ActionsRunDetailViewModel(owner, name, runId, repository, detailsRepository, sessions, journal) as T
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
    val logError: Boolean = false,
    val viewerAccountId: Long? = null
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
    private val repository: GithubActionsRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
) : ViewModel() {
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(ActionsJobDetailUiState(owner, name, jobId))
    val state: StateFlow<ActionsJobDetailUiState> = _state.asStateFlow()
    private var jobJob: Job? = null
    private var logJob: Job? = null

    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        loadJob()
        loadLog()
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = ActionsJobDetailUiState(owner, name, jobId, viewerAccountId = account?.id)
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
        jobJob = requests.launch {
            repository.job(owner, name, jobId).currentRepositoryResult().fold(
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
        logJob = requests.launch {
            repository.jobLog(owner, name, jobId).currentRepositoryResult().fold(
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
        private val repository: GithubActionsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsJobDetailViewModel::class.java))
            return ActionsJobDetailViewModel(owner, name, jobId, repository, sessions) as T
        }
    }
}
