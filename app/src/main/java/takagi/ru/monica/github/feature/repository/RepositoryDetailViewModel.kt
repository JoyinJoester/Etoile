package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryFeature
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
import takagi.ru.monica.github.domain.GithubRepositoryViewerState
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubSession

@Immutable
data class RepositoryDetailUiState(
    val owner: String,
    val name: String,
    val details: GithubRepositoryDetails? = null,
    val readme: String? = null,
    val isLoadingDetails: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingReadme: Boolean = true,
    val detailsError: Boolean = false,
    val readmeError: Boolean = false,
    val viewerState: GithubRepositoryViewerState? = null,
    val isLoadingViewerState: Boolean = false,
    val viewerStateError: Boolean = false,
    val isUpdatingStar: Boolean = false,
    val starFailure: RepositoryWriteFailure? = null,
    val isUpdatingWatch: Boolean = false,
    val watchFailure: RepositoryWriteFailure? = null,
    val isForking: Boolean = false,
    val forkFailure: RepositoryWriteFailure? = null,
    val forkedRepository: GithubRepository? = null,
    val branchProtection: GithubBranchProtection? = null,
    val isLoadingBranchProtection: Boolean = false,
    val branchProtectionError: Boolean = false,
    val isUpdatingTopics: Boolean = false,
    val topicsFailure: RepositoryWriteFailure? = null,
    val isUpdatingSettings: Boolean = false,
    val settingsFailure: RepositoryWriteFailure? = null,
    val viewerLogin: String? = null
) {
    val fullName: String get() = "$owner/$name"

    val viewerRole: GithubCollaboratorRole
        get() = details?.viewerRole ?: GithubCollaboratorRole.UNKNOWN

    private val isSignedIn: Boolean get() = viewerLogin != null

    /** Starring, watching and forking work on any visible repository. */
    val canInteract: Boolean get() = isSignedIn

    /** Editing topics writes to the repository itself. */
    val canEditTopics: Boolean get() = isSignedIn && viewerRole.canPush

    /** Visibility and archiving are the GitHub "Administration" category. */
    val canManageSettings: Boolean get() = isSignedIn && viewerRole.canAdmin

    /** Collaborator and webhook management requires maintain or admin. */
    val canAdminister: Boolean get() = isSignedIn && viewerRole.canMaintain
}

sealed interface RepositoryDetailAction {
    data object Refresh : RepositoryDetailAction
    data object RetryDetails : RepositoryDetailAction
    data object RetryReadme : RepositoryDetailAction
    data object RetryViewerState : RepositoryDetailAction
    data object ToggleStar : RepositoryDetailAction
    data object ToggleWatch : RepositoryDetailAction
    data object Fork : RepositoryDetailAction
    data object RetryBranchProtection : RepositoryDetailAction
    data class UpdateTopics(val topics: List<String>) : RepositoryDetailAction
    data class SetVisibility(val isPrivate: Boolean) : RepositoryDetailAction
    data class SetArchived(val isArchived: Boolean) : RepositoryDetailAction
    data class SetFeature(val feature: GithubRepositoryFeature, val enabled: Boolean) : RepositoryDetailAction
    data class UpdateDescription(val description: String) : RepositoryDetailAction
    data class SetDefaultBranch(val branch: String) : RepositoryDetailAction
}

class RepositoryDetailViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository,
    private val actionsRepository: GithubRepositoryActionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryDetailUiState(owner = owner, name = name))
    val state: StateFlow<RepositoryDetailUiState> = _state.asStateFlow()
    private var detailsJob: Job? = null
    private var readmeJob: Job? = null
    private var branchProtectionJob: Job? = null
    private var viewerStateJob: Job? = null
    private var starJob: Job? = null
    private var watchJob: Job? = null
    private var forkJob: Job? = null
    private var topicsJob: Job? = null
    private var settingsJob: Job? = null
    private var sessionLogin: String? = null
    private var sessionAccountId: Long? = null
    private var sessionRevision = 0L

    init {
        loadDetails()
        loadReadme()
    }

    fun onAction(action: RepositoryDetailAction) {
        when (action) {
            RepositoryDetailAction.Refresh -> {
                loadDetails(refreshing = true)
                loadReadme(_state.value.details?.defaultBranch)
            }
            RepositoryDetailAction.RetryDetails -> loadDetails()
            RepositoryDetailAction.RetryReadme -> loadReadme(_state.value.details?.defaultBranch)
            RepositoryDetailAction.RetryViewerState -> loadViewerState()
            RepositoryDetailAction.ToggleStar -> toggleStar()
            RepositoryDetailAction.ToggleWatch -> toggleWatch()
            RepositoryDetailAction.Fork -> fork()
            RepositoryDetailAction.RetryBranchProtection -> loadBranchProtection()
            is RepositoryDetailAction.UpdateTopics -> updateTopics(action.topics)
            is RepositoryDetailAction.SetVisibility -> updateSettings(
                GithubRepositorySettingsEdit(isPrivate = action.isPrivate)
            )
            is RepositoryDetailAction.SetArchived -> updateSettings(
                GithubRepositorySettingsEdit(isArchived = action.isArchived)
            )
            is RepositoryDetailAction.SetFeature -> updateSettings(
                when (action.feature) {
                    GithubRepositoryFeature.Issues -> GithubRepositorySettingsEdit(hasIssues = action.enabled)
                    GithubRepositoryFeature.Wiki -> GithubRepositorySettingsEdit(hasWiki = action.enabled)
                    GithubRepositoryFeature.Projects -> GithubRepositorySettingsEdit(hasProjects = action.enabled)
                }
            )
            // Trim once here so the already-applied check and the request body see the same string.
            is RepositoryDetailAction.UpdateDescription -> updateSettings(
                GithubRepositorySettingsEdit(description = action.description.trim())
            )
            is RepositoryDetailAction.SetDefaultBranch -> updateSettings(
                GithubRepositorySettingsEdit(defaultBranch = action.branch.trim())
            )
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        val login = account?.login
        if (account?.id == sessionAccountId && login == sessionLogin) return
        sessionAccountId = account?.id
        sessionLogin = login
        sessionRevision++
        detailsJob?.cancel()
        readmeJob?.cancel()
        branchProtectionJob?.cancel()
        viewerStateJob?.cancel()
        starJob?.cancel()
        watchJob?.cancel()
        forkJob?.cancel()
        topicsJob?.cancel()
        settingsJob?.cancel()
        _state.update { current ->
            // Public content can stay visible, but permissions and private data
            // must be resolved again for the new account.
            val retainedDetails = current.details
                ?.takeUnless { it.repository.isPrivate }
                ?.copy(viewerRole = GithubCollaboratorRole.UNKNOWN)
            RepositoryDetailUiState(
                owner = owner,
                name = name,
                details = retainedDetails,
                readme = current.readme.takeIf { retainedDetails != null },
                viewerLogin = login
            )
        }
        loadDetails(refreshing = _state.value.details != null)
        loadReadme(_state.value.details?.defaultBranch)
        if (login != null) loadViewerState()
    }

    private fun loadDetails(refreshing: Boolean = false) {
        detailsJob?.cancel()
        val revision = sessionRevision
        _state.update {
            it.copy(
                isLoadingDetails = !refreshing,
                isRefreshing = refreshing,
                detailsError = false
            )
        }
        detailsJob = viewModelScope.launch {
            val result = repository.details(owner, name)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { details ->
                    _state.update {
                        it.copy(
                            details = if (sessionLogin == null) {
                                details.copy(viewerRole = GithubCollaboratorRole.UNKNOWN)
                            } else {
                                details
                            },
                            isLoadingDetails = false,
                            isRefreshing = false,
                            detailsError = false
                        )
                    }
                    loadBranchProtection(details.defaultBranch)
                },
                onFailure = {
                    _state.update { it.copy(isLoadingDetails = false, isRefreshing = false, detailsError = true) }
                }
            )
        }
    }

    private fun loadBranchProtection(branch: String? = _state.value.details?.defaultBranch) {
        val ref = branch?.takeIf(String::isNotBlank) ?: return
        branchProtectionJob?.cancel()
        val revision = sessionRevision
        _state.update { it.copy(isLoadingBranchProtection = true, branchProtectionError = false) }
        branchProtectionJob = viewModelScope.launch {
            val result = repository.branchProtection(owner, name, ref)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { protection ->
                    _state.update {
                        it.copy(
                            branchProtection = protection,
                            isLoadingBranchProtection = false,
                            branchProtectionError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingBranchProtection = false, branchProtectionError = true) }
                }
            )
        }
    }

    private fun loadReadme(ref: String? = null) {
        readmeJob?.cancel()
        val revision = sessionRevision
        _state.update { it.copy(isLoadingReadme = true, readmeError = false) }
        readmeJob = viewModelScope.launch {
            val result = repository.readme(owner, name, ref)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { readme ->
                    _state.update { it.copy(readme = readme, isLoadingReadme = false, readmeError = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingReadme = false, readmeError = true) }
                }
            )
        }
    }

    private fun loadViewerState() {
        if (sessionLogin == null) return
        viewerStateJob?.cancel()
        val revision = sessionRevision
        _state.update { it.copy(isLoadingViewerState = true, viewerStateError = false) }
        viewerStateJob = viewModelScope.launch {
            val result = actionsRepository.viewerState(owner, name)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { viewerState ->
                    _state.update {
                        it.copy(
                            viewerState = viewerState,
                            isLoadingViewerState = false,
                            viewerStateError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingViewerState = false, viewerStateError = true) }
                }
            )
        }
    }

    private fun toggleStar() {
        val viewerState = _state.value.viewerState ?: return
        if (_state.value.isUpdatingStar || sessionLogin == null) return
        val target = !viewerState.isStarred
        val revision = sessionRevision
        _state.update { it.copy(isUpdatingStar = true, starFailure = null) }
        starJob?.cancel()
        starJob = viewModelScope.launch {
            val result = actionsRepository.setStarred(owner, name, target)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { starred ->
                    _state.update {
                        it.copy(
                            viewerState = it.viewerState?.copy(isStarred = starred),
                            isUpdatingStar = false,
                            starFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isUpdatingStar = false, starFailure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun toggleWatch() {
        val viewerState = _state.value.viewerState ?: return
        if (_state.value.isUpdatingWatch || sessionLogin == null) return
        val target = !viewerState.isWatching
        val revision = sessionRevision
        _state.update { it.copy(isUpdatingWatch = true, watchFailure = null) }
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            val result = actionsRepository.setWatching(owner, name, target)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { watching ->
                    _state.update {
                        it.copy(
                            viewerState = it.viewerState?.copy(isWatching = watching),
                            isUpdatingWatch = false,
                            watchFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isUpdatingWatch = false, watchFailure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun fork() {
        if (
            sessionLogin == null ||
            _state.value.isForking ||
            _state.value.forkedRepository != null
        ) return
        val revision = sessionRevision
        _state.update { it.copy(isForking = true, forkFailure = null) }
        forkJob?.cancel()
        forkJob = viewModelScope.launch {
            val result = actionsRepository.fork(owner, name)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { forkedRepository ->
                    _state.update {
                        it.copy(
                            details = it.details?.copy(forks = it.details.forks + 1),
                            forkedRepository = forkedRepository,
                            isForking = false,
                            forkFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isForking = false, forkFailure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun updateTopics(topics: List<String>) {
        if (!_state.value.canEditTopics || _state.value.isUpdatingTopics) return
        val revision = sessionRevision
        _state.update { it.copy(isUpdatingTopics = true, topicsFailure = null) }
        topicsJob?.cancel()
        topicsJob = viewModelScope.launch {
            val result = repository.updateTopics(owner, name, topics)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            details = it.details?.copy(topics = updated),
                            isUpdatingTopics = false,
                            topicsFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isUpdatingTopics = false, topicsFailure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun updateSettings(edit: GithubRepositorySettingsEdit) {
        val current = _state.value
        val details = current.details ?: return
        if (!current.canManageSettings || current.isUpdatingSettings) return
        if (isConfirmedBy(edit, details)) return
        val revision = sessionRevision
        _state.update { it.copy(isUpdatingSettings = true, settingsFailure = null) }
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            val result = repository.updateSettings(owner, name, edit)
            if (!isActive || revision != sessionRevision) return@launch
            result.fold(
                onSuccess = { settings ->
                    _state.update { state ->
                        val updated = state.details?.let { loaded ->
                            loaded.copy(
                                repository = loaded.repository.copy(
                                    isPrivate = settings.isPrivate,
                                    description = settings.description
                                ),
                                isArchived = settings.isArchived,
                                features = settings.features,
                                defaultBranch = settings.defaultBranch
                            )
                        }
                        state.copy(
                            details = updated,
                            isUpdatingSettings = false,
                            settingsFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isUpdatingSettings = false, settingsFailure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun isConfirmedBy(
        edit: GithubRepositorySettingsEdit,
        details: GithubRepositoryDetails
    ) = listOfNotNull(
        edit.isPrivate?.let { it to details.repository.isPrivate },
        edit.isArchived?.let { it to details.isArchived },
        edit.hasIssues?.let { it to details.features.hasIssues },
        edit.hasWiki?.let { it to details.features.hasWiki },
        edit.hasProjects?.let { it to details.features.hasProjects },
        edit.description?.let { it to details.repository.description.orEmpty() },
        edit.defaultBranch?.let { it to details.defaultBranch }
    ).all { (wanted, confirmed) -> wanted == confirmed } // an edit carrying nothing has nothing to send

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubRepositoryDetailsRepository,
        private val actionsRepository: GithubRepositoryActionsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryDetailViewModel::class.java))
            return RepositoryDetailViewModel(owner, name, repository, actionsRepository) as T
        }
    }
}
