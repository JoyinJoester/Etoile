package takagi.ru.monica.github.feature.releases

import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.data.InMemoryGithubActionJournal
import takagi.ru.monica.github.data.githubRunCatching
import takagi.ru.monica.github.data.runAction
import kotlinx.coroutines.flow.catch
import takagi.ru.monica.github.feature.repository.currentRepositoryResult
import takagi.ru.monica.github.feature.repository.matchesGithubAccount
import takagi.ru.monica.github.domain.GithubSession
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.data.GithubSignedOutException
import takagi.ru.monica.github.domain.GithubAssetInput
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAssetUpload
import takagi.ru.monica.github.domain.GithubReleaseDraft
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.mergeItems

@Immutable
data class ReleasesUiState(
    val owner: String,
    val name: String,
    val items: List<GithubRelease> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val pendingMutation: ReleaseMutation? = null,
    val mutationOutcome: ReleaseMutationOutcome? = null,
    val viewerAccountId: Long? = null,
    val canManage: Boolean = false,
    val editor: ReleaseEditorDraft? = null,
    val journalReady: Boolean = false,
    val journalUnavailable: Boolean = false,
    val unconfirmedOperations: Set<GithubActionOperation> = emptySet()
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
    val isMutating: Boolean get() = pendingMutation != null
}

@Immutable
@Serializable
data class ReleaseEditorDraft(
    val accountId: Long?,
    val releaseId: Long? = null,
    val tagName: String = "",
    val title: String = "",
    val body: String = "",
    val targetCommitish: String = "",
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false
)

sealed interface ReleaseMutation {
    val kind: ReleaseMutationKind

    data class Create(val draft: GithubReleaseDraft) : ReleaseMutation {
        override val kind: ReleaseMutationKind get() = ReleaseMutationKind.Create
    }

    data class Edit(val releaseId: Long, val draft: GithubReleaseDraft) : ReleaseMutation {
        override val kind: ReleaseMutationKind get() = ReleaseMutationKind.Edit
    }

    data class Delete(val releaseId: Long) : ReleaseMutation {
        override val kind: ReleaseMutationKind get() = ReleaseMutationKind.Delete
    }

    data class Attach(val releaseId: Long, val asset: GithubReleaseAssetUpload) : ReleaseMutation {
        override val kind: ReleaseMutationKind get() = ReleaseMutationKind.Attach
    }
}

/** Creation is scoped to the repository; edits and deletion share one target to avoid conflicting retries. */
internal fun ReleaseMutation.operation() = when (this) {
    is ReleaseMutation.Create -> GithubActionOperation(GithubActionKind.RELEASE_CREATE, 1L)
    is ReleaseMutation.Edit -> GithubActionOperation(GithubActionKind.RELEASE_CHANGE, releaseId)
    is ReleaseMutation.Delete -> GithubActionOperation(GithubActionKind.RELEASE_CHANGE, releaseId)
    is ReleaseMutation.Attach -> GithubActionOperation(GithubActionKind.RELEASE_UPLOAD, releaseId)
}

internal val releaseJournalKinds = setOf(GithubActionKind.RELEASE_CREATE, GithubActionKind.RELEASE_CHANGE,
    GithubActionKind.RELEASE_UPLOAD, GithubActionKind.RELEASE_ASSET_DELETE)

enum class ReleaseMutationKind { Create, Edit, Delete, Attach }

enum class ReleaseMutationFailure {
    InvalidInput,
    Conflict,
    Forbidden,
    NotFound,
    RateLimited,
    Network
}

sealed interface ReleaseMutationOutcome {
    data class Succeeded(val kind: ReleaseMutationKind) : ReleaseMutationOutcome
    data class Failed(val kind: ReleaseMutationKind, val failure: ReleaseMutationFailure) : ReleaseMutationOutcome
}

