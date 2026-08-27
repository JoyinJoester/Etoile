package takagi.ru.monica.github.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        deviceRepository: FakeDeviceAuthRepository = FakeDeviceAuthRepository()
    ): GithubSessionViewModel = GithubSessionViewModel(
        repository = authRepository,
        deviceAuthRepository = deviceRepository,
        awaitDeviceAuthorization = AwaitGithubDeviceAuthorizationUseCase(
            repository = deviceRepository,
            nowEpochMillis = { testScheduler.currentTime },
            delayMillis = { delay(it) }
        )
    )

    private class FakeAuthRepository(
        initialSession: GithubSession = GithubSession.SignedOut,
        savedAccounts: List<GithubAccount> = emptyList(),
        switchAccount: suspend (Long) -> Result<GithubAccount> = { accountId ->
            Result.success(account(id = accountId, login = "account-$accountId"))
        },
        removeAccount: suspend (Long) -> Result<Unit> = { Result.success(Unit) }
    ) : GithubAuthRepository {
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
            val account = account()
            mutableSession.value = GithubSession.SignedIn(account)
            return Result.success(account)
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

    private companion object {
        const val DEVICE_CODE = "1234567890123456789012345678901234567890"
        const val OAUTH_TOKEN = "gho_123456789012345678901234567890"
        const val PERSONAL_TOKEN = "github_pat_123456789012345678901234567890"

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
