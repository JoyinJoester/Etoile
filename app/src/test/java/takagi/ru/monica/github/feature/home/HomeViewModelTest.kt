package takagi.ru.monica.github.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubSession
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun repeatedSessionUpdatesDoNotReloadTheSameAccount() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        val session = signedIn(1, "alice")
        viewModel.onSessionChanged(session)
        runCurrent()
        viewModel.onSessionChanged(session.copy(account = session.account.copy(followers = 2)))
        assertEquals(1, repository.requests.size)

        repository.requests.single().succeed(12)
        runCurrent()
        viewModel.onSessionChanged(session)
        viewModel.loadContributions("alice")
        runCurrent()
        assertEquals(1, repository.requests.size)
        assertEquals(12, viewModel.contributionsState.value.calendar?.totalContributions)
    }

    @Test
    fun switchingAccountsClearsCachedCalendarBeforeLoadingTheNewAccount() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.requests[0].succeed(12)
        runCurrent()

        viewModel.onSessionChanged(signedIn(2, "bob"))
        assertNull(viewModel.contributionsState.value.calendar)
        assertTrue(viewModel.contributionsState.value.isLoading)
        assertEquals("bob", viewModel.contributionsState.value.login)
        runCurrent()
        assertEquals(listOf("alice", "bob"), repository.requests.map { it.login })
        repository.requests[1].succeed(34)
        runCurrent()
        assertEquals(34, viewModel.contributionsState.value.calendar?.totalContributions)
    }

    @Test
    fun lateResponseFromPreviousAccountCannotReplaceTheNewCalendar() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.onSessionChanged(signedIn(2, "bob"))
        runCurrent()
        repository.requests[1].succeed(34)
        runCurrent()
        repository.requests[0].succeed(12)
        runCurrent()

        assertEquals("bob", viewModel.contributionsState.value.login)
        assertEquals(34, viewModel.contributionsState.value.calendar?.totalContributions)
        assertFalse(viewModel.contributionsState.value.isLoading)
        assertFalse(viewModel.contributionsState.value.hasError)
    }

    @Test
    fun logoutClearsStateAndRejectsLateResponsesAndPreviousScreenCallbacks() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.onSessionChanged(GithubSession.SignedOut)
        assertEquals(HomeContributionsState(), viewModel.contributionsState.value)

        repository.requests[0].succeed(12)
        runCurrent()
        viewModel.loadContributions("alice")
        viewModel.retryLoadContributions("alice")
        runCurrent()
        assertEquals(HomeContributionsState(), viewModel.contributionsState.value)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun retryIgnoresLateFailureFromTheRequestItReplaced() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.retryLoadContributions("alice")
        runCurrent()
        repository.requests[1].succeed(34)
        runCurrent()
        repository.requests[0].fail()
        runCurrent()

        assertEquals(34, viewModel.contributionsState.value.calendar?.totalContributions)
        assertFalse(viewModel.contributionsState.value.hasError)
        assertFalse(viewModel.contributionsState.value.isLoading)
    }

    @Test
    fun failedCalendarCanBeRetriedForTheCurrentAccount() = runTest(dispatcher) {
        val repository = ControlledRepository()
        val viewModel = HomeViewModel(repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.requests[0].fail()
        runCurrent()
        assertTrue(viewModel.contributionsState.value.hasError)

        viewModel.retryLoadContributions("alice")
        assertTrue(viewModel.contributionsState.value.isLoading)
        assertFalse(viewModel.contributionsState.value.hasError)
        runCurrent()
        repository.requests[1].succeed(34)
        runCurrent()
        assertEquals(34, viewModel.contributionsState.value.calendar?.totalContributions)
    }

    private class ControlledRepository : GithubContributionsRepository {
        val requests = mutableListOf<Request>()

        // Deliberately complete even after cancellation, like a callback-based repository can do.
        override suspend fun getContributionCalendar(username: String): Result<GithubContributionCalendar> =
            suspendCoroutine { requests += Request(username, it) }
    }

    private class Request(
        val login: String,
        private val continuation: Continuation<Result<GithubContributionCalendar>>
    ) {
        fun succeed(count: Int) = continuation.resume(Result.success(GithubContributionCalendar(emptyList(), count)))
        fun fail() = continuation.resume(Result.failure(IllegalStateException("offline")))
    }

    private fun signedIn(id: Long, login: String) = GithubSession.SignedIn(
        GithubAccount(id, login, login, null, "https://avatars.example/$login", "https://github.com/$login", 1, 1, 1)
    )
}