sealed interface ReleasesAction {
    data class OpenEditor(val releaseId: Long? = null, val asDraft: Boolean? = null) : ReleasesAction
    data class EditEditor(val draft: ReleaseEditorDraft) : ReleasesAction
    data object CloseEditor : ReleasesAction
    data object Retry : ReleasesAction
    data object Refresh : ReleasesAction
    data object LoadMore : ReleasesAction
    data object DismissMutation : ReleasesAction
    data class Save(
        /** Null creates a release; otherwise this identifies the release being overwritten. */
        val releaseId: Long?,
        val tagName: String,
        val title: String,
        val body: String,
        val targetCommitish: String,
        val isDraft: Boolean,
        val isPrerelease: Boolean,
        val expectedAccountId: Long? = null,
        val repeatConfirmed: Boolean = false
    ) : ReleasesAction

    data class Delete(val releaseId: Long, val expectedAccountId: Long? = null, val repeatConfirmed: Boolean = false) : ReleasesAction
    data class Acknowledge(val operation: GithubActionOperation, val expectedAccountId: Long? = null) : ReleasesAction

    data class Attach(
        val releaseId: Long,
        val fileName: String,
        val label: String,
        val contentType: String?,
        val contentLength: Long,
        val open: () -> GithubAssetInput,
        val expectedAccountId: Long? = null,
        val repeatConfirmed: Boolean = false
    ) : ReleasesAction
}

class ReleasesViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubReleasesRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
    private val detailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val journal: GithubActionJournal = InMemoryGithubActionJournal()
) : ViewModel() {
    private val sessionSource = sessions
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private val sessionsEnabled = sessions != null
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(ReleasesUiState(owner = owner, name = name))
    val state: StateFlow<ReleasesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true
    private var mutationJob: Job? = null

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
                if (isCurrent(id)) _state.update { it.copy(journalReady = true, journalUnavailable = false,
                    unconfirmedOperations = pending.filter { it.kind in releaseJournalKinds }.toSet()) }
            }
        }
    }

    private fun acknowledge(operation: GithubActionOperation, expectedAccountId: Long?) {
        if (!isCurrent(expectedAccountId) || !_state.value.journalReady || operation.kind !in releaseJournalKinds) return
        val id = accountId
        requests.launch {
            if (!isCurrent(id)) return@launch
            githubRunCatching { journal.acknowledge(scope(id), operation) }.onFailure {
                if (isCurrent(id)) _state.update { it.copy(journalReady = false, journalUnavailable = true) }
            }
        }
    }


    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        if (sessions == null) observeJournal()
        load(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = ReleasesUiState(owner, name, viewerAccountId = account?.id)
        observeJournal()
        if (account != null) requests.launch {
            val role = detailsRepository?.details(owner, name)?.currentRepositoryResult()?.getOrNull()?.viewerRole
            _state.update { it.copy(canManage = role?.canPush == true) }
        }
        load(reset = true)
    }

    fun onAction(action: ReleasesAction) {
        if ((action == ReleasesAction.Refresh || action == ReleasesAction.Retry) && !_state.value.journalReady) observeJournal()
        val writes = action is ReleasesAction.Save || action is ReleasesAction.Delete || action is ReleasesAction.Attach
        val expectedAccountId = when (action) {
            is ReleasesAction.Save -> action.expectedAccountId
            is ReleasesAction.Delete -> action.expectedAccountId
            is ReleasesAction.Attach -> action.expectedAccountId
            else -> accountId
        }
        if (writes && !sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return
        if (sessionsEnabled && !_state.value.canManage &&
            writes) return
        when (action) {
            is ReleasesAction.Acknowledge -> acknowledge(action.operation, action.expectedAccountId)
            is ReleasesAction.OpenEditor -> openEditor(action.releaseId, action.asDraft)
            is ReleasesAction.EditEditor -> {
                val current = _state.value.editor
                if (!isMutating && current != null && action.draft.accountId == accountId &&
                    current.accountId == accountId && action.draft.releaseId == current.releaseId) {
                    savedState[editorKey(current.releaseId)] = Json.encodeToString(action.draft)
                    _state.update { it.copy(editor = action.draft) }
                }
            }
            ReleasesAction.CloseEditor -> if (!isMutating) _state.update { it.copy(editor = null, mutationOutcome = null) }
            ReleasesAction.Retry -> load(
                reset = failedReset,
                preserveExisting = failedReset && _state.value.items.isNotEmpty(),
                forceRefresh = failedReset
            )
            ReleasesAction.Refresh -> load(
                reset = true,
                preserveExisting = _state.value.items.isNotEmpty(),
                forceRefresh = true
            )
            ReleasesAction.LoadMore -> load(reset = false)
            ReleasesAction.DismissMutation -> _state.update { it.copy(mutationOutcome = null) }
            is ReleasesAction.Save -> if (!isMutating) {
                val kind = if (action.releaseId == null) {
                    ReleaseMutationKind.Create
                } else {
                    ReleaseMutationKind.Edit
                }
                GithubReleaseDraft.fromInput(
                    tagName = action.tagName,
                    title = action.title,
                    body = action.body,
                    targetCommitish = action.targetCommitish,
                    isDraft = action.isDraft,
                    isPrerelease = action.isPrerelease
                ).fold(
                    onSuccess = { draft ->
                        val releaseId = action.releaseId
                        submit(
                            if (releaseId == null) {
                                ReleaseMutation.Create(draft)
                            } else {
                                ReleaseMutation.Edit(releaseId, draft)
                            }, action.repeatConfirmed
                        )
                    },
                    onFailure = { reject(kind) }
                )
            }
            is ReleasesAction.Delete -> submit(ReleaseMutation.Delete(action.releaseId), action.repeatConfirmed)
            is ReleasesAction.Attach -> if (!isMutating) {
                GithubReleaseAssetUpload.fromFile(
                    fileName = action.fileName,
                    label = action.label,
                    contentType = action.contentType,
                    contentLength = action.contentLength,
                    open = action.open
                ).fold(
                    onSuccess = { asset -> submit(ReleaseMutation.Attach(action.releaseId, asset), action.repeatConfirmed) },
                    onFailure = { reject(ReleaseMutationKind.Attach) }
                )
            }
        }
    }

    private fun editorKey(releaseId: Long?) = "releaseEditor:$owner/$name:$accountId:${releaseId ?: 0L}"

    private fun openEditor(releaseId: Long?, asDraft: Boolean?) {
        if (isMutating || (sessionsEnabled && !_state.value.canManage)) return
        val release = releaseId?.let { id -> _state.value.items.firstOrNull { it.id == id } ?: return }
        val restored = savedState.get<String>(editorKey(releaseId))?.let { text ->
            runCatching { Json.decodeFromString<ReleaseEditorDraft>(text) }.getOrNull()
        }?.takeIf { it.accountId == accountId && it.releaseId == releaseId }
        val draft = restored ?: ReleaseEditorDraft(accountId, releaseId, release?.tagName.orEmpty(),
            release?.name.orEmpty(), release?.body.orEmpty(), release?.targetCommitish.orEmpty(),
            release?.isDraft ?: false, release?.isPrerelease ?: false)
        val opened = if (asDraft == null) draft else draft.copy(isDraft = asDraft)
        if (asDraft != null) savedState[editorKey(releaseId)] = Json.encodeToString(opened)
        _state.update { it.copy(editor = opened, mutationOutcome = null) }
    }

    private val isMutating: Boolean get() = _state.value.pendingMutation != null

    private fun reject(kind: ReleaseMutationKind) {
        _state.update {
            it.copy(
                mutationOutcome = ReleaseMutationOutcome.Failed(kind, ReleaseMutationFailure.InvalidInput)
            )
        }
    }

    private fun submit(mutation: ReleaseMutation, repeatConfirmed: Boolean) {
        val operation = mutation.operation()
        if (_state.value.pendingMutation != null || !_state.value.journalReady || operation.targetId <= 0) return
        if (operation in _state.value.unconfirmedOperations && !repeatConfirmed) return
        val expectedAccountId = accountId
        val targetRepository = if (sessionSource == null) repository else repository.forAccount(expectedAccountId)
        _state.update { it.copy(pendingMutation = mutation, mutationOutcome = null) }
        mutationJob?.cancel()
        mutationJob = requests.launch {
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            val result = journal.runAction(scope(expectedAccountId), operation, repeatConfirmed, { isCurrent(expectedAccountId) }) {
                when (mutation) {
                is ReleaseMutation.Create ->
                    targetRepository.createRelease(owner, name, mutation.draft).map { it.id }
                is ReleaseMutation.Edit ->
                    targetRepository.updateRelease(owner, name, mutation.releaseId, mutation.draft).map { it.id }
                is ReleaseMutation.Delete ->
                    targetRepository.deleteRelease(owner, name, mutation.releaseId)
                is ReleaseMutation.Attach ->
                    targetRepository.uploadAsset(owner, name, mutation.releaseId, mutation.asset).map { it.id }
                }
            }
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            result.currentRepositoryResult().fold(
                onSuccess = {
                    val editedId = when (mutation) {
                        is ReleaseMutation.Create -> null
                        is ReleaseMutation.Edit -> mutation.releaseId
                        else -> null
                    }
                    val savedEditor = mutation is ReleaseMutation.Create || mutation is ReleaseMutation.Edit
                    if (savedEditor) savedState.remove<String>(editorKey(editedId))
                    _state.update {
                        it.copy(
                            pendingMutation = null,
                            editor = if (savedEditor) null else it.editor,
                            mutationOutcome = ReleaseMutationOutcome.Succeeded(mutation.kind)
                        )
                    }
                    load(reset = true, preserveExisting = true, forceRefresh = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            pendingMutation = null,
                            mutationOutcome = ReleaseMutationOutcome.Failed(mutation.kind, classify(error))
                        )
                    }
                }
            )
        }
    }

    private fun classify(error: Throwable): ReleaseMutationFailure {
        if (error is GithubSignedOutException) return ReleaseMutationFailure.Forbidden
        val apiError = error as? GithubApiException ?: return ReleaseMutationFailure.Network
        if (apiError.rateLimited || apiError.statusCode == 429) return ReleaseMutationFailure.RateLimited
        return when (apiError.statusCode) {
            401, 403 -> ReleaseMutationFailure.Forbidden
            404 -> ReleaseMutationFailure.NotFound
            409 -> ReleaseMutationFailure.Conflict
            422 -> ReleaseMutationFailure.InvalidInput
            else -> ReleaseMutationFailure.Network
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
                isLoading = reset && !preserveExisting,
                isRefreshing = reset && preserveExisting,
                isLoadingMore = !reset,
                error = false,
                items = if (reset && !preserveExisting) emptyList() else it.items
            )
        }
        loadJob = requests.launch {
            val result = if (forceRefresh) {
                repository.refreshReleases(owner, name, requestedPage)
            } else {
                repository.releases(owner, name, requestedPage)
            }
            result.currentRepositoryResult().fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            items = page.mergeItems(state.items, reset, GithubRelease::id),
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
        private val repository: GithubReleasesRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
        private val detailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null,
        private val journal: GithubActionJournal = InMemoryGithubActionJournal()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ReleasesViewModel::class.java))
            return ReleasesViewModel(owner, name, repository, sessions, detailsRepository, extras.createSavedStateHandle(), journal) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleasesViewModel::class.java))
            return ReleasesViewModel(owner, name, repository, sessions, detailsRepository, journal = journal) as T
        }
    }
}

