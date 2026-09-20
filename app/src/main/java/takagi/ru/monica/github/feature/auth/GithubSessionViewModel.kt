package takagi.ru.monica.github.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import takagi.ru.monica.github.domain.GithubWebAuthRepository
import takagi.ru.monica.github.domain.GithubWebOAuthDeniedException
import takagi.ru.monica.github.domain.GithubWebOAuthInvalidCallbackException
import takagi.ru.monica.github.domain.UnavailableGithubWebAuthRepository

enum class GithubSignInError { INVALID_TOKEN, REQUEST_FAILED }
enum class GithubDeviceSignInError { REQUEST_FAILED, DENIED, EXPIRED, VERIFICATION_FAILED }
enum class GithubBrowserSignInError { REQUEST_FAILED, DENIED, INVALID_CALLBACK, VERIFICATION_FAILED }

@Immutable
sealed interface GithubBrowserSignInUiState {
    data object Unavailable : GithubBrowserSignInUiState
    data object Idle : GithubBrowserSignInUiState
    data class Opening(val authorizationUrl: String) : GithubBrowserSignInUiState
    data class Waiting(val authorizationUrl: String) : GithubBrowserSignInUiState
    data object Verifying : GithubBrowserSignInUiState
    data class Failed(val error: GithubBrowserSignInError) : GithubBrowserSignInUiState
}

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
    val signInCompletionVersion: Long = 0L,
    val deviceSignIn: GithubDeviceSignInUiState = GithubDeviceSignInUiState.Unavailable,
    val browserSignIn: GithubBrowserSignInUiState = GithubBrowserSignInUiState.Unavailable,
    val isAccountActionRunning: Boolean = false,
    val accountActionError: Boolean = false
) {
    override fun toString(): String =
        "GithubSessionUiState(session=$session, accounts=$accounts, tokenInput=<redacted>, isSubmitting=$isSubmitting, signInError=$signInError, deviceSignIn=$deviceSignIn, browserSignIn=${browserSignIn::class.simpleName}, isAccountActionRunning=$isAccountActionRunning, accountActionError=$accountActionError)"
}

