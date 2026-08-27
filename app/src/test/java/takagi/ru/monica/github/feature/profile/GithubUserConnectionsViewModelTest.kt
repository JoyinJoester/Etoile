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
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary

@OptIn(ExperimentalCoroutinesApi::class)
class GithubUserConnectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstPageAndNextPageAreMergedWithStableLoginDeduplication() = runTest(dispatcher) {
        val viewModel = GithubUserConnectionsViewModel(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWERS,
            repository = FakeRepository()
        )
        advanceUntilIdle()

        viewModel.onAction(GithubUserConnectionsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob", "carol"), viewModel.state.value.users.map { it.login })
        assertEquals(null, viewModel.state.value.nextPage)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun nextPageFailureKeepsUsersAndCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failNextPage = true }
        val viewModel = GithubUserConnectionsViewModel(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWING,
            repository = repository
        )
        advanceUntilIdle()

        viewModel.onAction(GithubUserConnectionsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob"), viewModel.state.value.users.map { it.login })
        assertTrue(viewModel.state.value.error)
        assertEquals(2, viewModel.state.value.nextPage)

        repository.failNextPage = false
        viewModel.onAction(GithubUserConnectionsAction.Retry)
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob", "carol"), viewModel.state.value.users.map { it.login })
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun refreshKeepsExistingUsersVisibleUntilReplacementArrives() = runTest(dispatcher) {
        val viewModel = GithubUserConnectionsViewModel(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWERS,
            repository = FakeRepository()
        )
        advanceUntilIdle()
        val existing = viewModel.state.value.users

        viewModel.onAction(GithubUserConnectionsAction.Refresh)

        assertEquals(existing, viewModel.state.value.users)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun refreshFailureKeepsExistingUsersAndRetryReloadsTheFirstPage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = GithubUserConnectionsViewModel(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWERS,
            repository = repository
        )
        advanceUntilIdle()
        repository.failRefresh = true

        viewModel.onAction(GithubUserConnectionsAction.Refresh)
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob"), viewModel.state.value.users.map { it.login })
        assertTrue(viewModel.state.value.error)
        assertTrue(viewModel.state.value.refreshError)

        repository.failRefresh = false
        viewModel.onAction(GithubUserConnectionsAction.Retry)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshError)
    }

    private class FakeRepository : GithubPublicUserRepository {
        var failNextPage = false
        var failRefresh = false
        private var firstPageRequests = 0

        override suspend fun user(login: String) = Result.success(
            GithubPublicUser(
                7, login, "Joyin", null, null, "https://github.com/$login",
                null, null, null, 0, 0, 0, null
            )
        )

        override suspend fun repositories(
            login: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubRepository>> = Result.success(GithubPage(emptyList(), null))

        override suspend fun connections(
            login: String,
            kind: GithubUserConnectionKind,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubUserSummary>> {
            if (page == 1) {
                firstPageRequests++
                if (firstPageRequests > 1 && failRefresh) {
                    return Result.failure(IllegalStateException("refresh offline"))
                }
            }
            if (page == 2 && failNextPage) {
                return Result.failure(IllegalStateException("offline"))
            }
            return Result.success(
                if (page == 1) {
                    GithubPage(listOf(summary("alice"), summary("bob")), nextPage = 2)
                } else {
                    GithubPage(listOf(summary("bob"), summary("carol")), nextPage = null)
                }
            )
        }

        override suspend fun viewerFollows(login: String): Result<Boolean> = Result.success(false)

        override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> =
            Result.success(following)

        private fun summary(login: String) = GithubUserSummary(
            login = login,
            avatarUrl = null,
            htmlUrl = "https://github.com/$login"
        )
    }
}
