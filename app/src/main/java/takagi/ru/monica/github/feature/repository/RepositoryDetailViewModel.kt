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
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositoryViewerState
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubSession

@Immutable
data class RepositoryDetailUiState(
    val owner: String,
    val name: String,
    val details: GithubRepositoryDetails? = null,
    val readme: String? = null,
    val isLoadingDetails: Boolean = true,
    val isLoadingReadme: Boolean = true,
    val detailsError: Boolean = false,
    val readmeError: Boolean = false,
    val viewerState: GithubRepositoryViewerState? = null,
    val isLoadingViewerState: Boolean = false,
    val viewerStateError: Boolean = false,
    val isUpdatingStar: Boolean = false,
    val starError: Boolean = false,
    val isUpdatingWatch: Boolean = false,
    val watchError: Boolean = false,
    val isForking: Boolean = false,
    val forkError: Boolean = false,
    val forkedRepository: GithubRepository? = null,
    val branchProtection: GithubBranchProtection? = null,
    val isLoadingBranchProtection: Boolean = false,
    val branchProtectionError: Boolean = false,
    val isUpdatingTopics: Boolean = false,
    val topicsError: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}

sealed interface RepositoryDetailAction {
    data object RetryDetails : RepositoryDetailAction
    data object RetryReadme : RepositoryDetailAction
    data object RetryViewerState : RepositoryDetailAction
    data object ToggleStar : RepositoryDetailAction
    data object ToggleWatch : RepositoryDetailAction
    data object Fork : RepositoryDetailAction
    data object RetryBranchProtection : RepositoryDetailAction
    data class UpdateTopics(val topics: List<String>) : RepositoryDetailAction
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
    private var viewerStateJob: Job? = null
    private var starJob: Job? = null
    private var watchJob: Job? = null
    private var forkJob: Job? = null
    private var topicsJob: Job? = null
    private var sessionLogin: String? = null

    init {
        loadDetails()
        loadReadme()
    }

    fun onAction(action: RepositoryDetailAction) {
        when (action) {
            RepositoryDetailAction.RetryDetails -> loadDetails()
            RepositoryDetailAction.RetryReadme -> loadReadme(_state.value.details?.defaultBranch)
            RepositoryDetailAction.RetryViewerState -> loadViewerState()
            RepositoryDetailAction.ToggleStar -> toggleStar()
            RepositoryDetailAction.ToggleWatch -> toggleWatch()
            RepositoryDetailAction.Fork -> fork()
            RepositoryDetailAction.RetryBranchProtection -> loadBranchProtection()
            is RepositoryDetailAction.UpdateTopics -> updateTopics(action.topics)
        }
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login == sessionLogin) return
        sessionLogin = login
        if (login == null) {
            viewerStateJob?.cancel()
            starJob?.cancel()
            watchJob?.cancel()
            forkJob?.cancel()
            topicsJob?.cancel()
            _state.update {
                it.copy(
                    viewerState = null,
                    isLoadingViewerState = false,
                    viewerStateError = false,
                    isUpdatingStar = false,
                    starError = false,
                    isUpdatingWatch = false,
                    watchError = false,
                    isForking = false,
                    forkError = false,
                    forkedRepository = null,
                    isUpdatingTopics = false,
                    topicsError = false
                )
            }
        } else {
            loadViewerState()
        }
    }

    private fun loadDetails() {
        detailsJob?.cancel()
        _state.update { it.copy(isLoadingDetails = true, detailsError = false) }
        detailsJob = viewModelScope.launch {
            repository.details(owner, name).fold(
                onSuccess = { details ->
                    _state.update { it.copy(details = details, isLoadingDetails = false, detailsError = false) }
                    loadBranchProtection(details.defaultBranch)
                },
                onFailure = {
                    _state.update { it.copy(isLoadingDetails = false, detailsError = true) }
                }
            )
        }
    }

    private fun loadBranchProtection(branch: String? = _state.value.details?.defaultBranch) {
        val ref = branch?.takeIf(String::isNotBlank) ?: return
        _state.update { it.copy(isLoadingBranchProtection = true, branchProtectionError = false) }
        viewModelScope.launch {
            repository.branchProtection(owner, name, ref).fold(
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
        _state.update { it.copy(isLoadingReadme = true, readmeError = false) }
        readmeJob = viewModelScope.launch {
            repository.readme(owner, name, ref).fold(
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
        _state.update { it.copy(isLoadingViewerState = true, viewerStateError = false) }
        viewerStateJob = viewModelScope.launch {
            actionsRepository.viewerState(owner, name).fold(
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
        _state.update { it.copy(isUpdatingStar = true, starError = false) }
        starJob?.cancel()
        starJob = viewModelScope.launch {
            actionsRepository.setStarred(owner, name, target).fold(
                onSuccess = { starred ->
                    _state.update {
                        it.copy(
                            viewerState = it.viewerState?.copy(isStarred = starred),
                            isUpdatingStar = false,
                            starError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingStar = false, starError = true) }
                }
            )
        }
    }

    private fun toggleWatch() {
        val viewerState = _state.value.viewerState ?: return
        if (_state.value.isUpdatingWatch || sessionLogin == null) return
        val target = !viewerState.isWatching
        _state.update { it.copy(isUpdatingWatch = true, watchError = false) }
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            actionsRepository.setWatching(owner, name, target).fold(
                onSuccess = { watching ->
                    _state.update {
                        it.copy(
                            viewerState = it.viewerState?.copy(isWatching = watching),
                            isUpdatingWatch = false,
                            watchError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingWatch = false, watchError = true) }
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
        _state.update { it.copy(isForking = true, forkError = false) }
        forkJob?.cancel()
        forkJob = viewModelScope.launch {
            actionsRepository.fork(owner, name).fold(
                onSuccess = { forkedRepository ->
                    _state.update {
                        it.copy(
                            details = it.details?.copy(forks = it.details.forks + 1),
                            forkedRepository = forkedRepository,
                            isForking = false,
                            forkError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isForking = false, forkError = true) }
                }
            )
        }
    }

    private fun updateTopics(topics: List<String>) {
        if (sessionLogin == null || _state.value.isUpdatingTopics) return
        _state.update { it.copy(isUpdatingTopics = true, topicsError = false) }
        topicsJob?.cancel()
        topicsJob = viewModelScope.launch {
            repository.updateTopics(owner, name, topics).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            details = it.details?.copy(topics = updated),
                            isUpdatingTopics = false,
                            topicsError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isUpdatingTopics = false, topicsError = true) }
                }
            )
        }
    }

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
