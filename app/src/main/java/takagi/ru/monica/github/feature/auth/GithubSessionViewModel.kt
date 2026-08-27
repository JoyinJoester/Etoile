package takagi.ru.monica.github.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.AwaitGithubDeviceAuthorizationUseCase
import takagi.ru.monica.github.domain.GithubAuthRepository
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubDeviceAuthRepository
import takagi.ru.monica.github.domain.GithubDeviceAuthorization
import takagi.ru.monica.github.domain.GithubDeviceAuthorizationDeniedException
import takagi.ru.monica.github.domain.GithubDeviceAuthorizationExpiredException
import takagi.ru.monica.github.domain.GithubSession

enum class GithubSignInError { INVALID_TOKEN, REQUEST_FAILED }
enum class GithubDeviceSignInError { REQUEST_FAILED, DENIED, EXPIRED, VERIFICATION_FAILED }

@Immutable
sealed interface GithubDeviceSignInUiState {
    data object Unavailable : GithubDeviceSignInUiState
    data object Idle : GithubDeviceSignInUiState
    data object Requesting : GithubDeviceSignInUiState
    data class Waiting(
        val userCode: String,
        val verificationUri: String,
        val expiresAtEpochMillis: Long
    ) : GithubDeviceSignInUiState
    data object Verifying : GithubDeviceSignInUiState
    data class Failed(val error: GithubDeviceSignInError) : GithubDeviceSignInUiState
}

@Immutable
data class GithubSessionUiState(
    val session: GithubSession = GithubSession.Loading,
    val accounts: List<GithubAccount> = emptyList(),
    val tokenInput: String = "",
    val isSubmitting: Boolean = false,
    val signInError: GithubSignInError? = null,
    val deviceSignIn: GithubDeviceSignInUiState = GithubDeviceSignInUiState.Unavailable,
    val isAccountActionRunning: Boolean = false,
    val accountActionError: Boolean = false
) {
    override fun toString(): String =
        "GithubSessionUiState(session=$session, accounts=$accounts, tokenInput=<redacted>, isSubmitting=$isSubmitting, signInError=$signInError, deviceSignIn=$deviceSignIn, isAccountActionRunning=$isAccountActionRunning, accountActionError=$accountActionError)"
}

sealed interface GithubSessionAction {
    data class TokenChanged(val value: String) : GithubSessionAction
    data object SignIn : GithubSessionAction
    data object StartDeviceSignIn : GithubSessionAction
    data object CancelDeviceSignIn : GithubSessionAction
    data class SwitchAccount(val accountId: Long) : GithubSessionAction
    data class RemoveAccount(val accountId: Long) : GithubSessionAction
    data object SignOut : GithubSessionAction
    data object RetryRestore : GithubSessionAction
    data object ClearForm : GithubSessionAction
    data object ClearAccountError : GithubSessionAction
}

