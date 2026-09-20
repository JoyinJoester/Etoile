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
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure

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

    @Test
    fun organizationNeverUsesPersonalFollowingEvenWhenSessionArrivesBeforeProfile() = runTest(dispatcher) {
        val repository = FakeRepository(organization = true)
        val viewModel = PublicUserProfileViewModel("Monica-Pass", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(PublicUserProfileAction.ToggleFollowing)
        viewModel.onAction(PublicUserProfileAction.RetryFollowing)
        viewModel.onAction(PublicUserProfileAction.RetryCalendar)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.user?.isOrganization == true)
        assertEquals(0, repository.followingReads)
        assertEquals(0, repository.followingWrites)
        assertFalse(viewModel.state.value.isLoadingCalendar)
        assertFalse(viewModel.state.value.calendarError)
    }

    @Test
    fun deniedFollowPreservesCountAndExplainsAccessFailure() = runTest(dispatcher) {
        val repository = FakeRepository(setFollowingResult = Result.failure(
            takagi.ru.monica.github.data.GithubApiException(403)))
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(PublicUserProfileAction.ToggleFollowing)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isFollowing)
        assertEquals(4, viewModel.state.value.user?.followers)
        assertTrue(viewModel.state.value.followingAccessError)
    }

    @Test
    fun throttledFollowFailureReportsAnErrorWithoutAskingToSignInAgain() = runTest(dispatcher) {
        val repository = FakeRepository(setFollowingResult = Result.failure(
            takagi.ru.monica.github.data.GithubApiException(403, rateLimited = true)))
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(PublicUserProfileAction.ToggleFollowing)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.followingError)
        assertFalse(viewModel.state.value.followingAccessError)
    }

    @Test
    fun blockIsConfirmedByReadingTheStateBack() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isBlocked)
        val readsBeforeWrite = repository.blockedReads

        viewModel.onAction(PublicUserProfileAction.ToggleBlocked)
        advanceUntilIdle()

        assertTrue(repository.blocked)
        assertEquals(true, viewModel.state.value.isBlocked)
        assertEquals(1, repository.blockedWrites)
        assertTrue(repository.blockedReads > readsBeforeWrite)
        assertEquals(null, viewModel.state.value.blockedFailure)
    }

    @Test
    fun refusedBlockKeepsKnownStateAndExplainsWhy() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.blockWriteFailure = takagi.ru.monica.github.data.GithubApiException(403)
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(PublicUserProfileAction.ToggleBlocked)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isBlocked)
        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.blockedFailure)
        assertFalse(repository.blocked)
        assertFalse(viewModel.state.value.isUpdatingBlocked)
    }

    @Test
    fun blockedOrganizationCanUnblockButIsNeverOfferedABlock() = runTest(dispatcher) {
        val repository = FakeRepository(organization = true)
        repository.blocked = true
        val viewModel = PublicUserProfileViewModel("Monica-Pass", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.isBlocked)

        viewModel.onAction(PublicUserProfileAction.ToggleBlocked)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isBlocked)
        assertEquals(1, repository.blockedWrites)

        viewModel.onAction(PublicUserProfileAction.ToggleBlocked)
        advanceUntilIdle()
        assertEquals(1, repository.blockedWrites)
        assertEquals(false, viewModel.state.value.isBlocked)
    }

    @Test
    fun failedBlockReadLeavesNoControlAndOffersRetry() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.blockReadFailure = takagi.ru.monica.github.data.GithubApiException(401)
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.isBlocked)
        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.blockedFailure)

        repository.blockReadFailure = null
        viewModel.onAction(PublicUserProfileAction.RetryBlocked)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isBlocked)
        assertEquals(null, viewModel.state.value.blockedFailure)
    }

    @Test
    fun signedOutProfileNeverAsksAboutBlocks() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = PublicUserProfileViewModel("joyins", repository)
        advanceUntilIdle()

        assertEquals(0, repository.blockedReads)
        assertEquals(null, viewModel.state.value.isBlocked)
        viewModel.onAction(PublicUserProfileAction.ToggleBlocked)
        advanceUntilIdle()
        assertEquals(0, repository.blockedWrites)
    }

    private class FakeRepository(
        var repositoryFailure: Boolean = false,
        val organization: Boolean = false,
        var viewerFollowsResult: Result<Boolean> = Result.success(false),
        var setFollowingResult: Result<Boolean> = Result.success(true)
    ) : GithubPublicUserRepository {
        var followingReads = 0
        var followingWrites = 0
        var blockedReads = 0
        var blockedWrites = 0
        var blocked = false
        var blockReadFailure: Throwable? = null
        var blockWriteFailure: Throwable? = null
        override suspend fun user(login: String) = Result.success(
            GithubPublicUser(7, login, "Joyin", "Build things", null, "https://github.com/$login", null, null, null, 2, 4, 8, true, type = if (organization) "Organization" else "User")
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

        override suspend fun viewerFollows(login: String): Result<Boolean> {
            followingReads++
            return viewerFollowsResult
        }

        override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> {
            followingWrites++
            return setFollowingResult.map { following }
        }

        override suspend fun blockedUsers(page: Int, perPage: Int): Result<GithubPage<GithubUserSummary>> =
            error("unused")

        override suspend fun viewerBlocks(login: String): Result<Boolean> {
            blockedReads++
            return blockReadFailure?.let { Result.failure<Boolean>(it) } ?: Result.success(blocked)
        }

        override suspend fun setBlocked(login: String, blocked: Boolean): Result<Unit> {
            blockedWrites++
            blockWriteFailure?.let { return Result.failure(it) }
            this.blocked = blocked
            return Result.success(Unit)
        }

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
