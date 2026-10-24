package takagi.ru.monica.gitlab.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.gitlab.domain.*

data class GitLabPipelineState(
    val account: GitLabAccount? = null,
    val pipeline: GitLabPipeline? = null,
    val loading: Boolean = false,
    val error: GitLabReadError? = null,
    val jobs: List<GitLabJob> = emptyList(),
    val nextPage: Int? = null,
    val jobsLoading: Boolean = false,
    val jobsError: GitLabReadError? = null,
    val traceJob: GitLabJob? = null,
    val trace: String? = null,
    val traceLoading: Boolean = false,
    val traceError: GitLabReadError? = null,
    val writing: Boolean = false,
    val writeError: GitLabReadError? = null,
    val writeSucceeded: Boolean = false
)

class GitLabPipelineViewModel(
    private val key: String,
    private val projectId: Long,
    private val pipelineId: Long,
    auth: GitLabAuthRepository,
    private val repository: GitLabWorkRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabPipelineState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var detailVersion = 0L; private var jobsVersion = 0L; private var traceVersion = 0L
    private var detailJob: Job? = null; private var jobsJob: Job? = null; private var traceJob: Job? = null; private var writeJob: Job? = null

    init {
        viewModelScope.launch {
            auth.accounts.collect { accounts ->
                val account = accounts.accounts.firstOrNull { it.key == key && accounts.activeKey == key }
                if (account != state.value.account) {
                    epoch++; cancelReads(); writeJob?.cancel()
                    mutableState.value = GitLabPipelineState(account = account)
                    if (account != null) refresh()
                }
            }
        }
    }
    fun onAction(action: GitLabPipelineAction) {
        when (action) {
            GitLabPipelineAction.Refresh -> refresh()
            is GitLabPipelineAction.Jobs -> jobs(action.more)
            is GitLabPipelineAction.Trace -> trace(action.job)
            GitLabPipelineAction.CloseTrace -> closeTrace()
            is GitLabPipelineAction.Run -> pipelineAction(action.expected, action.action)
            is GitLabPipelineAction.Job -> jobAction(action.expected, action.action)
        }
    }
    fun refresh() {
        if (state.value.account == null || state.value.writing || state.value.loading) return
        cancelReads(); val version = detailVersion; val revision = epoch
        mutableState.update { it.copy(loading = true, error = null) }
        detailJob = viewModelScope.launch {
            val result = repository.pipeline(key, projectId, pipelineId)
            currentCoroutineContext().ensureActive()
            if (version != detailVersion || revision != epoch) return@launch
            result.fold(onSuccess = { pipeline -> mutableState.update { it.copy(pipeline = pipeline, loading = false) }; jobs() },
                onFailure = { error -> mutableState.update {
                    if (error.gitLabError() in setOf(GitLabReadError.AUTHENTICATION, GitLabReadError.PERMISSION)) {
                        GitLabPipelineState(account = it.account, error = error.gitLabError())
                    } else it.copy(loading = false, error = error.gitLabError())
                } })
        }
    }
    fun jobs(more: Boolean = false) {
        val before = state.value
        if (before.account == null || before.writing || before.pipeline == null || before.jobsLoading) return
        val page = if (more) before.nextPage ?: return else 1
        jobsJob?.cancel(); val version = ++jobsVersion; val revision = epoch
        mutableState.update { it.copy(jobsLoading = true, jobsError = null) }
        jobsJob = viewModelScope.launch {
            val result = repository.jobs(key, projectId, pipelineId, page)
            currentCoroutineContext().ensureActive()
            if (version != jobsVersion || revision != epoch) return@launch
            result.fold(onSuccess = { response ->
                mutableState.update { it.copy(jobs = ((if (more) it.jobs else emptyList()) + response.items).distinctBy(GitLabJob::id),
                    nextPage = response.nextPage, jobsLoading = false) }
                // A selected job may be on a later page. Keep its log open while
                // refreshing the pipeline rather than navigating away on resume.
                if (!more) state.value.traceJob?.let { selected -> loadTrace(state.value.jobs.firstOrNull { it.id == selected.id } ?: selected) }
            },
                onFailure = { error -> mutableState.update { it.copy(jobsLoading = false, jobsError = error.gitLabError()) } })
        }
    }
    fun trace(job: GitLabJob) {
        if (state.value.account == null || state.value.writing || job !in state.value.jobs) return
        loadTrace(job)
    }
    private fun loadTrace(job: GitLabJob) {
        traceJob?.cancel(); val version = ++traceVersion; val revision = epoch
        mutableState.update { it.copy(traceJob = job, trace = if (it.traceJob?.id == job.id) it.trace else null, traceLoading = true, traceError = null) }
        traceJob = viewModelScope.launch {
            val result = repository.trace(key, projectId, pipelineId, job.id)
            currentCoroutineContext().ensureActive()
            if (version != traceVersion || revision != epoch) return@launch
            result.fold(onSuccess = { value -> mutableState.update { it.copy(trace = value, traceLoading = false) } },
                onFailure = { error -> mutableState.update { it.copy(traceLoading = false, traceError = error.gitLabError()) } })
        }
    }
    fun closeTrace() {
        traceVersion++; traceJob?.cancel()
        mutableState.update { it.copy(traceJob = null, trace = null, traceLoading = false, traceError = null) }
    }
    fun pipelineAction(expected: GitLabPipeline, action: GitLabRunAction) {
        if (expected != state.value.pipeline || action == GitLabRunAction.PLAY ||
            !(if (action == GitLabRunAction.CANCEL) expected.canCancel else expected.canRetry)) return
        write { repository.pipelineAction(key, projectId, pipelineId, action).gitLabApply { value -> mutableState.update { it.copy(pipeline = value) } } }
    }
    fun jobAction(expected: GitLabJob, action: GitLabRunAction) {
        if (expected !in state.value.jobs || !(when (action) {
            GitLabRunAction.PLAY -> expected.canPlay; GitLabRunAction.CANCEL -> expected.canCancel; GitLabRunAction.RETRY -> expected.canRetry
        })) return
        write { repository.jobAction(key, projectId, pipelineId, expected.id, action).gitLabApply { value ->
            mutableState.update { it.copy(jobs = (listOf(value) + it.jobs.filterNot { old -> old.id == expected.id }).distinctBy(GitLabJob::id)) }
        } }
    }
    private fun write(block: suspend () -> Result<Unit>) {
        if (state.value.account == null || state.value.pipeline == null || state.value.writing) return
        cancelReads(); val revision = epoch
        mutableState.update { it.copy(writing = true, writeError = null, writeSucceeded = false, traceJob = null, trace = null) }
        writeJob = viewModelScope.launch {
            val result = block()
            currentCoroutineContext().ensureActive()
            if (revision != epoch) return@launch
            mutableState.update { it.copy(writing = false, writeError = result.exceptionOrNull()?.gitLabError(), writeSucceeded = result.isSuccess) }
            if (result.isSuccess) refresh()
        }
    }
    private fun cancelReads() {
        detailVersion++; jobsVersion++; traceVersion++
        detailJob?.cancel(); jobsJob?.cancel(); traceJob?.cancel()
        mutableState.update { it.copy(loading = false, jobsLoading = false, traceLoading = false) }
    }
}
