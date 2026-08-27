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
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubAccount

@OptIn(ExperimentalCoroutinesApi::class)
class PublicUserProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun profileLoadsUserAndRepositories() = runTest(dispatcher) {
        val viewModel = PublicUserProfileViewModel("joyins", FakeRepository())
        advanceUntilIdle()

        assertEquals("Joyin", viewModel.state.value.user?.name)
        assertEquals(listOf("joyins/etoile"), viewModel.state.value.repositories.map { it.fullName })
        assertFalse(viewModel.state.value.userError)
        assertFalse(viewModel.state.value.repositoriesError)
    }

    @Test
    fun loadMoreAppendsRepositoriesAndDeduplicates() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        advanceUntilIdle()

        viewModel.onAction(PublicUserProfileAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("joyins/etoile", "joyins/second"), viewModel.state.value.repositories.map { it.fullName })
        assertEquals(null, viewModel.state.value.nextPage)
    }

    @Test
    fun repositoryFailureExposesRetryableState() = runTest(dispatcher) {
        val repository = FakeRepository(repositoryFailure = true)
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.repositoriesError)

        repository.repositoryFailure = false
        viewModel.onAction(PublicUserProfileAction.RetryRepositories)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.repositoriesError)
        assertEquals(1, viewModel.state.value.repositories.size)
    }

    @Test
    fun signedInViewerLoadsFollowingAndToggleUpdatesProfileCount() = runTest(dispatcher) {
        val repository = FakeRepository(viewerFollowsResult = Result.success(false))
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isFollowing)

        viewModel.onAction(PublicUserProfileAction.ToggleFollowing)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isFollowing == true)
        assertEquals(5, viewModel.state.value.user?.followers)
        assertFalse(viewModel.state.value.followingError)
    }

    @Test
    fun followingFailureKeepsStateAndExposesRetryableError() = runTest(dispatcher) {
        val repository = FakeRepository(
            viewerFollowsResult = Result.success(true),
            setFollowingResult = Result.failure(IllegalStateException("offline"))
        )
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        advanceUntilIdle()
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(PublicUserProfileAction.ToggleFollowing)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isFollowing == true)
        assertEquals(4, viewModel.state.value.user?.followers)
        assertTrue(viewModel.state.value.followingError)
    }

    private class FakeRepository(
        var repositoryFailure: Boolean = false,
        var viewerFollowsResult: Result<Boolean> = Result.success(false),
        var setFollowingResult: Result<Boolean> = Result.success(true)
    ) : GithubPublicUserRepository {
        override suspend fun user(login: String) = Result.success(
            GithubPublicUser(7, login, "Joyin", "Build things", null, "https://github.com/$login", null, null, null, 2, 4, 8, true)
        )

        override suspend fun repositories(login: String, page: Int, perPage: Int): Result<GithubPage<GithubRepository>> {
            if (repositoryFailure) return Result.failure(IllegalStateException("offline"))
            return Result.success(
                if (page == 1) GithubPage(listOf(repository("$login/etoile", 1)), 2)
                else GithubPage(listOf(repository("$login/etoile", 1), repository("$login/second", 2)), null)
            )
        }

        override suspend fun connections(
            login: String,
            kind: GithubUserConnectionKind,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubUserSummary>> = Result.success(GithubPage(emptyList(), null))

        override suspend fun viewerFollows(login: String): Result<Boolean> = viewerFollowsResult

        override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> =
            setFollowingResult.map { following }

        private fun repository(fullName: String, id: Long) = GithubRepository(
            id, fullName.substringAfter('/'), fullName, null, "Kotlin", 1, null, false, "https://github.com/$fullName"
        )
    }

    private fun account() = GithubAccount(
        id = 1,
        login = "alice",
        name = "Alice",
        bio = null,
        avatarUrl = "https://avatars.example/alice",
        htmlUrl = "https://github.com/alice",
        publicRepositories = 1,
        followers = 1,
        following = 2
    )
}
