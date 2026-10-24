package takagi.ru.monica.github.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.*

data class CreatePullRequestState(
    val title: String = "", val body: String = "", val base: String = "", val head: String = "",
    val headRepository: String = "", val draft: Boolean = false, val maintainerCanModify: Boolean = false,
    val branches: List<String> = emptyList(), val branchPage: Int? = 1,
    val loadingBranches: Boolean = false, val branchesError: Boolean = false,
    val submitting: Boolean = false, val failed: Boolean = false, val created: GithubPullRequest? = null,
    val comparison: GithubBranchComparison? = null, val comparing: Boolean = false, val compareError: Boolean = false,
    val templates: List<GithubPullRequestTemplate> = emptyList(), val loadingTemplates: Boolean = false,
    val templatesLoaded: Boolean = false, val templatesError: Boolean = false,
    val viewerAccountId: Long? = null, val accountReady: Boolean = true
) {
    fun validated() = GithubCreatePullRequestDraft.fromInput(title, body, base, head, draft, maintainerCanModify, headRepository)
}

class CreatePullRequestViewModel(
    private val loadBranchesPage: suspend (Int) -> Result<GithubPage<GithubBranch>>,
    private val create: suspend (GithubCreatePullRequestDraft) -> Result<GithubPullRequest>,
    private val saved: SavedStateHandle,
    private val compare: suspend (String, String, String?) -> Result<GithubBranchComparison> = { _, _, _ -> Result.failure(UnsupportedOperationException()) },
    private val readTemplates: suspend () -> Result<List<GithubPullRequestTemplate>> = { Result.success(emptyList()) },
    sessions: Flow<GithubSession>? = null,
    private val expectedAccountId: Long? = null
) : ViewModel() {
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val sessionSource = sessions
    private val sessionsEnabled = sessions != null
    private var activeAccountId: Long? = null
    private var sessionInitialized = false
    private var requestGeneration = 0L
    private fun savedKey(field: String, accountId: Long? = activeAccountId) =
        if (sessionsEnabled) "create_pr:$accountId:$field" else field

    private fun readDraft(accountId: Long? = activeAccountId) = CreatePullRequestState(
        title = saved[savedKey("title", accountId)] ?: "", body = saved[savedKey("body", accountId)] ?: "",
        base = saved[savedKey("base", accountId)] ?: "", head = saved[savedKey("head", accountId)] ?: "",
        headRepository = saved[savedKey("headRepository", accountId)] ?: "",
        draft = saved[savedKey("draft", accountId)] ?: false,
        maintainerCanModify = saved[savedKey("maintainerCanModify", accountId)] ?: false,
        viewerAccountId = accountId, accountReady = !sessionsEnabled || accountId != null)

    private val mutable = MutableStateFlow(
        if (sessionsEnabled) CreatePullRequestState(accountReady = false) else readDraft())
    val state = mutable.asStateFlow()
    private var submitJob: Job? = null
    private var branchesJob: Job? = null
    private var compareJob: Job? = null
    private var comparisonRevision = 0L
    private var templatesJob: Job? = null

    init {
        if (sessions == null) loadBranches()
        else viewModelScope.launch { sessions.collect { onSessionChanged(it) } }
    }

    private fun onSessionChanged(session: GithubSession) {
        val incoming = (session as? GithubSession.SignedIn)?.account?.id
        val next = incoming?.takeIf { expectedAccountId == null || it == expectedAccountId }
        if (sessionInitialized && activeAccountId == next) return
        sessionInitialized = true
        cancel()
        activeAccountId = next
        mutable.value = if (next == null) CreatePullRequestState(accountReady = false) else readDraft(next)
        if (next != null) loadBranches()
    }

    private fun acceptsRequests(): Boolean {
        if (!sessionsEnabled) return true
        if (!mutable.value.accountReady) return false
        // Auth can change before the main-thread collector is scheduled; never start a write in that gap.
        val currentSession = (sessionSource as? StateFlow<GithubSession>)?.value
        return currentSession == null || (currentSession as? GithubSession.SignedIn)?.account?.id == activeAccountId
    }
    private fun isCurrent(generation: Long) = generation == requestGeneration && acceptsRequests()

    /** Re-entering a retained destination resumes a cancelled first page without restoring writes. */
    fun onEntered() {
        if (acceptsRequests() && mutable.value.branches.isEmpty()) loadBranches()
    }

    fun loadTemplates() {
        if (!acceptsRequests() || mutable.value.loadingTemplates) return
        mutable.update { it.copy(loadingTemplates = true, templatesError = false) }
        val generation = requestGeneration
        templatesJob = requests.launch {
            if (!isActive || !isCurrent(generation)) return@launch
            val result = readTemplates()
            if (!isActive || !isCurrent(generation)) return@launch
            result.fold(onSuccess = { values -> mutable.update { it.copy(templates = values, templatesLoaded = true, loadingTemplates = false) } },
                onFailure = { mutable.update { it.copy(templatesError = true, loadingTemplates = false) } })
        }
    }

    fun applyTemplate(id: String, expectedBody: String) {
        val current = mutable.value
        if (!acceptsRequests() || current.submitting || current.body != expectedBody) return
        val body = current.templates.firstOrNull { it.id == id }?.body ?: return
        edit(current.copy(body = body))
    }

    fun edit(value: CreatePullRequestState) {
        if (sessionsEnabled && value.viewerAccountId != activeAccountId) return
        if (!acceptsRequests() || mutable.value.submitting || value.title.length > 256 || value.body.length > 65536) return
        val refsChanged = value.base != mutable.value.base || value.head != mutable.value.head || value.headRepository != mutable.value.headRepository
        if (refsChanged) { compareJob?.cancel(); comparisonRevision++ }
        mutable.update { it.copy(title = value.title, body = value.body, base = value.base, head = value.head,
            comparison = if (refsChanged) null else it.comparison,
            comparing = if (refsChanged) false else it.comparing, compareError = if (refsChanged) false else it.compareError,
            headRepository = value.headRepository, draft = value.draft, maintainerCanModify = value.maintainerCanModify, failed = false) }
        save()
    }

    fun preview() {
        val current = mutable.value
        if (!acceptsRequests() || current.comparing || current.submitting) return
        val refs = GithubCreatePullRequestDraft.fromInput("Compare", "", current.base, current.head, headRepository = current.headRepository).getOrNull() ?: return
        val revision = ++comparisonRevision
        mutable.update { it.copy(comparing = true, compareError = false, comparison = null) }
        val generation = requestGeneration
        compareJob = requests.launch {
            try {
                if (!isActive || !isCurrent(generation)) return@launch
                val result = compare(refs.base, refs.head, refs.headRepository)
                if (!isActive || !isCurrent(generation) || revision != comparisonRevision) return@launch
                result.fold(onSuccess = { value -> mutable.update { it.copy(comparison = value) } },
                    onFailure = { mutable.update { it.copy(compareError = true) } })
            } finally {
                if (revision == comparisonRevision) mutable.update { it.copy(comparing = false) }
            }
        }
    }

    private fun save() {
        if (!acceptsRequests()) return
        val value = mutable.value
        saved[savedKey("title")] = value.title; saved[savedKey("body")] = value.body
        saved[savedKey("base")] = value.base; saved[savedKey("head")] = value.head
        saved[savedKey("headRepository")] = value.headRepository
        saved[savedKey("draft")] = value.draft; saved[savedKey("maintainerCanModify")] = value.maintainerCanModify
    }

    fun loadBranches() {
        val current = mutable.value
        val page = current.branchPage ?: return
        if (!acceptsRequests() || current.loadingBranches) return
        mutable.update { it.copy(loadingBranches = true, branchesError = false) }
        val generation = requestGeneration
        branchesJob = requests.launch {
            if (!isActive || !isCurrent(generation)) return@launch
            val result = loadBranchesPage(page)
            if (!isActive || !isCurrent(generation)) return@launch
            result.fold(onSuccess = { loaded -> mutable.update {
                it.copy(branches = (it.branches + loaded.items.map(GithubBranch::name)).distinct(),
                    branchPage = loaded.nextPage?.takeIf { next -> next > page }, loadingBranches = false)
            } }, onFailure = { mutable.update { it.copy(loadingBranches = false, branchesError = true) } })
        }
    }

    fun submit() {
        val current = mutable.value
        if (!acceptsRequests() || current.submitting) return
        val draft = current.validated().getOrNull() ?: return
        compareJob?.cancel(); comparisonRevision++
        mutable.update { it.copy(submitting = true, failed = false, comparing = false) }
        val generation = requestGeneration
        submitJob = requests.launch {
            if (!isActive || !isCurrent(generation)) return@launch
            val result = create(draft)
            if (!isActive || !isCurrent(generation)) return@launch
            result.fold(onSuccess = { created ->
                mutable.update { CreatePullRequestState(branches = it.branches, branchPage = it.branchPage, created = created,
                    viewerAccountId = it.viewerAccountId, accountReady = it.accountReady) }
                save()
            }, onFailure = { mutable.update { it.copy(submitting = false, failed = true) } })
        }
    }

    fun consumeCreated() { mutable.update { it.copy(created = null) } }

    /** Consume only a result owned by the account currently driving the destination. */
    fun consumeCreatedForAccount(accountId: Long?): GithubPullRequest? {
        if (sessionsEnabled && (!acceptsRequests() || accountId == null || accountId != activeAccountId)) return null
        val created = mutable.value.created ?: return null
        consumeCreated()
        return created
    }
    fun cancel() {
        requestGeneration++
        requests.coroutineContext.cancelChildren()
        submitJob?.cancel(); branchesJob?.cancel(); compareJob?.cancel(); templatesJob?.cancel(); comparisonRevision++
        mutable.update { it.copy(submitting = false, loadingBranches = false, comparing = false, loadingTemplates = false, created = null) }
    }
}
