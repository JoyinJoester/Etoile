package takagi.ru.monica.gitlab.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.gitlab.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabAccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun coldStartCallbackWaitsForRestorationAndCompletesOnce() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GitLabAccountsViewModel(repository)
        viewModel.completeBrowserSignIn("etoile://gitlab-oauth?code=example")
        viewModel.completeBrowserSignIn("etoile://gitlab-oauth?code=example")
        runCurrent()
        assertEquals(0, repository.callbacks)
        repository.restored.complete(Unit)
        runCurrent()
        assertEquals(1, repository.callbacks)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
    }

    @Test fun cancellationClearsSensitiveInputAndCancelsQueuedSignIn() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GitLabAccountsViewModel(repository)
        viewModel.tokenChanged("glpat-sensitive-input")
        viewModel.signInWithToken()
        runCurrent()
        viewModel.cancelSignIn()
        repository.restored.complete(Unit)
        runCurrent()
        assertEquals(0, repository.tokenSignIns)
        assertEquals("", viewModel.state.value.tokenInput)
        assertFalse(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.toString().contains("sensitive-input"))
    }

    @Test fun writableScopeRequiresOptInAndCannotChangeDuringBrowserAuthorization() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.restored.complete(Unit)
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent()
        assertFalse(viewModel.state.value.allowWrites)
        viewModel.startBrowserSignIn()
        runCurrent()
        assertEquals(listOf(false), repository.requestedWriteScopes)
        viewModel.allowWritesChanged(true)
        assertFalse(viewModel.state.value.allowWrites)
        viewModel.cancelSignIn()
        viewModel.allowWritesChanged(true)
        viewModel.startBrowserSignIn()
        runCurrent()
        assertEquals(listOf(false, true), repository.requestedWriteScopes)
        viewModel.allowWritesChanged(false)
        assertTrue(viewModel.state.value.allowWrites)
    }

    @Test fun pendingStoreFailureDoesNotCrashCancellationOrLeakSensitiveInput() = runTest(dispatcher) {
        val repository = FakeRepository().apply { restored.complete(Unit) }
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent()
        viewModel.startBrowserSignIn()
        runCurrent()
        viewModel.tokenChanged("glpat-sensitive-input")
        repository.cancelFailure = IllegalStateException("Encrypted store unavailable")
        viewModel.cancelSignIn()
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.browserUrl)
        assertEquals("", viewModel.state.value.tokenInput)
        runCurrent()
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        viewModel.tokenChanged("new-sensitive-input")
        viewModel.signInWithToken()
        runCurrent()
        assertFalse(viewModel.state.value.busy)
        assertEquals(0, repository.tokenSignIns)
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        repository.cancelFailure = null
        viewModel.signInWithToken()
        runCurrent()
        assertEquals(1, repository.tokenSignIns)
        assertEquals("", viewModel.state.value.tokenInput)
        assertNull(viewModel.state.value.error)
    }

    @Test fun unexpectedSignInFailureReleasesBusyAndAllowsRetry() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            restored.complete(Unit)
            tokenSignInBlock = { throw IllegalStateException("Storage unavailable") }
        }
        val viewModel = GitLabAccountsViewModel(repository)
        viewModel.signInWithToken()
        runCurrent()
        assertFalse(viewModel.state.value.busy)
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        repository.tokenSignInBlock = null
        viewModel.signInWithToken()
        runCurrent()
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
        assertEquals(2, repository.tokenSignIns)
    }

    @Test fun cancelledLateSignInCannotClearNewTokenOrFinishAnotherOperation() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<GitLabAccount>>()
        val repository = FakeRepository().apply {
            restored.complete(Unit)
            tokenSignInBlock = { withContext(NonCancellable) { pending.await() } }
        }
        val viewModel = GitLabAccountsViewModel(repository)
        viewModel.tokenChanged("old-token")
        viewModel.signInWithToken()
        runCurrent()
        viewModel.cancelSignIn()
        viewModel.tokenChanged("replacement-token")
        pending.complete(Result.success(GitLabAccount(42, "https://gitlab.com/", "alice", "Alice", "")))
        runCurrent()
        assertEquals("replacement-token", viewModel.state.value.tokenInput)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
    }

    @Test fun browserStorageFailuresAreNotReportedAsInvalidInput() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            restored.complete(Unit)
            browserFailure = IllegalStateException("Storage unavailable")
        }
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent()
        viewModel.startBrowserSignIn()
        runCurrent()
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        assertNull(viewModel.state.value.browserUrl)
    }

    @Test fun unexpectedRestoreFailureIsVisibleAndDoesNotPreventNewSignIn() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            restored.complete(Unit)
            restoreFailure = IllegalStateException("Encrypted account read failed")
        }
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent()
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        viewModel.signInWithToken()
        runCurrent()
        assertEquals(1, repository.tokenSignIns)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
    }

    @Test fun cancellationReturnsImmediatelyAndOldPreparationIsCleanedBeforeReplacementStarts() = runTest(dispatcher) {
        val oldCommit = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var pending: String? = null
        var attempt = 0
        val repository = FakeRepository().apply {
            restored.complete(Unit)
            browserBlock = {
                attempt++
                val value = if (attempt == 1) "old" else "new"
                if (value == "old") withContext(NonCancellable) { oldCommit.await() }
                pending = value
                events += "prepared:$value"
                Result.success("https://gitlab.com/oauth/authorize?state=$value")
            }
            cancelBlock = { pending = null; events += "cleared" }
        }
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent()
        viewModel.tokenChanged("sensitive-input")
        viewModel.startBrowserSignIn(); runCurrent()
        assertTrue(viewModel.state.value.busy)
        viewModel.cancelSignIn()
        assertFalse(viewModel.state.value.busy)
        assertEquals("", viewModel.state.value.tokenInput)
        assertNull(viewModel.state.value.browserUrl)
        viewModel.startBrowserSignIn(); runCurrent()
        assertEquals(1, repository.requestedWriteScopes.size)
        oldCommit.complete(Unit); runCurrent()
        assertEquals(listOf("prepared:old", "cleared", "prepared:new"), events)
        assertEquals("new", pending)
        assertEquals("https://gitlab.com/oauth/authorize?state=new", viewModel.state.value.browserUrl)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
    }

    @Test fun cancelledCallbackCannotSignInEvenWhenPendingCleanupFails() = runTest(dispatcher) {
        val repository = FakeRepository().apply { restored.complete(Unit) }
        val viewModel = GitLabAccountsViewModel(repository)
        runCurrent(); viewModel.startBrowserSignIn(); runCurrent()
        repository.cancelFailure = IllegalStateException("Pending storage unavailable")
        viewModel.cancelSignIn(); runCurrent()
        viewModel.completeBrowserSignIn("etoile://gitlab-oauth?code=old-code")
        runCurrent()
        assertEquals(0, repository.callbacks)
        assertEquals(GitLabSignInError.REQUEST_FAILED, viewModel.state.value.error)
        assertNull(viewModel.state.value.browserUrl)
    }

    private class FakeRepository : GitLabAuthRepository {
        override val accounts = MutableStateFlow(GitLabAccounts())
        val restored = CompletableDeferred<Unit>()
        var callbacks = 0
        var tokenSignIns = 0
        var cancelFailure: Exception? = null
        var browserFailure: Exception? = null
        var restoreFailure: Exception? = null
        var tokenSignInBlock: (suspend () -> Result<GitLabAccount>)? = null
        var browserBlock: (suspend () -> Result<String>)? = null
        var cancelBlock: (suspend () -> Unit)? = null
        val requestedWriteScopes = mutableListOf<Boolean>()
        private val account = GitLabAccount(42, "https://gitlab.com/", "alice", "Alice", "")
        override suspend fun restore(): Result<Unit> {
            restored.await(); restoreFailure?.let { throw it }; return Result.success(Unit)
        }
        override suspend fun signInWithToken(instance: String, token: String): Result<GitLabAccount> {
            tokenSignIns++; return tokenSignInBlock?.invoke() ?: Result.success(account)
        }
        override suspend fun startBrowserSignIn(instance: String, clientId: String, allowWrites: Boolean): Result<String> {
            requestedWriteScopes += allowWrites
            browserFailure?.let { throw it }
            return browserBlock?.invoke() ?: Result.success("https://gitlab.com/oauth/authorize")
        }
        override suspend fun completeBrowserSignIn(callback: String): Result<GitLabAccount> {
            callbacks++; return Result.success(account)
        }
        override suspend fun cancelBrowserSignIn() { cancelFailure?.let { throw it }; cancelBlock?.invoke() }
        override suspend fun activate(key: String) = Result.success(Unit)
        override suspend fun remove(key: String) = Result.success(Unit)
    }
}
