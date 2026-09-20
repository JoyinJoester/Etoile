package takagi.ru.monica.github.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure

@OptIn(ExperimentalCoroutinesApi::class)
class GithubBlockedUsersViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun noSessionMeansNoRequestAndASignInPrompt() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubBlockedUsersViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedOut)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.requiresAuthentication)
        assertEquals(0, repository.listRequests)
    }

    @Test
    fun signingInLoadsTheViewerListOnce() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubBlockedUsersViewModel(repository)

        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()

        assertEquals(1, repository.listRequests)
        assertFalse(viewModel.state.value.requiresAuthentication)
        assertEquals(listOf("spam-bot"), viewModel.state.value.users.map { it.login })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun switchingAccountsReplacesTheListInsteadOfAppending() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubBlockedUsersViewModel(repository)

        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()
        repository.pages = listOf(GithubPage(listOf(blockedSummary("blocked-by-bob")), null))
        viewModel.onSessionChanged(GithubSession.SignedIn(account("bob")))
        advanceUntilIdle()

        assertEquals(2, repository.listRequests)
        assertEquals(listOf("blocked-by-bob"), viewModel.state.value.users.map { it.login })
    }

    @Test
    fun signingOutClearsPrivateData() = runTest(dispatcher) {
        val viewModel = GithubBlockedUsersViewModel(FakeRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.users.isEmpty())

        viewModel.onSessionChanged(GithubSession.SignedOut)

        assertTrue(viewModel.state.value.users.isEmpty())
        assertTrue(viewModel.state.value.requiresAuthentication)
    }

    @Test
    fun loadMoreAppendsAndStopsWithoutAnotherPage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubBlockedUsersViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()

        viewModel.onAction(GithubBlockedUsersAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf("spam-bot", "troll-2"), viewModel.state.value.users.map { it.login })
        assertEquals(null, viewModel.state.value.nextPage)

        viewModel.onAction(GithubBlockedUsersAction.LoadMore)
        advanceUntilIdle()
        assertEquals(2, repository.listRequests)
    }

    @Test
    fun refusalIsClassifiedSoThePageCanNameTheCause() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.failNext = GithubApiException(403)
        val viewModel = GithubBlockedUsersViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)

        repository.failNext = null
        repository.pages = listOf(GithubPage(listOf(blockedSummary("late-bot")), null))
        viewModel.onAction(GithubBlockedUsersAction.Retry)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.error)
        assertEquals(listOf("late-bot"), viewModel.state.value.users.map { it.login })
    }

    @Test
    fun refreshFailureOverVisibleRowsMakesRetryReplayFirstPage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubBlockedUsersViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account("alice")))
        advanceUntilIdle()

        repository.failNext = IllegalStateException("Offline")
        viewModel.onAction(GithubBlockedUsersAction.Refresh)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Network, viewModel.state.value.error)
        assertEquals(listOf("spam-bot"), viewModel.state.value.users.map { it.login })
        assertEquals(listOf(1, 1), repository.requested)

        repository.failNext = null
        viewModel.onAction(GithubBlockedUsersAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf(1, 1, 1), repository.requested)
        assertEquals(null, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    private class FakeRepository : GithubPublicUserRepository {
        var listRequests = 0
        val requested = mutableListOf<Int>()
        var failNext: Throwable? = null
        var pages: List<GithubPage<GithubUserSummary>> = listOf(
            GithubPage(listOf(blockedSummary("spam-bot")), 2),
            GithubPage(listOf(blockedSummary("troll-2")), null)
        )

        override suspend fun blockedUsers(page: Int, perPage: Int): Result<GithubPage<GithubUserSummary>> {
            listRequests++
            requested += page
            failNext?.let { return Result.failure(it) }
            return Result.success(pages.getOrElse(page - 1) { GithubPage(emptyList(), null) })
        }

        override suspend fun user(login: String): Result<GithubPublicUser> = error("unused")
        override suspend fun repositories(
            login: String, page: Int, perPage: Int
        ): Result<GithubPage<GithubRepository>> = error("unused")
        override suspend fun connections(
            login: String, kind: GithubUserConnectionKind, page: Int, perPage: Int
        ): Result<GithubPage<GithubUserSummary>> = error("unused")
        override suspend fun viewerFollows(login: String): Result<Boolean> = error("unused")
        override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> = error("unused")
        override suspend fun viewerBlocks(login: String): Result<Boolean> = error("unused")
        override suspend fun setBlocked(login: String, blocked: Boolean): Result<Unit> = error("unused")
    }

    private fun account(login: String) = GithubAccount(
        id = 1,
        login = login,
        name = null,
        bio = null,
        avatarUrl = "https://avatars.example/$login",
        htmlUrl = "https://github.com/$login",
        publicRepositories = 1,
        followers = 1,
        following = 1
    )
}

private fun blockedSummary(login: String) = GithubUserSummary(
    login, "https://avatars.example/$login", "https://github.com/$login"
)
