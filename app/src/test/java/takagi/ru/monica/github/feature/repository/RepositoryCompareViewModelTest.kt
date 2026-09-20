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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubBranchComparison
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubMergeResult
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestListQuery
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubRequestedReviewersUpdate

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryCompareViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun comparisonAsksForAWholePageOfFilesInsteadOfASingleCommit() = runTest(dispatcher) {
        val repository = FakeCompareRepository()
        val viewModel = RepositoryCompareViewModel("openai", "codex", "main", "feature/mobile", repository)
        advanceUntilIdle()

        assertEquals(listOf(100), repository.perPages)
        assertEquals(listOf("main" to "feature/mobile"), repository.ranges)
        assertEquals(2, viewModel.state.value.comparison?.aheadBy)
        assertEquals(1, viewModel.state.value.files.size)
        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.failure)
        assertEquals("main...feature/mobile", viewModel.state.value.refRange)
    }

    @Test
    fun aRefusedComparisonReportsWhyWithoutAnyRowsToShow() = runTest(dispatcher) {
        val viewModel = RepositoryCompareViewModel(
            "openai", "codex", "main", "gone",
            FakeCompareRepository(result = Result.failure(GithubApiException(404)))
        )
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.NotFound, viewModel.state.value.failure)
        assertNull(viewModel.state.value.comparison)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun aFailedRetryKeepsTheComparisonThatIsAlreadyOnScreen() = runTest(dispatcher) {
        val repository = FakeCompareRepository()
        val viewModel = RepositoryCompareViewModel("openai", "codex", "main", "feature/mobile", repository)
        advanceUntilIdle()

        repository.result = Result.failure(GithubApiException(403))
        viewModel.onAction(RepositoryCompareAction.Retry)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.failure)
        assertEquals(1, viewModel.state.value.files.size)
    }

    @Test
    fun retryClearsTheExplanationOnceTheComparisonArrives() = runTest(dispatcher) {
        val repository = FakeCompareRepository(result = Result.failure(GithubApiException(409)))
        val viewModel = RepositoryCompareViewModel("openai", "codex", "main", "feature/mobile", repository)
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Conflict, viewModel.state.value.failure)

        repository.result = Result.success(
            GithubBranchComparison("behind", 0, 1, 0, emptyList(), false)
        )
        viewModel.onAction(RepositoryCompareAction.Retry)
        advanceUntilIdle()

        assertNull(viewModel.state.value.failure)
        assertEquals("behind", viewModel.state.value.comparison?.status)
        assertEquals(2, repository.perPages.size)
    }

    private class FakeCompareRepository(
        var result: Result<GithubBranchComparison> = Result.success(
            GithubBranchComparison("diverged", 2, 1, 2, listOf(TEST_FILE), false)
        )
    ) : GithubPullRequestsRepository {
        val perPages = mutableListOf<Int>()
        val ranges = mutableListOf<Pair<String, String>>()

        override suspend fun compare(
            owner: String,
            name: String,
            base: String,
            head: String,
            headRepository: String?,
            perPage: Int
        ): Result<GithubBranchComparison> {
            perPages += perPage
            ranges += base to head
            return result
        }

        override suspend fun pullRequests(
            owner: String,
            name: String,
            query: GithubPullRequestListQuery,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubPullRequest>> = error("unused")

        override suspend fun pullRequest(owner: String, name: String, number: Int) = error("unused")

        override suspend fun files(
            owner: String,
            name: String,
            number: Int,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubPullRequestFile>> = error("unused")

        override suspend fun reviews(
            owner: String,
            name: String,
            number: Int,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubPullRequestReview>> = error("unused")

        override suspend fun reviewComments(
            owner: String,
            name: String,
            number: Int,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubPullRequestReviewComment>> = error("unused")

        override suspend fun submitReview(
            owner: String,
            name: String,
            number: Int,
            draft: GithubPullRequestReviewDraft
        ): Result<GithubPullRequestReview> = error("unused")

        override suspend fun merge(
            owner: String,
            name: String,
            number: Int,
            draft: GithubMergeDraft
        ): Result<GithubMergeResult> = error("unused")

        override suspend fun updateState(
            owner: String,
            name: String,
            number: Int,
            state: GithubPullRequestState
        ): Result<GithubPullRequest> = error("unused")

        override suspend fun updateContent(
            owner: String,
            name: String,
            number: Int,
            draft: GithubPullRequestDraft
        ): Result<GithubPullRequest> = error("unused")

        override suspend fun updateRequestedReviewers(
            owner: String,
            name: String,
            number: Int,
            update: GithubRequestedReviewersUpdate
        ): Result<GithubPullRequest> = error("unused")
    }

    private companion object {
        val TEST_FILE = GithubPullRequestFile(
            sha = "file-1",
            filename = "app/src/main/kotlin/Main.kt",
            status = "modified",
            additions = 4,
            deletions = 1,
            changes = 5,
            patch = "@@ -1,4 +1,7 @@\n-old\n+new",
            blobUrl = "https://github.com/openai/codex/blob/feature/mobile/Main.kt",
            rawUrl = null
        )
    }
}