@Immutable
sealed interface ReleaseReference {
    data class Id(val value: Long) : ReleaseReference
    data class Tag(val value: String) : ReleaseReference
}

@Immutable
data class ReleaseDetailUiState(
    val owner: String,
    val name: String,
    val reference: ReleaseReference,
    val release: GithubRelease? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val removingAssetId: Long? = null,
    val assetRemovalFailed: Boolean = false,
    val viewerAccountId: Long? = null,
    val canManage: Boolean = false,
    val journalReady: Boolean = false,
    val journalUnavailable: Boolean = false,
    val unconfirmedOperations: Set<GithubActionOperation> = emptySet()
) {
    val fullName: String get() = "$owner/$name"
    val isRemovingAsset: Boolean get() = removingAssetId != null
}

sealed interface ReleaseDetailAction {
    data object Retry : ReleaseDetailAction
    data class RemoveAsset(val assetId: Long, val expectedAccountId: Long? = null, val repeatConfirmed: Boolean = false) : ReleaseDetailAction
    data class Acknowledge(val operation: GithubActionOperation, val expectedAccountId: Long? = null) : ReleaseDetailAction
}

class ReleaseDetailViewModel(
    private val owner: String,
    private val name: String,
    private val reference: ReleaseReference,
    private val repository: GithubReleasesRepository,
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
    private val detailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null,
    private val journal: GithubActionJournal = InMemoryGithubActionJournal()
) : ViewModel() {
    private val sessionSource = sessions
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[kotlinx.coroutines.Job]))
    private val sessionsEnabled = sessions != null
    private var accountId: Long? = null
    private var sessionInitialized = false

    private val _state = MutableStateFlow(ReleaseDetailUiState(owner, name, reference))
    val state: StateFlow<ReleaseDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var removeJob: Job? = null

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
                if (isCurrent(id)) _state.update { it.copy(journalReady = true, journalUnavailable = false,
                    unconfirmedOperations = pending.filter { it.kind in releaseJournalKinds }.toSet()) }
            }
        }
    }

    private fun acknowledge(operation: GithubActionOperation, expectedAccountId: Long?) {
        if (!isCurrent(expectedAccountId) || !_state.value.journalReady || operation.kind !in releaseJournalKinds) return
        val id = accountId
        requests.launch {
            if (!isCurrent(id)) return@launch
            githubRunCatching { journal.acknowledge(scope(id), operation) }.onFailure {
                if (isCurrent(id)) _state.update { it.copy(journalReady = false, journalUnavailable = true) }
            }
        }
    }


    init { sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } } }

    init {
        if (sessions == null) observeJournal()
        load()
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && accountId == account?.id) return
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        _state.value = ReleaseDetailUiState(owner, name, reference, viewerAccountId = account?.id)
        observeJournal()
        if (account != null) requests.launch {
            val role = detailsRepository?.details(owner, name)?.currentRepositoryResult()?.getOrNull()?.viewerRole
            _state.update { it.copy(canManage = role?.canPush == true) }
        }
        load()
    }

    fun onAction(action: ReleaseDetailAction) {
        when (action) {
            ReleaseDetailAction.Retry -> { if (!_state.value.journalReady) observeJournal(); load() }
            is ReleaseDetailAction.Acknowledge -> acknowledge(action.operation, action.expectedAccountId)
            is ReleaseDetailAction.RemoveAsset -> removeAsset(action.assetId, action.expectedAccountId, action.repeatConfirmed)
        }
    }

    private fun removeAsset(assetId: Long, expectedAccountId: Long?, repeatConfirmed: Boolean) {
        val operation = GithubActionOperation(GithubActionKind.RELEASE_ASSET_DELETE, assetId)
        if (!_state.value.journalReady || assetId <= 0) return
        if (operation in _state.value.unconfirmedOperations && !repeatConfirmed) return
        if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return
        if (sessionsEnabled && !_state.value.canManage) return
        if (_state.value.removingAssetId != null) return
        val targetRepository = if (sessionSource == null) repository else repository.forAccount(expectedAccountId)
        _state.update { it.copy(removingAssetId = assetId, assetRemovalFailed = false) }
        removeJob?.cancel()
        removeJob = requests.launch {
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            val result = journal.runAction(scope(expectedAccountId), operation, repeatConfirmed, { isCurrent(expectedAccountId) }) {
                targetRepository.deleteAsset(owner, name, assetId)
            }.currentRepositoryResult()
            if (!sessionSource.matchesGithubAccount(accountId, expectedAccountId)) return@launch
            result.fold(
                onSuccess = {
                    _state.update { it.copy(removingAssetId = null, assetRemovalFailed = false) }
                    load()
                },
                onFailure = {
                    _state.update { it.copy(removingAssetId = null, assetRemovalFailed = true) }
                }
            )
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        loadJob = requests.launch {
            val result = when (val currentReference = reference) {
                is ReleaseReference.Id -> repository.release(owner, name, currentReference.value)
                is ReleaseReference.Tag -> repository.releaseByTag(owner, name, currentReference.value)
            }
            result.currentRepositoryResult().fold(
                onSuccess = { release ->
                    _state.update { it.copy(release = release, isLoading = false, error = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val reference: ReleaseReference,
        private val repository: GithubReleasesRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null,
        private val detailsRepository: takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository? = null,
        private val journal: GithubActionJournal = InMemoryGithubActionJournal()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleaseDetailViewModel::class.java))
            return ReleaseDetailViewModel(owner, name, reference, repository, sessions, detailsRepository, journal) as T
        }
    }
}
