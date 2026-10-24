package takagi.ru.monica.github.feature.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.*

data class DiscussionsUiState(
    val items: List<GithubDiscussion> = emptyList(),
    val categories: List<GithubDiscussionCategory> = emptyList(),
    val repositoryId: String? = null,
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadError: Boolean = false,
    val categoriesError: Boolean = false,
    val submitting: Boolean = false,
    val submitError: Boolean = false,
    val title: String = "",
    val body: String = "",
    val categoryId: String? = null,
    val composing: Boolean = false,
    val created: GithubDiscussion? = null
)

class DiscussionsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubDiscussionsRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val accountId: Long? = null,
    private val sessions: Flow<GithubSession>? = null
) : ViewModel() {
    private val draftScope = "$accountId:$owner/$name"
    private val draftKey = "discussion.draft:$draftScope"
    private val composingKey = "discussion.composing:$draftScope"
    private var requestsAllowed = sessions == null
    private var writeAllowed = sessions == null
    private var sessionObserved = false
    private var generation = 0L
    private val mutable = MutableStateFlow(if (requestsAllowed) restoreDraft() else DiscussionsUiState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var categoriesJob: Job? = null
    private var createJob: Job? = null

    private fun synchronizeSession(): Boolean {
        (sessions as? StateFlow<GithubSession>)?.value?.let(::onSessionChanged)
        return requestsAllowed
    }

    fun cancelPendingRequests(clearAccountData: Boolean = true) {
        synchronizeSession()
        cancelJobs(clearAccountData)
    }

    private fun cancelJobs(clearAccountData: Boolean) {
        generation++
        loadJob?.cancel(); categoriesJob?.cancel(); createJob?.cancel()
        if (clearAccountData) {
            // Draft edits are persisted immediately; clearing fetched data must not overwrite them.
            mutable.value = DiscussionsUiState()
        }
        else mutable.update { it.copy(loading = false, submitting = false, created = null) }
    }

    init {
        if (sessions == null) { load(); loadCategories() }
        else viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.collect(::onSessionChanged)
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val currentSession = (sessions as? StateFlow<GithubSession>)?.value ?: session
        val signedInId = (currentSession as? GithubSession.SignedIn)?.account?.id
        val allowed = if (accountId == null) currentSession is GithubSession.SignedOut
            else signedInId == accountId
        val canWrite = allowed && signedInId != null
        if (sessionObserved && requestsAllowed == allowed && writeAllowed == canWrite) return
        sessionObserved = true
        cancelJobs(clearAccountData = true)
        requestsAllowed = allowed
        writeAllowed = canWrite
        if (allowed) {
            mutable.value = restoreDraft()
            load()
            loadCategories()
        }
    }

    fun restorePendingDraft() {
        if (!synchronizeSession() || mutable.value.submitting) return
        val draft = restoreDraft()
        mutable.update { it.copy(title = draft.title, body = draft.body,
            categoryId = draft.categoryId, composing = draft.composing) }
    }

    private fun restoreDraft(): DiscussionsUiState {
        // Migrate only a legacy draft whose owner matches this exact account and repository.
        if (!savedState.contains(draftKey) && savedState.get<String>("discussion.scope") == draftScope) {
            savedState[draftKey] = savedState.get<ArrayList<String>>("discussion.draft") ?: arrayListOf<String>()
            savedState[composingKey] = savedState.get<Boolean>("discussion.composing") ?: false
            savedState.remove<Any>("discussion.scope")
            savedState.remove<Any>("discussion.draft")
            savedState.remove<Any>("discussion.composing")
        }
        val draft = savedState.get<ArrayList<String>>(draftKey).orEmpty()
        return DiscussionsUiState(
            title = draft.getOrNull(0).orEmpty().take(256),
            body = draft.getOrNull(1).orEmpty().take(65536),
            categoryId = draft.getOrNull(2)?.takeIf(String::isNotEmpty),
            composing = writeAllowed && (savedState[composingKey] ?: false)
        )
    }

    private fun saveDraft() {
        val current = mutable.value
        savedState[draftKey] = arrayListOf(current.title, current.body, current.categoryId.orEmpty())
        savedState[composingKey] = current.composing
    }

    fun setComposing(value: Boolean) {
        if (!synchronizeSession() || !writeAllowed || mutable.value.submitting) return
        mutable.update { it.copy(composing = value) }
        saveDraft()
    }

    fun load(more: Boolean = false) {
        if (!synchronizeSession()) return
        val version = generation
        val current = mutable.value
        if (current.loading || (more && current.nextCursor == null)) return
        val cursor = if (more) current.nextCursor else null
        mutable.update { it.copy(loading = true, loadError = false) }
        loadJob = viewModelScope.launch {
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            val result = repository.list(owner, name, cursor)
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            result.fold(onSuccess = { page ->
                mutable.update { it.copy(loading = false, repositoryId = page.repositoryId,
                    items = (if (more) it.items + page.items else page.items).distinctBy(GithubDiscussion::id),
                    nextCursor = page.nextCursor?.takeUnless { next -> next == cursor }) }
            }, onFailure = { mutable.update { it.copy(loading = false, loadError = true) } })
        }
    }

    fun loadCategories() {
        if (!synchronizeSession()) return
        val version = generation
        if (categoriesJob?.isActive == true) return
        categoriesJob = viewModelScope.launch {
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            val result = repository.categories(owner, name)
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            result.fold(onSuccess = { values -> mutable.update { it.copy(categories = values, categoriesError = false) } },
                onFailure = { mutable.update { it.copy(categoriesError = true) } })
        }
    }

    fun edit(title: String, body: String, categoryId: String?) {
        if (!synchronizeSession() || !writeAllowed || mutable.value.submitting || title.length > 256 || body.length > 65536) return
        mutable.update { it.copy(title = title, body = body, categoryId = categoryId, submitError = false) }
        saveDraft()
    }

    fun create() {
        if (!synchronizeSession() || !writeAllowed) return
        val version = generation
        val current = mutable.value
        if (current.submitting) return
        val repositoryId = current.repositoryId ?: return
        val category = current.categories.firstOrNull { it.id == current.categoryId }
        if (category == null || current.title.isBlank() || current.body.isBlank()) {
            mutable.update { it.copy(submitError = true) }; return
        }
        mutable.update { it.copy(submitting = true, submitError = false) }
        createJob = viewModelScope.launch {
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            val result = repository.create(repositoryId, category.id, current.title, current.body)
            if (!synchronizeSession() || !isActive || version != generation) return@launch
            result.fold(onSuccess = { created -> mutable.update { it.copy(submitting = false,
                items = (listOf(created) + it.items).distinctBy(GithubDiscussion::id), created = created,
                title = "", body = "", categoryId = null, composing = false) }; saveDraft() },
                onFailure = { mutable.update { it.copy(submitting = false, submitError = true) } })
        }
    }

    fun consumeCreated() {
        if (!synchronizeSession()) return
        mutable.update { it.copy(created = null) }
    }
}
