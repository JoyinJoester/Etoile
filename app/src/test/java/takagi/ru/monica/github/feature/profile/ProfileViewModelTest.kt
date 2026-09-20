package takagi.ru.monica.github.feature.profile

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
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubProfileAchievement
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySettings
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun switchingAccountsClearsAgeAndContentBeforeNewRequestsFinish() = runTest(dispatcher) {
        val repository = ControlledRepositories()
        val viewModel = ProfileViewModel(repository, repository, repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.users[0].complete(publicUser("alice", LocalDate.now().minusYears(5)))
        repository.calendars[0].complete(calendar(120))
        repository.readmes[0].complete("# Alice")
        runCurrent()
        assertTrue(viewModel.state.value.unlocked(GithubProfileAchievement.OPEN_SOURCE_VETERAN))

        viewModel.onSessionChanged(signedIn(2, "bob"))
        assertEquals("bob", viewModel.state.value.login)
        assertNull(viewModel.state.value.calendar)
        assertNull(viewModel.state.value.readme)
        assertFalse(viewModel.state.value.unlocked(GithubProfileAchievement.OPEN_SOURCE_VETERAN))
        runCurrent()
        repository.calendars[1].complete(calendar(2))
        repository.readmes[1].complete("# Bob")
        runCurrent()
        // Bob's calendar must not reuse Alice's age while Bob's public profile is still pending.
        assertFalse(viewModel.state.value.unlocked(GithubProfileAchievement.OPEN_SOURCE_VETERAN))
        repository.users[1].fail()
        runCurrent()
        assertFalse(viewModel.state.value.unlocked(GithubProfileAchievement.OPEN_SOURCE_VETERAN))
        assertEquals(2, viewModel.state.value.calendar?.totalContributions)
    }

    @Test
    fun lateResponsesFromAllPreviousAccountRequestsCannotOverwriteCurrentProfile() = runTest(dispatcher) {
        val repository = ControlledRepositories()
        val viewModel = ProfileViewModel(repository, repository, repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.onSessionChanged(signedIn(2, "bob"))
        runCurrent()
        repository.users[1].complete(publicUser("bob", LocalDate.now().minusDays(30)))
        repository.calendars[1].complete(calendar(2))
        repository.readmes[1].complete("# Bob")
        runCurrent()
        val current = viewModel.state.value

        repository.users[0].complete(publicUser("alice", LocalDate.now().minusYears(5)))
        repository.calendars[0].complete(calendar(120))
        repository.readmes[0].complete("# Alice")
        runCurrent()
        assertEquals(current, viewModel.state.value)
        assertEquals("bob", viewModel.state.value.login)
        assertFalse(viewModel.state.value.unlocked(GithubProfileAchievement.OPEN_SOURCE_VETERAN))
    }

    @Test
    fun logoutClearsProfileAndLateCallbacksCannotRestoreIt() = runTest(dispatcher) {
        val repository = ControlledRepositories()
        val viewModel = ProfileViewModel(repository, repository, repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        viewModel.onSessionChanged(GithubSession.SignedOut)
        assertEquals(ProfileUiState(), viewModel.state.value)

        repository.users[0].complete(publicUser("alice", LocalDate.now().minusYears(5)))
        repository.calendars[0].complete(calendar(120))
        repository.readmes[0].complete("# Alice")
        runCurrent()
        viewModel.onAction(ProfileAction.Retry)
        viewModel.onAction(ProfileAction.Refresh)
        runCurrent()
        assertEquals(ProfileUiState(), viewModel.state.value)
        assertEquals(1, repository.users.size)
        assertEquals(1, repository.calendars.size)
        assertEquals(1, repository.readmes.size)
    }

    @Test
    fun refreshKeepsNewContentWhenCancelledRequestsReturnLateFailures() = runTest(dispatcher) {
        val repository = ControlledRepositories()
        val viewModel = ProfileViewModel(repository, repository, repository)
        viewModel.onSessionChanged(signedIn(1, "alice"))
        runCurrent()
        repository.users[0].complete(publicUser("alice", LocalDate.now().minusYears(5)))
        viewModel.onAction(ProfileAction.Refresh)
        runCurrent()
        repository.calendars[1].complete(calendar(121))
        repository.readmes[1].complete("# Updated profile")
        runCurrent()
        repository.calendars[0].fail()
        repository.readmes[0].fail()
        runCurrent()

        assertEquals(121, viewModel.state.value.calendar?.totalContributions)
        assertEquals("# Updated profile", viewModel.state.value.readme)
        assertFalse(viewModel.state.value.calendarError)
        assertFalse(viewModel.state.value.isLoadingCalendar)
        assertFalse(viewModel.state.value.isLoadingReadme)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun updatedAccountStatisticsRecomputeAchievementsWithoutReloadingContent() = runTest(dispatcher) {
        val repository = ControlledRepositories()
        val viewModel = ProfileViewModel(repository, repository, repository)
        val session = signedIn(1, "alice")
        viewModel.onSessionChanged(session)
        runCurrent()
        repository.users[0].complete(publicUser("alice", LocalDate.now().minusYears(5)))
        repository.calendars[0].complete(calendar(120))
        repository.readmes[0].complete("# Alice")
        runCurrent()
        assertFalse(viewModel.state.value.unlocked(GithubProfileAchievement.SOCIAL_BUTTERFLY))

        viewModel.onSessionChanged(session.copy(account = session.account.copy(followers = 50)))
        runCurrent()
        assertTrue(viewModel.state.value.unlocked(GithubProfileAchievement.SOCIAL_BUTTERFLY))
        assertEquals(1, repository.users.size)
        assertEquals(1, repository.calendars.size)
        assertEquals(1, repository.readmes.size)
    }

    private class ControlledRepositories :
        GithubContributionsRepository, GithubRepositoryDetailsRepository, GithubPublicUserRepository {
        val users = mutableListOf<Pending<GithubPublicUser>>()
        val calendars = mutableListOf<Pending<GithubContributionCalendar>>()
        val readmes = mutableListOf<Pending<String?>>()

        // These callbacks deliberately ignore cancellation to verify account ownership at publication time.
        override suspend fun user(login: String): Result<GithubPublicUser> =
            suspendCoroutine { users += Pending(it) }
        override suspend fun getContributionCalendar(username: String): Result<GithubContributionCalendar> =
            suspendCoroutine { calendars += Pending(it) }
        override suspend fun readme(owner: String, name: String, ref: String?): Result<String?> =
            suspendCoroutine { readmes += Pending(it) }

        override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> = error("unused")
        override suspend fun branchProtection(owner: String, name: String, branch: String): Result<GithubBranchProtection?> =
            error("unused")
        override suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>> =
            error("unused")
        override suspend fun updateSettings(owner: String, name: String, edit: GithubRepositorySettingsEdit): Result<GithubRepositorySettings> =
            error("unused")
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubCollaborator>> =
            error("unused")
        override suspend fun setCollaborator(owner: String, name: String, invite: GithubCollaboratorInvite): Result<GithubCollaboratorChange> =
            error("unused")
        override suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit> =
            error("unused")
        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubRepositoryWebhook>> =
            error("unused")
        override suspend fun viewerFollows(login: String): Result<Boolean> = error("unused")
        override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> = error("unused")
        override suspend fun repositories(login: String, page: Int, perPage: Int): Result<GithubPage<GithubRepository>> =
            error("unused")
        override suspend fun connections(
            login: String, kind: GithubUserConnectionKind, page: Int, perPage: Int
        ): Result<GithubPage<GithubUserSummary>> = error("unused")
        override suspend fun blockedUsers(page: Int, perPage: Int): Result<GithubPage<GithubUserSummary>> =
            error("unused")
        override suspend fun viewerBlocks(login: String): Result<Boolean> = error("unused")
        override suspend fun setBlocked(login: String, blocked: Boolean): Result<Unit> = error("unused")
    }

    private class Pending<T>(private val continuation: Continuation<Result<T>>) {
        fun complete(value: T) = continuation.resume(Result.success(value))
        fun fail() = continuation.resume(Result.failure(IllegalStateException("offline")))
    }

    private fun ProfileUiState.unlocked(achievement: GithubProfileAchievement): Boolean =
        achievements.single { it.achievement == achievement }.unlocked

    private fun calendar(total: Int) = GithubContributionCalendar(emptyList(), total)

    private fun publicUser(login: String, createdAt: LocalDate) = GithubPublicUser(
        id = if (login == "alice") 1 else 2,
        login = login,
        name = login,
        bio = null,
        avatarUrl = null,
        htmlUrl = "https://github.com/$login",
        company = null,
        location = null,
        blog = null,
        publicRepositories = 1,
        followers = 1,
        following = 1,
        isHireable = null,
        createdAt = "${createdAt}T00:00:00Z"
    )

    private fun signedIn(id: Long, login: String) = GithubSession.SignedIn(
        GithubAccount(id, login, login, null, "https://avatars.example/$login", "https://github.com/$login", 1, 1, 1)
    )
}
