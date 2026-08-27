package takagi.ru.monica.github.feature.commits

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
import takagi.ru.monica.github.domain.GithubCommit
import takagi.ru.monica.github.domain.GithubCommitDetails
import takagi.ru.monica.github.domain.GithubCommitsRepository
import takagi.ru.monica.github.domain.GithubPage

@OptIn(ExperimentalCoroutinesApi::class)
class CommitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listLoadsSelectedRefAndAppendsPages() = runTest(dispatcher) {
        val repository = FakeCommitsRepository()
        val viewModel = CommitsViewModel("openai", "codex", "feature/ui", repository)
        advanceUntilIdle()

        viewModel.onAction(CommitsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("feature/ui" to 1, "feature/ui" to 2), repository.requests)
        assertEquals(listOf("0000001", "0000002"), viewModel.state.value.items.map(GithubCommit::shortSha))
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun nextPageFailureKeepsExistingCommitsAndRetries() = runTest(dispatcher) {
        val repository = FakeCommitsRepository().apply { failSecondPage = true }
        val viewModel = CommitsViewModel("openai", "codex", "main", repository)
        advanceUntilIdle()

        viewModel.onAction(CommitsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertTrue(viewModel.state.value.error)

        repository.failSecondPage = false
        viewModel.onAction(CommitsAction.LoadMore)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.items.size)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun detailLoadsCommitAndFileChanges() = runTest(dispatcher) {
        val repository = FakeCommitsRepository()
        val viewModel = CommitDetailViewModel("openai", "codex", SHA_PREFIX + "9", repository)
        advanceUntilIdle()

        assertEquals(SHA_PREFIX + "9", repository.detailShas.single())
        assertEquals(9, viewModel.state.value.details?.additions)
        assertFalse(viewModel.state.value.isLoading)
    }

    private class FakeCommitsRepository : GithubCommitsRepository {
        val requests = mutableListOf<Pair<String, Int>>()
        val detailShas = mutableListOf<String>()
        var failSecondPage = false

        override suspend fun commits(
            owner: String,
            name: String,
            ref: String,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubCommit>> {
            requests += ref to page
            if (page == 2 && failSecondPage) {
                return Result.failure(IllegalStateException("page failed"))
            }
            return Result.success(
                GithubPage(
                    items = listOf(commit(page.toString())),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }

        override suspend fun commit(owner: String, name: String, sha: String): Result<GithubCommitDetails> {
            detailShas += sha
            return Result.success(
                GithubCommitDetails(
                    commit = commit("9"),
                    additions = 9,
                    deletions = 2,
                    totalChanges = 11,
                    files = emptyList()
                )
            )
        }
    }

    private companion object {
        const val SHA_PREFIX = "000000"

        fun commit(suffix: String) = GithubCommit(
            sha = SHA_PREFIX + suffix + "1234567890abcdef1234567890abcdef",
            message = "Commit $suffix",
            authorName = "Alice",
            authorLogin = "alice",
            authorAvatarUrl = null,
            authoredAt = "2026-08-16T00:00:00Z",
            committerName = "Alice",
            committedAt = "2026-08-16T00:00:00Z",
            htmlUrl = "https://github.com/openai/codex/commit/${SHA_PREFIX + suffix}",
            isVerified = true
        )
    }
}
