package takagi.ru.monica.github.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
    val templatesLoaded: Boolean = false, val templatesError: Boolean = false
) {
    fun validated() = GithubCreatePullRequestDraft.fromInput(title, body, base, head, draft, maintainerCanModify, headRepository)
}

class CreatePullRequestViewModel(
    private val loadBranchesPage: suspend (Int) -> Result<GithubPage<GithubBranch>>,
    private val create: suspend (GithubCreatePullRequestDraft) -> Result<GithubPullRequest>,
    private val saved: SavedStateHandle,
    private val compare: suspend (String, String, String?) -> Result<GithubBranchComparison> = { _, _, _ -> Result.failure(UnsupportedOperationException()) },
    private val readTemplates: suspend () -> Result<List<GithubPullRequestTemplate>> = { Result.success(emptyList()) }
) : ViewModel() {
    private val mutable = MutableStateFlow(CreatePullRequestState(
        title = saved["title"] ?: "", body = saved["body"] ?: "", base = saved["base"] ?: "",
        head = saved["head"] ?: "", headRepository = saved["headRepository"] ?: "",
        draft = saved["draft"] ?: false, maintainerCanModify = saved["maintainerCanModify"] ?: false))
    val state = mutable.asStateFlow()
    private var submitJob: Job? = null
    private var branchesJob: Job? = null
    private var compareJob: Job? = null
    private var comparisonRevision = 0L
    private var templatesJob: Job? = null

    init { loadBranches() }

    fun loadTemplates() {
        if (mutable.value.loadingTemplates) return
        mutable.update { it.copy(loadingTemplates = true, templatesError = false) }
        templatesJob = viewModelScope.launch {
            val result = readTemplates()
            if (!isActive) return@launch
            result.fold(onSuccess = { values -> mutable.update { it.copy(templates = values, templatesLoaded = true, loadingTemplates = false) } },
                onFailure = { mutable.update { it.copy(templatesError = true, loadingTemplates = false) } })
        }
    }

    fun applyTemplate(id: String, expectedBody: String) {
        val current = mutable.value
        if (current.submitting || current.body != expectedBody) return
        val body = current.templates.firstOrNull { it.id == id }?.body ?: return
        edit(current.copy(body = body))
    }

    fun edit(value: CreatePullRequestState) {
        if (mutable.value.submitting || value.title.length > 256 || value.body.length > 65536) return
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
        if (current.comparing || current.submitting) return
        val refs = GithubCreatePullRequestDraft.fromInput("Compare", "", current.base, current.head, headRepository = current.headRepository).getOrNull() ?: return
        val revision = ++comparisonRevision
        mutable.update { it.copy(comparing = true, compareError = false, comparison = null) }
        compareJob = viewModelScope.launch {
            try {
                val result = compare(refs.base, refs.head, refs.headRepository)
                if (!isActive || revision != comparisonRevision) return@launch
                result.fold(onSuccess = { value -> mutable.update { it.copy(comparison = value) } },
                    onFailure = { mutable.update { it.copy(compareError = true) } })
            } finally {
                if (revision == comparisonRevision) mutable.update { it.copy(comparing = false) }
            }
        }
    }

    private fun save() {
        val value = mutable.value
        saved["title"] = value.title; saved["body"] = value.body; saved["base"] = value.base
        saved["head"] = value.head; saved["headRepository"] = value.headRepository
        saved["draft"] = value.draft; saved["maintainerCanModify"] = value.maintainerCanModify
    }

    fun loadBranches() {
        val current = mutable.value
        val page = current.branchPage ?: return
        if (current.loadingBranches) return
        mutable.update { it.copy(loadingBranches = true, branchesError = false) }
        branchesJob = viewModelScope.launch {
            val result = loadBranchesPage(page)
            if (!isActive) return@launch
            result.fold(onSuccess = { loaded -> mutable.update {
                it.copy(branches = (it.branches + loaded.items.map(GithubBranch::name)).distinct(),
                    branchPage = loaded.nextPage?.takeIf { next -> next > page }, loadingBranches = false)
            } }, onFailure = { mutable.update { it.copy(loadingBranches = false, branchesError = true) } })
        }
    }

    fun submit() {
        val current = mutable.value
        if (current.submitting) return
        val draft = current.validated().getOrNull() ?: return
        compareJob?.cancel(); comparisonRevision++
        mutable.update { it.copy(submitting = true, failed = false, comparing = false) }
        submitJob = viewModelScope.launch {
            val result = create(draft)
            if (!isActive) return@launch
            result.fold(onSuccess = { created ->
                mutable.update { CreatePullRequestState(branches = it.branches, branchPage = it.branchPage, created = created) }
                save()
            }, onFailure = { mutable.update { it.copy(submitting = false, failed = true) } })
        }
    }

    fun consumeCreated() { mutable.update { it.copy(created = null) } }
    fun cancel() {
        submitJob?.cancel(); branchesJob?.cancel(); compareJob?.cancel(); templatesJob?.cancel(); comparisonRevision++
        mutable.update { it.copy(submitting = false, loadingBranches = false, comparing = false, loadingTemplates = false) }
    }
}
