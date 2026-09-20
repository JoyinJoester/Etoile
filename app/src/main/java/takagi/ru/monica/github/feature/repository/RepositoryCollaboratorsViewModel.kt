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
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.mergeItems

/** What landed after a collaborator write, worded for the person who asked for it. */
enum class RepositoryCollaboratorOutcome {
    InvitationSent,
    AccessUpdated,
    Removed
}

@Immutable
data class RepositoryCollaboratorFeedback(
    val login: String,
    val outcome: RepositoryCollaboratorOutcome
)

@Immutable
data class RepositoryCollaboratorsUiState(
    val owner: String,
    val name: String,
    val items: List<GithubCollaborator> = emptyList(),
    val query: String = "",
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Boolean = false,
    val viewerLogin: String? = null,
    val viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.UNKNOWN,
    val isWriting: Boolean = false,
    val failure: RepositoryWriteFailure? = null,
    val outcome: RepositoryCollaboratorFeedback? = null
) {
    val fullName: String get() = "$owner/$name"
    val filteredItems: List<GithubCollaborator>
        get() = query.trim().takeIf(String::isNotBlank)?.let { value ->
            items.filter { it.user.login.contains(value, ignoreCase = true) }
        } ?: items
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore

    /** Granting and taking away access is an admin right, which push access does not cover. */
    val canManage: Boolean get() = viewerLogin != null && viewerRole.canAdmin

    // Without the collaborator endpoint the list is public contributors, and a contributor
    // row says nothing about whether that account already holds access here.
    fun isManageable(collaborator: GithubCollaborator): Boolean =
        canManage && !collaborator.isContributor && collaborator.role != GithubCollaboratorRole.UNKNOWN
}

sealed interface RepositoryCollaboratorsAction {
    data object Refresh : RepositoryCollaboratorsAction
    data object Retry : RepositoryCollaboratorsAction
    data object LoadMore : RepositoryCollaboratorsAction
    data class Search(val query: String) : RepositoryCollaboratorsAction
    data class ChangeRole(val login: String, val role: GithubCollaboratorRole) : RepositoryCollaboratorsAction
    data class Remove(val login: String) : RepositoryCollaboratorsAction
    data object DismissFeedback : RepositoryCollaboratorsAction
}

class RepositoryCollaboratorsViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RepositoryCollaboratorsUiState(owner, name))
    val state: StateFlow<RepositoryCollaboratorsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var viewerRoleJob: Job? = null
    private var sessionLogin: String? = null
    private var failedReset = true

    init {
        load(reset = true)
    }

    fun onSessionChanged(session: GithubSession) {
        val login = (session as? GithubSession.SignedIn)?.account?.login
        if (login == sessionLogin) return
        sessionLogin = login
        // A different account inherits nothing from the last one, not even a role still loading.
        writeJob?.cancel()
        _state.update {
            it.copy(
                viewerLogin = login,
                viewerRole = GithubCollaboratorRole.UNKNOWN,
                isWriting = false,
                failure = null,
                outcome = null
            )
        }
        loadViewerRole(signedIn = login != null)
    }

    private fun loadViewerRole(signedIn: Boolean) {
        viewerRoleJob?.cancel()
        if (!signedIn) {
            _state.update { it.copy(viewerRole = GithubCollaboratorRole.UNKNOWN) }
            return
        }
        viewerRoleJob = viewModelScope.launch {
            val role = repository.details(owner, name)
                .getOrNull()
                ?.viewerRole
                ?: GithubCollaboratorRole.UNKNOWN
            _state.update { it.copy(viewerRole = role) }
        }
    }

    fun onAction(action: RepositoryCollaboratorsAction) {
        when (action) {
            RepositoryCollaboratorsAction.Refresh -> load(reset = true, refreshing = true)
            RepositoryCollaboratorsAction.Retry -> load(
                reset = failedReset,
                refreshing = failedReset && _state.value.items.isNotEmpty()
            )
            RepositoryCollaboratorsAction.LoadMore -> load(reset = false)
            is RepositoryCollaboratorsAction.Search -> _state.update { it.copy(query = action.query) }
            RepositoryCollaboratorsAction.DismissFeedback -> _state.update {
                it.copy(failure = null, outcome = null)
            }
            is RepositoryCollaboratorsAction.ChangeRole -> changeRole(action.login, action.role)
            is RepositoryCollaboratorsAction.Remove -> remove(action.login)
        }
    }

    private fun changeRole(login: String, role: GithubCollaboratorRole) {
        if (!currentCanManage()) return
        val invite = GithubCollaboratorInvite.fromInput(login, role).getOrNull() ?: return
        submit(login, { repository.setCollaborator(owner, name, invite) }) { change ->
            when (change) {
                GithubCollaboratorChange.Invited -> RepositoryCollaboratorOutcome.InvitationSent
                GithubCollaboratorChange.Updated -> RepositoryCollaboratorOutcome.AccessUpdated
            }
        }
    }

    private fun remove(login: String) {
        if (!currentCanManage()) return
        submit(login, { repository.removeCollaborator(owner, name, login) }) {
            RepositoryCollaboratorOutcome.Removed
        }
    }

    private fun currentCanManage(): Boolean {
        val current = _state.value
        return current.canManage && !current.isWriting
    }

    private fun <T> submit(
        login: String,
        call: suspend () -> Result<T>,
        describe: (T) -> RepositoryCollaboratorOutcome
    ) {
        _state.update { it.copy(isWriting = true, failure = null, outcome = null) }
        writeJob = viewModelScope.launch {
            call().fold(
                onSuccess = { value ->
                    _state.update {
                        it.copy(
                            isWriting = false,
                            outcome = RepositoryCollaboratorFeedback(
                                login = login,
                                outcome = describe(value)
                            )
                        )
                    }
                    // The row has to show the access that is now in place, not the one asked for.
                    load(reset = true, refreshing = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isWriting = false, failure = githubWriteFailure(error))
                    }
                }
            )
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = if (reset && !refreshing) emptyList() else it.items,
                isLoading = reset && !refreshing,
                isLoadingMore = !reset,
                isRefreshing = refreshing,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            repository.collaborators(owner, name, page = page).fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            items = result.mergeItems(state.items, reset) { it.user.login },
                            nextPage = result.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    failedReset = reset
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = true) }
                }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubRepositoryDetailsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RepositoryCollaboratorsViewModel::class.java))
            return RepositoryCollaboratorsViewModel(owner, name, repository) as T
        }
    }
}