sealed interface GithubSessionAction {
    data class TokenChanged(val value: String) : GithubSessionAction
    data object SignIn : GithubSessionAction
    data object StartDeviceSignIn : GithubSessionAction
    data object CancelDeviceSignIn : GithubSessionAction
    data object StartBrowserSignIn : GithubSessionAction
    data object BrowserSignInOpened : GithubSessionAction
    data object CancelBrowserSignIn : GithubSessionAction
    data class BrowserSignInCallback(val url: String) : GithubSessionAction
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
    private val webAuthRepository: GithubWebAuthRepository = UnavailableGithubWebAuthRepository,
    private val awaitDeviceAuthorization: AwaitGithubDeviceAuthorizationUseCase =
        AwaitGithubDeviceAuthorizationUseCase(deviceAuthRepository)
) : ViewModel() {
    private val _state = MutableStateFlow(
        GithubSessionUiState(
            deviceSignIn = idleDeviceSignInState(),
            browserSignIn = idleBrowserSignInState()
        )
    )
    val state: StateFlow<GithubSessionUiState> = _state.asStateFlow()
    private var tokenSignInJob: Job? = null
    private var browserSignInJob: Job? = null
    private var deviceSignInJob: Job? = null
    private var accountActionJob: Job? = null
    private var activeDeviceAuthorization: GithubDeviceAuthorization? = null
    // A fresh ViewModel may receive a persisted OAuth callback after process
    // recreation. Once the user cancels, only a new authorization can reopen it.
    private var ignoreBrowserCallbacks = false

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
            GithubSessionAction.StartBrowserSignIn -> startBrowserSignIn()
            GithubSessionAction.BrowserSignInOpened -> markBrowserSignInOpened()
            GithubSessionAction.CancelBrowserSignIn -> cancelBrowserSignIn()
            is GithubSessionAction.BrowserSignInCallback -> completeBrowserSignIn(action.url)
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
        if (_state.value.isSubmitting || _state.value.isAccountActionRunning) return
        cancelDeviceFlow()
        cancelBrowserFlow()
        val token = _state.value.tokenInput
        _state.update {
            it.copy(
                isSubmitting = true,
                signInError = null,
                deviceSignIn = idleDeviceSignInState(),
                browserSignIn = idleBrowserSignInState()
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = repository.signInWithToken(token)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { resetFormState(signInCompleted = true) },
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
        tokenSignInJob = job
        job.invokeOnCompletion {
            if (tokenSignInJob === job) tokenSignInJob = null
        }
        job.start()
    }

    private fun startBrowserSignIn() {
        if (!webAuthRepository.isConfigured) {
            startDeviceSignIn()
            return
        }
        if (_state.value.isSubmitting || _state.value.isAccountActionRunning ||
            deviceSignInJob?.isActive == true || browserSignInJob?.isActive == true
        ) return
        cancelDeviceFlow()
        cancelBrowserFlow()
        webAuthRepository.start().fold(
            onSuccess = { authorization ->
                ignoreBrowserCallbacks = false
                _state.update {
                    it.copy(
                        signInError = null,
                        deviceSignIn = idleDeviceSignInState(),
                        browserSignIn = GithubBrowserSignInUiState.Opening(authorization.authorizationUrl)
                    )
                }
            },
            onFailure = {
                _state.update {
                    it.copy(
                        browserSignIn = GithubBrowserSignInUiState.Failed(
                            GithubBrowserSignInError.REQUEST_FAILED
                        )
                    )
                }
            }
        )
    }

    private fun markBrowserSignInOpened() {
        val opening = _state.value.browserSignIn as? GithubBrowserSignInUiState.Opening ?: return
        _state.update { it.copy(browserSignIn = GithubBrowserSignInUiState.Waiting(opening.authorizationUrl)) }
    }

    private fun completeBrowserSignIn(url: String) {
        if (!webAuthRepository.isCallbackUrl(url) || ignoreBrowserCallbacks ||
            _state.value.isSubmitting || _state.value.isAccountActionRunning ||
            browserSignInJob?.isActive == true || deviceSignInJob?.isActive == true
        ) return
        cancelDeviceFlow()
        _state.update { it.copy(browserSignIn = GithubBrowserSignInUiState.Verifying) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val exchangeResult = webAuthRepository.exchangeCallback(url)
            currentCoroutineContext().ensureActive()
            val token = exchangeResult.getOrElse { error ->
                val reason = when (error) {
                    is GithubWebOAuthDeniedException -> GithubBrowserSignInError.DENIED
                    is GithubWebOAuthInvalidCallbackException -> GithubBrowserSignInError.INVALID_CALLBACK
                    else -> GithubBrowserSignInError.REQUEST_FAILED
                }
                _state.update { it.copy(browserSignIn = GithubBrowserSignInUiState.Failed(reason)) }
                return@launch
            }
            val signInResult = repository.signInWithOAuth(token)
            currentCoroutineContext().ensureActive()
            signInResult.fold(
                onSuccess = { resetFormState(signInCompleted = true) },
                onFailure = {
                    _state.update {
                        it.copy(
                            browserSignIn = GithubBrowserSignInUiState.Failed(
                                GithubBrowserSignInError.VERIFICATION_FAILED
                            )
                        )
                    }
                }
            )
        }
        browserSignInJob = job
        job.invokeOnCompletion {
            if (browserSignInJob === job) browserSignInJob = null
        }
        job.start()
    }

    private fun cancelBrowserSignIn() {
        cancelBrowserFlow()
        _state.update { it.copy(browserSignIn = idleBrowserSignInState()) }
    }

    private fun cancelBrowserFlow() {
        ignoreBrowserCallbacks = true
        browserSignInJob?.cancel()
        browserSignInJob = null
        webAuthRepository.cancel()
    }

    private fun startDeviceSignIn() {
        if (
            !deviceAuthRepository.isConfigured ||
            _state.value.isSubmitting ||
            _state.value.isAccountActionRunning ||
            deviceSignInJob?.isActive == true
        ) {
            return
        }
        cancelDeviceFlow()
        cancelBrowserFlow()
        _state.update {
            it.copy(
                signInError = null,
                deviceSignIn = GithubDeviceSignInUiState.Requesting,
                browserSignIn = idleBrowserSignInState()
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val startResult = deviceAuthRepository.start()
            currentCoroutineContext().ensureActive()
            val authorization = startResult.getOrElse { error ->
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
            val authorizationResult = awaitDeviceAuthorization(authorization)
            currentCoroutineContext().ensureActive()
            val token = authorizationResult.getOrElse { error ->
                showDeviceFailure(error)
                return@launch
            }
            _state.update { it.copy(deviceSignIn = GithubDeviceSignInUiState.Verifying) }
            val signInResult = repository.signInWithOAuth(token)
            currentCoroutineContext().ensureActive()
            signInResult.fold(
                onSuccess = { resetFormState(signInCompleted = true) },
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
        tokenSignInJob?.cancel()
        tokenSignInJob = null
        cancelDeviceFlow()
        cancelBrowserFlow()
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
        runAccountAction {
            repository.signOut()
            resetFormState()
            Result.success(Unit)
        }
    }

    private fun runAccountAction(block: suspend () -> Result<Unit>) {
        if (accountActionJob?.isActive == true) return
        clearForm()
        _state.update { it.copy(isAccountActionRunning = true, accountActionError = false) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
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
        job.start()
    }

    private fun resetFormState(signInCompleted: Boolean = false) {
        if (signInCompleted) ignoreBrowserCallbacks = true
        _state.update {
            it.copy(
                tokenInput = "",
                isSubmitting = false,
                signInError = null,
                signInCompletionVersion = it.signInCompletionVersion + if (signInCompleted) 1L else 0L,
                deviceSignIn = idleDeviceSignInState(),
                browserSignIn = idleBrowserSignInState()
            )
        }
    }

    private fun idleDeviceSignInState(): GithubDeviceSignInUiState =
        if (deviceAuthRepository.isConfigured) {
            GithubDeviceSignInUiState.Idle
        } else {
            GithubDeviceSignInUiState.Unavailable
        }

    private fun idleBrowserSignInState(): GithubBrowserSignInUiState =
        if (webAuthRepository.isConfigured) {
            GithubBrowserSignInUiState.Idle
        } else {
            GithubBrowserSignInUiState.Unavailable
        }

    class Factory(
        private val repository: GithubAuthRepository,
        private val deviceAuthRepository: GithubDeviceAuthRepository,
        private val webAuthRepository: GithubWebAuthRepository = UnavailableGithubWebAuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GithubSessionViewModel::class.java))
            return GithubSessionViewModel(repository, deviceAuthRepository, webAuthRepository) as T
        }
    }
}