class GithubSessionViewModel(
    private val repository: GithubAuthRepository,
    private val deviceAuthRepository: GithubDeviceAuthRepository,
    private val awaitDeviceAuthorization: AwaitGithubDeviceAuthorizationUseCase =
        AwaitGithubDeviceAuthorizationUseCase(deviceAuthRepository)
) : ViewModel() {
    private val _state = MutableStateFlow(
        GithubSessionUiState(deviceSignIn = idleDeviceSignInState())
    )
    val state: StateFlow<GithubSessionUiState> = _state.asStateFlow()
    private var deviceSignInJob: Job? = null
    private var accountActionJob: Job? = null
    private var activeDeviceAuthorization: GithubDeviceAuthorization? = null

    init {
        viewModelScope.launch {
            repository.session.collect { session -> _state.update { it.copy(session = session) } }
        }
        viewModelScope.launch {
            repository.accounts.collect { accounts -> _state.update { it.copy(accounts = accounts) } }
        }
        restore()
    }

    fun onAction(action: GithubSessionAction) {
        when (action) {
            is GithubSessionAction.TokenChanged -> _state.update { it.copy(tokenInput = action.value.take(255), signInError = null) }
            GithubSessionAction.SignIn -> signIn()
            GithubSessionAction.StartDeviceSignIn -> startDeviceSignIn()
            GithubSessionAction.CancelDeviceSignIn -> cancelDeviceSignIn()
            is GithubSessionAction.SwitchAccount -> switchAccount(action.accountId)
            is GithubSessionAction.RemoveAccount -> removeAccount(action.accountId)
            GithubSessionAction.SignOut -> signOut()
            GithubSessionAction.RetryRestore -> restore()
            GithubSessionAction.ClearForm -> clearForm()
            GithubSessionAction.ClearAccountError -> _state.update { it.copy(accountActionError = false) }
        }
    }

    private fun restore() {
        viewModelScope.launch { repository.restore() }
    }

    private fun signIn() {
        if (_state.value.isSubmitting) return
        cancelDeviceFlow()
        val token = _state.value.tokenInput
        _state.update {
            it.copy(
                isSubmitting = true,
                signInError = null,
                deviceSignIn = idleDeviceSignInState()
            )
        }
        viewModelScope.launch {
            repository.signInWithToken(token).fold(
                onSuccess = { resetFormState() },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            signInError = if (error is IllegalArgumentException) GithubSignInError.INVALID_TOKEN else GithubSignInError.REQUEST_FAILED
                        )
                    }
                }
            )
        }
    }

    private fun startDeviceSignIn() {
        if (
            !deviceAuthRepository.isConfigured ||
            _state.value.isSubmitting ||
            deviceSignInJob?.isActive == true
        ) {
            return
        }
        cancelDeviceFlow()
        _state.update {
            it.copy(
                signInError = null,
                deviceSignIn = GithubDeviceSignInUiState.Requesting
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val authorization = deviceAuthRepository.start().getOrElse { error ->
                showDeviceFailure(error)
                return@launch
            }
            activeDeviceAuthorization = authorization
            _state.update {
                it.copy(
                    deviceSignIn = GithubDeviceSignInUiState.Waiting(
                        userCode = authorization.userCode,
                        verificationUri = authorization.verificationUri,
                        expiresAtEpochMillis = authorization.expiresAtEpochMillis
                    )
                )
            }
            val token = awaitDeviceAuthorization(authorization).getOrElse { error ->
                showDeviceFailure(error)
                return@launch
            }
            _state.update { it.copy(deviceSignIn = GithubDeviceSignInUiState.Verifying) }
            repository.signInWithToken(token.accessToken).fold(
                onSuccess = { resetFormState() },
                onFailure = {
                    _state.update {
                        it.copy(
                            deviceSignIn = GithubDeviceSignInUiState.Failed(
                                GithubDeviceSignInError.VERIFICATION_FAILED
                            )
                        )
                    }
                }
            )
            activeDeviceAuthorization = null
        }
        deviceSignInJob = job
        job.invokeOnCompletion {
            if (deviceSignInJob === job) {
                deviceSignInJob = null
                activeDeviceAuthorization = null
            }
        }
        job.start()
    }

    private fun showDeviceFailure(error: Throwable) {
        val reason = when (error) {
            is GithubDeviceAuthorizationDeniedException -> GithubDeviceSignInError.DENIED
            is GithubDeviceAuthorizationExpiredException -> GithubDeviceSignInError.EXPIRED
            else -> GithubDeviceSignInError.REQUEST_FAILED
        }
        _state.update {
            it.copy(deviceSignIn = GithubDeviceSignInUiState.Failed(reason))
        }
    }

    private fun cancelDeviceSignIn() {
        cancelDeviceFlow()
        _state.update { it.copy(deviceSignIn = idleDeviceSignInState()) }
    }

    private fun cancelDeviceFlow() {
        deviceSignInJob?.cancel()
        deviceSignInJob = null
        activeDeviceAuthorization = null
    }

    private fun clearForm() {
        cancelDeviceFlow()
        resetFormState()
    }

    private fun switchAccount(accountId: Long) {
        if ((_state.value.session as? GithubSession.SignedIn)?.account?.id == accountId) return
        runAccountAction { repository.switchAccount(accountId).map { Unit } }
    }

    private fun removeAccount(accountId: Long) {
        runAccountAction { repository.removeAccount(accountId) }
    }

    private fun signOut() {
        cancelDeviceFlow()
        runAccountAction {
            repository.signOut()
            resetFormState()
            Result.success(Unit)
        }
    }

    private fun runAccountAction(block: suspend () -> Result<Unit>) {
        if (accountActionJob?.isActive == true || _state.value.isSubmitting) return
        _state.update { it.copy(isAccountActionRunning = true, accountActionError = false) }
        val job = viewModelScope.launch {
            block().fold(
                onSuccess = {
                    _state.update {
                        it.copy(isAccountActionRunning = false, accountActionError = false)
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(isAccountActionRunning = false, accountActionError = true)
                    }
                }
            )
        }
        accountActionJob = job
        job.invokeOnCompletion {
            if (accountActionJob === job) accountActionJob = null
        }
    }

    private fun resetFormState() {
        _state.update {
            it.copy(
                tokenInput = "",
                isSubmitting = false,
                signInError = null,
                deviceSignIn = idleDeviceSignInState()
            )
        }
    }

    private fun idleDeviceSignInState(): GithubDeviceSignInUiState =
        if (deviceAuthRepository.isConfigured) {
            GithubDeviceSignInUiState.Idle
        } else {
            GithubDeviceSignInUiState.Unavailable
        }

    class Factory(
        private val repository: GithubAuthRepository,
        private val deviceAuthRepository: GithubDeviceAuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GithubSessionViewModel::class.java))
            return GithubSessionViewModel(repository, deviceAuthRepository) as T
        }
    }
}
