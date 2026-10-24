package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubSession

@Immutable
data class InviteCollaboratorUiState(
    val login: String = "",
    val role: GithubCollaboratorRole = GithubCollaboratorRole.WRITE,
    val isSubmitting: Boolean = false,
    val failure: RepositoryWriteFailure? = null,
    val feedback: RepositoryCollaboratorFeedback? = null,
    val canManage: Boolean = false
) {
    val isLoginUsable: Boolean get() = GithubCollaboratorInvite.isValidLogin(login)

    // A typed-but-unusable name deserves a hint while a blank one is only a disabled button.
    val showLoginHint: Boolean get() = login.isNotBlank() && !isLoginUsable
    val canSubmit: Boolean get() = isLoginUsable && !isSubmitting && canManage
}

sealed interface InviteCollaboratorAction {
    data class LoginChanged(val login: String) : InviteCollaboratorAction
    data class RoleChanged(val role: GithubCollaboratorRole) : InviteCollaboratorAction
    data object Submit : InviteCollaboratorAction
    data object ConsumeFeedback : InviteCollaboratorAction
}

class InviteCollaboratorViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubRepositoryDetailsRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
) : ViewModel() {
    private val requests = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private val _state = MutableStateFlow(InviteCollaboratorUiState())
    val state: StateFlow<InviteCollaboratorUiState> = _state.asStateFlow()

    private var accountId: Long? = null
    private var sessionInitialized = false
    private val drafts = mutableMapOf<Long, Pair<String, GithubCollaboratorRole>>()

    init {
        sessions?.let { source -> viewModelScope.launch { source.collect { onSessionChanged(it) } } }
    }

    fun onSessionChanged(session: GithubSession) {
        val account = (session as? GithubSession.SignedIn)?.account
        if (sessionInitialized && account?.id == accountId) return
        accountId?.let { drafts[it] = _state.value.login to _state.value.role }
        sessionInitialized = true
        accountId = account?.id
        requests.coroutineContext.cancelChildren()
        val draft = account?.id?.let { id ->
            drafts[id] ?: if (savedState.contains("$LOGIN_KEY:$id")) {
                savedState.get<String>("$LOGIN_KEY:$id").orEmpty() to savedState.get<String>("$ROLE_KEY:$id").toRestoredRole()
            } else if (savedState.get<Long>(ACCOUNT_KEY) == id) {
                savedState.get<String>(LOGIN_KEY).orEmpty() to savedState.get<String>(ROLE_KEY).toRestoredRole()
            } else null
        }
        _state.value = InviteCollaboratorUiState(login = draft?.first.orEmpty(), role = draft?.second ?: GithubCollaboratorRole.WRITE)
        if (account == null) return
        requests.launch {
            val role = repository.details(owner, name).currentRepositoryResult().getOrNull()?.viewerRole
            _state.update { it.copy(canManage = role?.canAdmin == true) }
        }
    }

    fun onAction(action: InviteCollaboratorAction) {
        if (action == InviteCollaboratorAction.ConsumeFeedback) {
            _state.update { it.copy(feedback = null) }
            return
        }
        if (_state.value.isSubmitting) return
        when (action) {
            is InviteCollaboratorAction.LoginChanged -> edit(_state.value.copy(login = action.login))
            is InviteCollaboratorAction.RoleChanged -> edit(_state.value.copy(role = action.role))
            InviteCollaboratorAction.Submit -> submit()
            InviteCollaboratorAction.ConsumeFeedback -> Unit
        }
    }

    private fun edit(next: InviteCollaboratorUiState) {
        _state.value = next.copy(failure = null)
        savedState[LOGIN_KEY] = next.login
        savedState[ROLE_KEY] = next.role.name
        accountId?.let { id ->
            savedState[ACCOUNT_KEY] = id
            savedState["$LOGIN_KEY:$id"] = next.login
            savedState["$ROLE_KEY:$id"] = next.role.name
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val invite = GithubCollaboratorInvite.fromInput(current.login, current.role).getOrNull() ?: return
        _state.update { it.copy(isSubmitting = true, failure = null, feedback = null) }
        requests.launch {
            repository.setCollaborator(owner, name, invite).currentRepositoryResult().fold(
                onSuccess = { change ->
                    savedState.remove<Any>(LOGIN_KEY)
                    accountId?.let { id ->
                        savedState["$LOGIN_KEY:$id"] = ""
                        drafts.remove(id)
                    }
                    _state.update {
                        it.copy(
                            login = "",
                            isSubmitting = false,
                            failure = null,
                            feedback = RepositoryCollaboratorFeedback(
                                login = invite.login,
                                outcome = when (change) {
                                    GithubCollaboratorChange.Invited ->
                                        RepositoryCollaboratorOutcome.InvitationSent
                                    GithubCollaboratorChange.Updated ->
                                        RepositoryCollaboratorOutcome.AccessUpdated
                                }
                            )
                        )
                    }
                },
                // The typed login stays on screen, so fixing a refusal beats retyping it.
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, failure = githubWriteFailure(error)) }
                }
            )
        }
    }

    private fun String?.toRestoredRole(): GithubCollaboratorRole =
        GithubCollaboratorRole.values()
            .firstOrNull { it.name == this && it.apiPermission != null }
            ?: GithubCollaboratorRole.WRITE

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubRepositoryDetailsRepository,
        private val sessions: kotlinx.coroutines.flow.Flow<GithubSession>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(InviteCollaboratorViewModel::class.java))
            return InviteCollaboratorViewModel(
                owner, name, repository, extras.createSavedStateHandle(), sessions
            ) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InviteCollaboratorViewModel::class.java))
            return InviteCollaboratorViewModel(owner, name, repository, sessions = sessions) as T
        }
    }

    private companion object {
        const val ACCOUNT_KEY = "invite_collaborator_account"
        const val LOGIN_KEY = "invite_collaborator_login"
        const val ROLE_KEY = "invite_collaborator_role"
    }
}
