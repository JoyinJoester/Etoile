package takagi.ru.monica.github.feature.repository

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
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositoryViewerState
import takagi.ru.monica.github.domain.GithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialLoadPublishesMetadataAndReadmeIndependently() = runTest(dispatcher) {
        val repository = FakeRepository()

        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()

        assertEquals("openai/codex", viewModel.state.value.details?.repository?.fullName)
        assertEquals("# Codex", viewModel.state.value.readme)
        assertFalse(viewModel.state.value.isLoadingDetails)
        assertFalse(viewModel.state.value.isLoadingReadme)
        assertFalse(viewModel.state.value.detailsError)
        assertFalse(viewModel.state.value.readmeError)
    }

    @Test
    fun retryRecoversAFailedDetailsRequestWithoutDiscardingReadme() = runTest(dispatcher) {
        val repository = FakeRepository(detailsResult = Result.failure(IllegalStateException("offline")))
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.detailsError)
        assertEquals("# Codex", viewModel.state.value.readme)

        repository.detailsResult = Result.success(details())
        viewModel.onAction(RepositoryDetailAction.RetryDetails)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.detailsError)
        assertEquals("openai/codex", viewModel.state.value.details?.repository?.fullName)
        assertEquals("# Codex", viewModel.state.value.readme)
    }

    @Test
    fun signedInViewerCanStarWatchAndForkWithoutMixingOperationStates() = runTest(dispatcher) {
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", FakeRepository(), actions)
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertEquals(GithubRepositoryViewerState(false, false), viewModel.state.value.viewerState)

        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.ToggleWatch)
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.Fork)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.viewerState?.isStarred == true)
        assertTrue(viewModel.state.value.viewerState?.isWatching == true)
        assertEquals("joyins/codex", viewModel.state.value.forkedRepository?.fullName)
        assertEquals(43, viewModel.state.value.details?.forks)
        assertFalse(viewModel.state.value.isUpdatingStar)
        assertFalse(viewModel.state.value.isUpdatingWatch)
        assertFalse(viewModel.state.value.isForking)

        viewModel.onSessionChanged(GithubSession.SignedOut)
        assertEquals(null, viewModel.state.value.viewerState)
    }

    @Test
    fun signedInViewerCanUpdateTopicsAndStateReflectsServerResult() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("Android", "Kotlin")))
        advanceUntilIdle()

        assertEquals(listOf("Android", "Kotlin"), repository.updatedTopics)
        assertEquals(listOf("android", "kotlin"), viewModel.state.value.details?.topics)
        assertFalse(viewModel.state.value.isUpdatingTopics)
        assertFalse(viewModel.state.value.topicsError)
    }

    private class FakeRepository(
        var detailsResult: Result<GithubRepositoryDetails> = Result.success(details()),
        var readmeResult: Result<String?> = Result.success("# Codex")
    ) : GithubRepositoryDetailsRepository {
        var updatedTopics: List<String>? = null
        override suspend fun details(owner: String, name: String) = detailsResult
        override suspend fun readme(owner: String, name: String, ref: String?) = readmeResult
        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            Result.success(GithubBranchProtection(branch, 2, 1, enforceAdmins = true))
        override suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>> {
            updatedTopics = topics
            return Result.success(topics.map(String::lowercase))
        }
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
    }

    private class FakeActionsRepository : GithubRepositoryActionsRepository {
        override suspend fun viewerState(owner: String, name: String) =
            Result.success(GithubRepositoryViewerState(isStarred = false, isWatching = false))

        override suspend fun setStarred(owner: String, name: String, starred: Boolean) =
            Result.success(starred)

        override suspend fun setWatching(owner: String, name: String, watching: Boolean) =
            Result.success(watching)

        override suspend fun fork(owner: String, name: String) = Result.success(
            GithubRepository(
                id = 12,
                name = name,
                fullName = "joyins/$name",
                description = "Fork",
                language = "Rust",
                stars = 0,
                updatedAt = "2026-08-16T00:00:00Z",
                isPrivate = false,
                htmlUrl = "https://github.com/joyins/$name"
            )
        )
    }

    private companion object {
        fun details() = GithubRepositoryDetails(
            repository = GithubRepository(
                id = 11,
                name = "codex",
                fullName = "openai/codex",
                description = "A coding agent",
                language = "Rust",
                stars = 1000,
                updatedAt = "2026-08-16T00:00:00Z",
                isPrivate = false,
                htmlUrl = "https://github.com/openai/codex"
            ),
            ownerLogin = "openai",
            ownerAvatarUrl = "https://avatars.example/openai",
            defaultBranch = "main",
            forks = 42,
            watchers = 7,
            openIssues = 13,
            license = "MIT",
            topics = listOf("ai", "developer-tools"),
            isArchived = false,
            isFork = false
        )

        fun account() = GithubAccount(
            id = 1,
            login = "joyins",
            name = "Joyins",
            bio = null,
            avatarUrl = "https://avatars.example/joyins",
            htmlUrl = "https://github.com/joyins",
            publicRepositories = 1,
            followers = 1,
            following = 1
        )
    }
}
