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
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositoryWebhook

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryWebhooksViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun webhooksAppendWithoutDuplicates() = runTest(dispatcher) {
        val viewModel = RepositoryWebhooksViewModel("openai", "codex", FakeRepository())
        advanceUntilIdle()
        viewModel.onAction(RepositoryWebhooksAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(11L, 12L), viewModel.state.value.items.map(GithubRepositoryWebhook::id))
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    private class FakeRepository : GithubRepositoryDetailsRepository {
        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) = when (page) {
            1 -> Result.success(GithubPage(listOf(webhook(11)), 2))
            else -> Result.success(GithubPage(listOf(webhook(11), webhook(12)), null))
        }

        override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> =
            Result.failure(UnsupportedOperationException())
        override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)
        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            Result.success<GithubBranchProtection?>(null)
        override suspend fun updateTopics(owner: String, name: String, topics: List<String>) =
            Result.success(topics)
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
    }

    private companion object {
        fun webhook(id: Long) = GithubRepositoryWebhook(
            id = id,
            name = "web",
            isActive = true,
            events = listOf("push"),
            lastResponseCode = 200,
            lastResponseStatus = "OK",
            lastResponseMessage = "delivered"
        )
    }
}
