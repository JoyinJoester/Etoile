package takagi.ru.monica.github.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.AwaitGithubDeviceAuthorizationUseCase
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubAuthRepository
import takagi.ru.monica.github.domain.GithubDeviceAccessToken
import takagi.ru.monica.github.domain.GithubDeviceAuthRepository
import takagi.ru.monica.github.domain.GithubDeviceAuthorization
import takagi.ru.monica.github.domain.GithubDevicePollResult
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubWebAuthRepository
import takagi.ru.monica.github.domain.GithubWebAuthorization
import takagi.ru.monica.github.domain.UnavailableGithubWebAuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class GithubSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deviceFlowMovesFromRequestingToWaitingWithoutExposingDeviceCode() = runTest(dispatcher) {
        val startResult = CompletableDeferred<Result<GithubDeviceAuthorization>>()
        val deviceRepository = FakeDeviceAuthRepository(start = { startResult.await() })
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()

        assertEquals(GithubDeviceSignInUiState.Requesting, viewModel.state.value.deviceSignIn)

        startResult.complete(Result.success(authorization()))
        runCurrent()

        assertEquals(
            GithubDeviceSignInUiState.Waiting(
                userCode = "ABCD-EFGH",
                verificationUri = "https://github.com/login/device",
                expiresAtEpochMillis = 60_000L
            ),
            viewModel.state.value.deviceSignIn
        )
        assertFalse(viewModel.state.value.toString().contains(DEVICE_CODE))

        viewModel.onAction(GithubSessionAction.ClearForm)
        runCurrent()
    }

    @Test
    fun browserFlowOpensGithubAndCompletesSignInFromValidatedCallback() = runTest(dispatcher) {
        val webRepository = FakeWebAuthRepository()
        val authRepository = FakeAuthRepository()
        val viewModel = viewModel(authRepository = authRepository, webRepository = webRepository)
        runCurrent()

        assertEquals(GithubBrowserSignInUiState.Idle, viewModel.state.value.browserSignIn)
        viewModel.onAction(GithubSessionAction.StartBrowserSignIn)

        val opening = viewModel.state.value.browserSignIn as GithubBrowserSignInUiState.Opening
        assertEquals(AUTHORIZATION_URL, opening.authorizationUrl)
        viewModel.onAction(GithubSessionAction.BrowserSignInOpened)
        assertTrue(viewModel.state.value.browserSignIn is GithubBrowserSignInUiState.Waiting)

        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()

        assertEquals(listOf(CALLBACK_URL), webRepository.callbacks)
        assertEquals(listOf(OAUTH_TOKEN), authRepository.signInTokens)
        assertEquals(GithubBrowserSignInUiState.Idle, viewModel.state.value.browserSignIn)
        assertEquals(1L, viewModel.state.value.signInCompletionVersion)
    }

    @Test
    fun failedAdditionalAccountDoesNotReportLoginSuccessWhenThePreviousSessionReturns() = runTest(dispatcher) {
        val first = account()
        val response = CompletableDeferred<Result<GithubAccount>>()
        val repository = FakeAuthRepository(
            initialSession = GithubSession.SignedIn(first),
            signIn = { response.await() },
            reportValidationLoading = true
        )
        val viewModel = viewModel(authRepository = repository)
        runCurrent()
        val completionBeforeOpeningForm = viewModel.state.value.signInCompletionVersion

        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()
        assertEquals(GithubSession.Loading, viewModel.state.value.session)

        response.complete(Result.failure(IllegalStateException("Invalid credential")))
        runCurrent()

        assertEquals(GithubSession.SignedIn(first), viewModel.state.value.session)
        assertEquals(completionBeforeOpeningForm, viewModel.state.value.signInCompletionVersion)
        assertEquals(GithubSignInError.REQUEST_FAILED, viewModel.state.value.signInError)
        assertEquals(PERSONAL_TOKEN, viewModel.state.value.tokenInput)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun clearingTokenSignInCancelsTheRequestAndDoesNotSignInLater() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<GithubAccount>>()
        var requestCancelled = false
        val repository = FakeAuthRepository(signIn = {
            try {
                response.await()
            } finally {
                requestCancelled = !currentCoroutineContext().isActive
            }
        })
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()
        assertTrue(viewModel.state.value.isSubmitting)
        viewModel.onAction(GithubSessionAction.ClearForm)
        runCurrent()
        response.complete(Result.success(account()))
        runCurrent()

        assertTrue(requestCancelled)
        assertEquals(GithubSession.SignedOut, viewModel.state.value.session)
        assertEquals(0L, viewModel.state.value.signInCompletionVersion)
        assertEquals("", viewModel.state.value.tokenInput)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.signInError)
    }

    @Test
    fun lateCancelledTokenResponseCannotResetAnIntentionalRetry() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<Result<GithubAccount>>()
        val retryResponse = CompletableDeferred<Result<GithubAccount>>()
        val second = account(id = 2, login = "octocat")
        val repository = FakeAuthRepository(signIn = { token ->
            if (token == PERSONAL_TOKEN) withContext(NonCancellable) { oldResponse.await() }
            else retryResponse.await()
        })
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()
        viewModel.onAction(GithubSessionAction.ClearForm)
        viewModel.onAction(GithubSessionAction.TokenChanged(OAUTH_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()

        oldResponse.complete(Result.success(account()))
        runCurrent()
        assertTrue(viewModel.state.value.isSubmitting)
        assertEquals(OAUTH_TOKEN, viewModel.state.value.tokenInput)
        assertEquals(0L, viewModel.state.value.signInCompletionVersion)
        assertNull(viewModel.state.value.signInError)

        retryResponse.complete(Result.success(second))
        runCurrent()
        assertEquals(GithubSession.SignedIn(second), viewModel.state.value.session)
        assertEquals(1L, viewModel.state.value.signInCompletionVersion)
        assertEquals("", viewModel.state.value.tokenInput)
        assertFalse(viewModel.state.value.isSubmitting)

        viewModel.onAction(GithubSessionAction.ClearForm)
        assertEquals(1L, viewModel.state.value.signInCompletionVersion)
    }

    @Test
    fun clearingBrowserExchangeIgnoresALateTokenAndLaterCallbacks() = runTest(dispatcher) {
        val exchangeResponse = CompletableDeferred<Result<GithubDeviceAccessToken>>()
        val webRepository = FakeWebAuthRepository(exchange = {
            withContext(NonCancellable) { exchangeResponse.await() }
        })
        val authRepository = FakeAuthRepository()
        val viewModel = viewModel(authRepository = authRepository, webRepository = webRepository)
        runCurrent()
        viewModel.onAction(GithubSessionAction.StartBrowserSignIn)
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()
        assertEquals(GithubBrowserSignInUiState.Verifying, viewModel.state.value.browserSignIn)

        viewModel.onAction(GithubSessionAction.ClearForm)
        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        exchangeResponse.complete(Result.success(GithubDeviceAccessToken(OAUTH_TOKEN, "bearer", setOf("repo"))))
        runCurrent()
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()

        assertEquals(listOf(CALLBACK_URL), webRepository.callbacks)
        assertTrue(authRepository.signInTokens.isEmpty())
        assertEquals(0L, viewModel.state.value.signInCompletionVersion)
        assertEquals(PERSONAL_TOKEN, viewModel.state.value.tokenInput)
        assertEquals(GithubBrowserSignInUiState.Idle, viewModel.state.value.browserSignIn)
    }

    @Test
    fun duplicateBrowserCallbacksShareOneExchangeAndOneSignIn() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<GithubDeviceAccessToken>>()
        val webRepository = FakeWebAuthRepository(exchange = { response.await() })
        val repository = FakeAuthRepository()
        val viewModel = viewModel(authRepository = repository, webRepository = webRepository)
        runCurrent()
        viewModel.onAction(GithubSessionAction.StartBrowserSignIn)
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()

        assertEquals(listOf(CALLBACK_URL), webRepository.callbacks)
        response.complete(Result.success(GithubDeviceAccessToken(OAUTH_TOKEN, "bearer", setOf("repo"))))
        runCurrent()
        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()

        assertEquals(listOf(OAUTH_TOKEN), repository.signInTokens)
        assertEquals(listOf(CALLBACK_URL), webRepository.callbacks)
        assertEquals(1L, viewModel.state.value.signInCompletionVersion)
    }

    @Test
    fun callbackAfterProcessRecreationCanStillCompletePersistedAuthorization() = runTest(dispatcher) {
        val webRepository = FakeWebAuthRepository()
        val repository = FakeAuthRepository()
        val viewModel = viewModel(authRepository = repository, webRepository = webRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.BrowserSignInCallback(CALLBACK_URL))
        runCurrent()

        assertEquals(listOf(OAUTH_TOKEN), repository.signInTokens)
        assertEquals(1L, viewModel.state.value.signInCompletionVersion)
    }

    @Test
    fun signingOutCancelsPendingCredentialValidationInsteadOfSilentlyIgnoringTheAction() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<GithubAccount>>()
        val repository = FakeAuthRepository(
            initialSession = GithubSession.SignedIn(account()),
            signIn = { response.await() }
        )
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()
        viewModel.onAction(GithubSessionAction.SignOut)
        runCurrent()
        response.complete(Result.success(account(id = 2, login = "octocat")))
        runCurrent()

        assertEquals(GithubSession.SignedOut, viewModel.state.value.session)
        assertFalse(viewModel.state.value.isSubmitting)
        assertFalse(viewModel.state.value.isAccountActionRunning)
        assertEquals(0L, viewModel.state.value.signInCompletionVersion)
    }

    @Test
    fun browserActionFallsBackToDeviceFlowWhenWebOauthIsNotConfigured() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository()
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartBrowserSignIn)
        runCurrent()

        assertEquals(1, deviceRepository.startCount)
        assertTrue(viewModel.state.value.deviceSignIn is GithubDeviceSignInUiState.Waiting)
        viewModel.onAction(GithubSessionAction.ClearForm)
        runCurrent()
    }

    @Test
    fun pendingAndSlowDownEventuallyValidateTheAuthorizedToken() = runTest(dispatcher) {
        val token = GithubDeviceAccessToken(OAUTH_TOKEN, "bearer", setOf("repo"))
        val deviceRepository = FakeDeviceAuthRepository(
            polls = ArrayDeque(
                listOf(
                    GithubDevicePollResult.Pending,
                    GithubDevicePollResult.SlowDown,
                    GithubDevicePollResult.Authorized(token)
                )
            )
        )
        val authRepository = FakeAuthRepository()
        val viewModel = viewModel(authRepository, deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()
        assertTrue(viewModel.state.value.deviceSignIn is GithubDeviceSignInUiState.Waiting)

        advanceTimeBy(5_000L)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(listOf(OAUTH_TOKEN), authRepository.signInTokens)
        assertEquals(3, deviceRepository.pollCount)
        assertEquals(GithubDeviceSignInUiState.Idle, viewModel.state.value.deviceSignIn)
        assertTrue(viewModel.state.value.session is GithubSession.SignedIn)
    }

    @Test
    fun deniedDeviceAuthorizationShowsARecoverableDeviceError() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository(
            polls = ArrayDeque(listOf(GithubDevicePollResult.Denied))
        )
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(
            GithubDeviceSignInUiState.Failed(GithubDeviceSignInError.DENIED),
            viewModel.state.value.deviceSignIn
        )
    }

    @Test
    fun expiredDeviceAuthorizationShowsARecoverableDeviceError() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository(
            polls = ArrayDeque(listOf(GithubDevicePollResult.Expired))
        )
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(
            GithubDeviceSignInUiState.Failed(GithubDeviceSignInError.EXPIRED),
            viewModel.state.value.deviceSignIn
        )
    }

    @Test
    fun clearingTheFormCancelsPollingAndRemovesShortLivedAuthorizationData() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository(
            polls = ArrayDeque(listOf(GithubDevicePollResult.Pending))
        )
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()
        viewModel.onAction(GithubSessionAction.ClearForm)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, deviceRepository.pollCount)
        assertEquals(GithubDeviceSignInUiState.Idle, viewModel.state.value.deviceSignIn)
        assertEquals("", viewModel.state.value.tokenInput)
    }

    @Test
    fun personalAccessTokenSignInCancelsAnActiveDeviceFlow() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository(
            polls = ArrayDeque(listOf(GithubDevicePollResult.Pending))
        )
        val authRepository = FakeAuthRepository()
        val viewModel = viewModel(authRepository, deviceRepository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()
        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))
        viewModel.onAction(GithubSessionAction.SignIn)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(listOf(PERSONAL_TOKEN), authRepository.signInTokens)
        assertEquals(0, deviceRepository.pollCount)
        assertEquals(GithubDeviceSignInUiState.Idle, viewModel.state.value.deviceSignIn)
    }

    @Test
    fun unconfiguredDeviceFlowFallsBackWithoutStartingANetworkRequest() = runTest(dispatcher) {
        val deviceRepository = FakeDeviceAuthRepository(isConfigured = false)
        val viewModel = viewModel(deviceRepository = deviceRepository)
        runCurrent()

        assertEquals(GithubDeviceSignInUiState.Unavailable, viewModel.state.value.deviceSignIn)

        viewModel.onAction(GithubSessionAction.StartDeviceSignIn)
        runCurrent()

        assertEquals(0, deviceRepository.startCount)
    }

    @Test
    fun sessionUiStateStringRedactsPersonalAccessTokenInput() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(GithubSessionAction.TokenChanged(PERSONAL_TOKEN))

        assertFalse(viewModel.state.value.toString().contains(PERSONAL_TOKEN))
    }

    @Test
    fun savedAccountsAreExposedWithoutCredentialData() = runTest(dispatcher) {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val repository = FakeAuthRepository(savedAccounts = listOf(first, second))
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        assertEquals(listOf(first, second), viewModel.state.value.accounts)
        assertFalse(viewModel.state.value.toString().contains(PERSONAL_TOKEN))
    }

    @Test
    fun failedAccountSwitchKeepsCurrentSessionAndShowsRecoverableError() = runTest(dispatcher) {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val switchResult = CompletableDeferred<Result<GithubAccount>>()
        val repository = FakeAuthRepository(
            initialSession = GithubSession.SignedIn(first),
            savedAccounts = listOf(first, second),
            switchAccount = { switchResult.await() }
        )
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.SwitchAccount(second.id))
        runCurrent()
        assertTrue(viewModel.state.value.isAccountActionRunning)

        switchResult.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertFalse(viewModel.state.value.isAccountActionRunning)
        assertTrue(viewModel.state.value.accountActionError)
        assertEquals(GithubSession.SignedIn(first), viewModel.state.value.session)
        assertEquals(listOf(second.id), repository.switchAccountIds)
    }

    @Test
    fun removingSavedAccountUsesRepositoryAndUpdatesAccountList() = runTest(dispatcher) {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val repository = FakeAuthRepository(
            initialSession = GithubSession.SignedIn(first),
            savedAccounts = listOf(first, second)
        )
        val viewModel = viewModel(authRepository = repository)
        runCurrent()

        viewModel.onAction(GithubSessionAction.RemoveAccount(second.id))
        runCurrent()

        assertEquals(listOf(second.id), repository.removedAccountIds)
        assertEquals(listOf(first), viewModel.state.value.accounts)
        assertFalse(viewModel.state.value.isAccountActionRunning)
        assertFalse(viewModel.state.value.accountActionError)
    }

    private fun TestScope.viewModel(
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        deviceRepository: FakeDeviceAuthRepository = FakeDeviceAuthRepository(),
        webRepository: GithubWebAuthRepository = UnavailableGithubWebAuthRepository
    ): GithubSessionViewModel = GithubSessionViewModel(
        repository = authRepository,
        deviceAuthRepository = deviceRepository,
        webAuthRepository = webRepository,
        awaitDeviceAuthorization = AwaitGithubDeviceAuthorizationUseCase(
            repository = deviceRepository,
            nowEpochMillis = { testScheduler.currentTime },
            delayMillis = { delay(it) }
        )
    )

    private class FakeAuthRepository(
        initialSession: GithubSession = GithubSession.SignedOut,
        savedAccounts: List<GithubAccount> = emptyList(),
        signIn: suspend (String) -> Result<GithubAccount> = { Result.success(account()) },
        private val reportValidationLoading: Boolean = false,
        switchAccount: suspend (Long) -> Result<GithubAccount> = { accountId ->
            Result.success(account(id = accountId, login = "account-$accountId"))
        },
        removeAccount: suspend (Long) -> Result<Unit> = { Result.success(Unit) }
    ) : GithubAuthRepository {
        private val signInResult = signIn
        private val switchAccountResult = switchAccount
        private val removeAccountResult = removeAccount
        private val mutableSession = MutableStateFlow(initialSession)
        override val session: StateFlow<GithubSession> = mutableSession
        private val mutableAccounts = MutableStateFlow(savedAccounts)
        override val accounts: StateFlow<List<GithubAccount>> = mutableAccounts
        val signInTokens = mutableListOf<String>()
        val switchAccountIds = mutableListOf<Long>()
        val removedAccountIds = mutableListOf<Long>()

        override suspend fun restore(): Result<Unit> = Result.success(Unit)

        override suspend fun signInWithToken(token: String): Result<GithubAccount> {
            signInTokens += token
            val previousSession = mutableSession.value
            if (reportValidationLoading) mutableSession.value = GithubSession.Loading
            val result = signInResult(token)
            // Model a repository that rejects cancelled writes but can still
            // return a delayed result from a non-cooperative transport.
            if (currentCoroutineContext().isActive) {
                mutableSession.value = result.fold(
                    onSuccess = { GithubSession.SignedIn(it) },
                    onFailure = { previousSession }
                )
            }
            return result
        }

        override suspend fun switchAccount(accountId: Long): Result<GithubAccount> {
            switchAccountIds += accountId
            return switchAccountResult(accountId).onSuccess { account ->
                mutableSession.value = GithubSession.SignedIn(account)
            }
        }

        override suspend fun removeAccount(accountId: Long): Result<Unit> {
            removedAccountIds += accountId
            return removeAccountResult(accountId).onSuccess {
                mutableAccounts.value = mutableAccounts.value.filterNot { account -> account.id == accountId }
                if ((mutableSession.value as? GithubSession.SignedIn)?.account?.id == accountId) {
                    mutableSession.value = GithubSession.SignedOut
                }
            }
        }

        override suspend fun signOut() {
            mutableSession.value = GithubSession.SignedOut
        }
    }

    private class FakeDeviceAuthRepository(
        override val isConfigured: Boolean = true,
        private val start: suspend () -> Result<GithubDeviceAuthorization> = {
            Result.success(authorization())
        },
        private val polls: ArrayDeque<GithubDevicePollResult> = ArrayDeque()
    ) : GithubDeviceAuthRepository {
        var startCount = 0
        var pollCount = 0

        override suspend fun start(): Result<GithubDeviceAuthorization> {
            startCount += 1
            return start.invoke()
        }

        override suspend fun poll(deviceCode: String): Result<GithubDevicePollResult> {
            assertEquals(DEVICE_CODE, deviceCode)
            pollCount += 1
            return Result.success(polls.removeFirst())
        }
    }

    private class FakeWebAuthRepository(
        private val exchange: suspend () -> Result<GithubDeviceAccessToken> = {
            Result.success(GithubDeviceAccessToken(OAUTH_TOKEN, "bearer", setOf("repo")))
        }
    ) : GithubWebAuthRepository {
        override val isConfigured: Boolean = true
        val callbacks = mutableListOf<String>()
        override fun isCallbackUrl(url: String): Boolean = url.startsWith("etoile://oauth")
        override fun start(): Result<GithubWebAuthorization> =
            Result.success(GithubWebAuthorization(AUTHORIZATION_URL))
        override suspend fun exchangeCallback(url: String): Result<GithubDeviceAccessToken> {
            callbacks += url
            return exchange()
        }
        override fun cancel() = Unit
    }

    private companion object {
        const val DEVICE_CODE = "1234567890123456789012345678901234567890"
        const val OAUTH_TOKEN = "gho_123456789012345678901234567890"
        const val PERSONAL_TOKEN = "github_pat_123456789012345678901234567890"
        const val AUTHORIZATION_URL = "https://github.com/login/oauth/authorize?state=redacted"
        const val CALLBACK_URL = "etoile://oauth?code=temporary-code&state=redacted"

        fun authorization() = GithubDeviceAuthorization(
            deviceCode = DEVICE_CODE,
            userCode = "ABCD-EFGH",
            verificationUri = "https://github.com/login/device",
            expiresAtEpochMillis = 60_000L,
            intervalSeconds = 5
        )

        fun account(id: Long = 1, login: String = "joyins") = GithubAccount(
            id = id,
            login = login,
            name = login.replaceFirstChar(Char::uppercase),
            bio = null,
            avatarUrl = "https://github.com/$login.png",
            htmlUrl = "https://github.com/$login",
            publicRepositories = 1,
            followers = 1,
            following = 1
        )
    }
}
