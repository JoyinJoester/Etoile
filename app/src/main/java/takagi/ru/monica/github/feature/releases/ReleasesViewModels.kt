package takagi.ru.monica.github.feature.releases

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
    val mutationOutcome: ReleaseMutationOutcome? = null
) {
    val fullName: String get() = "$owner/$name"
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isRefreshing && !isLoadingMore
    val isMutating: Boolean get() = pendingMutation != null
}

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
        val isPrerelease: Boolean
    ) : ReleasesAction

    data class Delete(val releaseId: Long) : ReleasesAction

    data class Attach(
        val releaseId: Long,
        val fileName: String,
        val label: String,
        val contentType: String?,
        val contentLength: Long,
        val open: () -> GithubAssetInput
    ) : ReleasesAction
}

class ReleasesViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubReleasesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReleasesUiState(owner = owner, name = name))
    val state: StateFlow<ReleasesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var failedReset = true
    private var mutationJob: Job? = null

    init {
        load(reset = true)
    }

    fun onAction(action: ReleasesAction) {
        when (action) {
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
                            }
                        )
                    },
                    onFailure = { reject(kind) }
                )
            }
            is ReleasesAction.Delete -> submit(ReleaseMutation.Delete(action.releaseId))
            is ReleasesAction.Attach -> if (!isMutating) {
                GithubReleaseAssetUpload.fromFile(
                    fileName = action.fileName,
                    label = action.label,
                    contentType = action.contentType,
                    contentLength = action.contentLength,
                    open = action.open
                ).fold(
                    onSuccess = { asset -> submit(ReleaseMutation.Attach(action.releaseId, asset)) },
                    onFailure = { reject(ReleaseMutationKind.Attach) }
                )
            }
        }
    }

    private val isMutating: Boolean get() = _state.value.pendingMutation != null

    private fun reject(kind: ReleaseMutationKind) {
        _state.update {
            it.copy(
                mutationOutcome = ReleaseMutationOutcome.Failed(kind, ReleaseMutationFailure.InvalidInput)
            )
        }
    }

    private fun submit(mutation: ReleaseMutation) {
        if (_state.value.pendingMutation != null) return
        _state.update { it.copy(pendingMutation = mutation, mutationOutcome = null) }
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val result = when (mutation) {
                is ReleaseMutation.Create ->
                    repository.createRelease(owner, name, mutation.draft).map { it.id }
                is ReleaseMutation.Edit ->
                    repository.updateRelease(owner, name, mutation.releaseId, mutation.draft).map { it.id }
                is ReleaseMutation.Delete ->
                    repository.deleteRelease(owner, name, mutation.releaseId)
                is ReleaseMutation.Attach ->
                    repository.uploadAsset(owner, name, mutation.releaseId, mutation.asset).map { it.id }
            }
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            pendingMutation = null,
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
        loadJob = viewModelScope.launch {
            val result = if (forceRefresh) {
                repository.refreshReleases(owner, name, requestedPage)
            } else {
                repository.releases(owner, name, requestedPage)
            }
            result.fold(
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
        private val repository: GithubReleasesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleasesViewModel::class.java))
            return ReleasesViewModel(owner, name, repository) as T
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
    val assetRemovalFailed: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
    val isRemovingAsset: Boolean get() = removingAssetId != null
}

sealed interface ReleaseDetailAction {
    data object Retry : ReleaseDetailAction
    data class RemoveAsset(val assetId: Long) : ReleaseDetailAction
}

class ReleaseDetailViewModel(
    private val owner: String,
    private val name: String,
    private val reference: ReleaseReference,
    private val repository: GithubReleasesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReleaseDetailUiState(owner, name, reference))
    val state: StateFlow<ReleaseDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var removeJob: Job? = null

    init {
        load()
    }

    fun onAction(action: ReleaseDetailAction) {
        when (action) {
            ReleaseDetailAction.Retry -> load()
            is ReleaseDetailAction.RemoveAsset -> removeAsset(action.assetId)
        }
    }

    private fun removeAsset(assetId: Long) {
        if (_state.value.removingAssetId != null) return
        _state.update { it.copy(removingAssetId = assetId, assetRemovalFailed = false) }
        removeJob?.cancel()
        removeJob = viewModelScope.launch {
            repository.deleteAsset(owner, name, assetId).fold(
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
        loadJob = viewModelScope.launch {
            val result = when (val currentReference = reference) {
                is ReleaseReference.Id -> repository.release(owner, name, currentReference.value)
                is ReleaseReference.Tag -> repository.releaseByTag(owner, name, currentReference.value)
            }
            result.fold(
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
        private val repository: GithubReleasesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReleaseDetailViewModel::class.java))
            return ReleaseDetailViewModel(owner, name, reference, repository) as T
        }
    }
}
