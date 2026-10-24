package takagi.ru.monica.gitlab.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.gitlab.domain.GitLabAccounts
import takagi.ru.monica.gitlab.domain.GitLabAuthRepository
import takagi.ru.monica.gitlab.domain.GitLabInvalidCallbackException
import takagi.ru.monica.github.data.githubRunCatching

enum class GitLabSignInError { INVALID_INPUT, REQUEST_FAILED, INVALID_CALLBACK, BROWSER_UNAVAILABLE }

data class GitLabAccountsUiState(
    val accounts: GitLabAccounts = GitLabAccounts(),
    val instanceInput: String = "https://gitlab.com/",
    val tokenInput: String = "",
    val clientIdInput: String = "",
    val allowWrites: Boolean = false,
    val browserUrl: String? = null,
    val busy: Boolean = false,
    val error: GitLabSignInError? = null
) {
    override fun toString() = "GitLabAccountsUiState(accounts=${accounts.accounts.size}, busy=$busy, inputs=<redacted>)"
}

class GitLabAccountsViewModel(
    private val repository: GitLabAuthRepository,
    defaultClientId: String = ""
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitLabAccountsUiState(clientIdInput = defaultClientId))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var browserPreparation: Job? = null
    private var cleanupJob: Job? = null
    private var callbacksAllowed = true
    private var operationVersion = 0L
    private val restoration: Job

    init {
        viewModelScope.launch { repository.accounts.collect { value -> mutableState.update { it.copy(accounts = value) } } }
        restoration = viewModelScope.launch {
            githubRunCatching { repository.restore().getOrThrow() }
                .onFailure { mutableState.update { it.copy(error = GitLabSignInError.REQUEST_FAILED) } }
        }
    }

    fun instanceChanged(value: String) { if (!state.value.busy && state.value.browserUrl == null) mutableState.update { it.copy(instanceInput = value.take(2048), error = null) } }
    fun tokenChanged(value: String) { if (!state.value.busy) mutableState.update { it.copy(tokenInput = value.take(4096), error = null) } }
    fun clientIdChanged(value: String) { if (!state.value.busy && state.value.browserUrl == null) mutableState.update { it.copy(clientIdInput = value.take(512), error = null) } }
    fun allowWritesChanged(value: Boolean) { if (!state.value.busy && state.value.browserUrl == null) mutableState.update { it.copy(allowWrites = value) } }

    fun signInWithToken() {
        if (state.value.busy) return
        callbacksAllowed = false
        val input = state.value
        execute {
            repository.cancelBrowserSignIn()
            repository.signInWithToken(input.instanceInput, input.tokenInput).map {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(tokenInput = "", browserUrl = null) }
            }
        }
    }

    fun startBrowserSignIn() {
        if (state.value.busy || state.value.browserUrl != null) return
        val input = state.value
        browserPreparation = execute {
            repository.startBrowserSignIn(input.instanceInput, input.clientIdInput, input.allowWrites).map { url ->
                currentCoroutineContext().ensureActive()
                callbacksAllowed = true
                mutableState.update { it.copy(browserUrl = url, tokenInput = "") }
            }
        }
    }

    fun completeBrowserSignIn(url: String) {
        if (state.value.busy || !callbacksAllowed) return
        execute {
            repository.completeBrowserSignIn(url).map {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(browserUrl = null, tokenInput = "") }
            }
        }
    }

    fun browserUnavailable() { mutableState.update { it.copy(error = GitLabSignInError.BROWSER_UNAVAILABLE) } }
    fun cancelSignIn() {
        val version = ++operationVersion
        callbacksAllowed = false
        job?.cancel()
        val previousCleanup = cleanupJob
        val preparation = browserPreparation
        browserPreparation = null
        mutableState.update { it.copy(busy = false, browserUrl = null, tokenInput = "", error = null) }
        cleanupJob = viewModelScope.launch {
            // A cancelled encrypted commit may still finish on IO. Complete its
            // cleanup before allowing a replacement authorization to prepare.
            previousCleanup?.join()
            preparation?.join()
            val result = githubRunCatching { repository.cancelBrowserSignIn() }
            if (version == operationVersion) result.onFailure { failure ->
                mutableState.update { it.copy(error = failure.signInError()) }
            }
        }
    }

    fun activate(key: String) { if (!state.value.busy) execute { repository.activate(key) } }
    fun remove(key: String) { if (!state.value.busy) execute { repository.remove(key) } }

    private fun execute(block: suspend () -> Result<Unit>): Job {
        val version = ++operationVersion
        val pendingCleanup = cleanupJob
        mutableState.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            // Cold-start OAuth callbacks must wait for encrypted account restoration,
            // rather than being dropped while startup work is still running.
            val result = githubRunCatching {
                restoration.join()
                pendingCleanup?.join()
                block().getOrThrow()
            }
            currentCoroutineContext().ensureActive()
            if (version != operationVersion) return@launch
            mutableState.update {
                it.copy(busy = false, error = result.exceptionOrNull()?.signInError())
            }
        }
        return job!!
    }

    private fun Throwable.signInError() = when (this) {
        is IllegalArgumentException -> GitLabSignInError.INVALID_INPUT
        is GitLabInvalidCallbackException -> GitLabSignInError.INVALID_CALLBACK
        else -> GitLabSignInError.REQUEST_FAILED
    }

    class Factory(private val repository: GitLabAuthRepository, private val clientId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GitLabAccountsViewModel::class.java))
            return GitLabAccountsViewModel(repository, clientId) as T
        }
    }
}
