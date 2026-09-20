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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySettings
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
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

    @Test
    fun failedRefreshRetriesFirstPageAndReplacesOldItems() = runTest(dispatcher) {
        for (nextPage in listOf<Int?>(null, 2)) {
            val repository = FakeRepository()
            repository.response = { Result.success(GithubPage(listOf(webhook(11)), nextPage)) }
            val model = RepositoryWebhooksViewModel("openai", "codex", repository)
            advanceUntilIdle()
            repository.response = { Result.failure(IllegalStateException("offline")) }
            model.onAction(RepositoryWebhooksAction.Refresh)
            advanceUntilIdle()
            assertTrue(model.state.value.error)
            assertEquals(listOf(11L), model.state.value.items.map { it.id })
            repository.response = { Result.success(GithubPage(listOf(webhook(99)), null)) }
            model.onAction(RepositoryWebhooksAction.Retry)
            assertEquals(listOf(11L), model.state.value.items.map { it.id })
            advanceUntilIdle()
            assertEquals(listOf(1, 1, 1), repository.pages)
            assertEquals(listOf(99L), model.state.value.items.map { it.id })
            assertFalse(model.state.value.error)
        }
    }

    @Test
    fun failedPaginationRetriesSamePageAndKeepsEarlierItems() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository)
        advanceUntilIdle()
        repository.response = { Result.failure(IllegalStateException("offline")) }
        model.onAction(RepositoryWebhooksAction.LoadMore)
        advanceUntilIdle()
        repository.response = { Result.success(GithubPage(listOf(webhook(12)), null)) }
        model.onAction(RepositoryWebhooksAction.Retry)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), repository.pages)
        assertEquals(listOf(11L, 12L), model.state.value.items.map { it.id })
    }

    @Test
    fun paginationCannotCancelPendingRefresh() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository)
        advanceUntilIdle()
        val pending = CompletableDeferred<Result<GithubPage<GithubRepositoryWebhook>>>()
        repository.response = { pending.await() }
        model.onAction(RepositoryWebhooksAction.Refresh)
        runCurrent()
        assertFalse(model.state.value.canLoadMore)
        model.onAction(RepositoryWebhooksAction.LoadMore)
        model.onAction(RepositoryWebhooksAction.Retry)
        runCurrent()
        assertEquals(listOf(1, 1), repository.pages)
        pending.complete(Result.success(GithubPage(listOf(webhook(99)), null)))
        advanceUntilIdle()
        assertEquals(listOf(99L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.isRefreshing)
    }

    private class FakeRepository : GithubRepositoryDetailsRepository {
        val pages = mutableListOf<Int>()
        var response: suspend (Int) -> Result<GithubPage<GithubRepositoryWebhook>> = { page -> when (page) {
            1 -> Result.success(GithubPage(listOf(webhook(11)), 2))
            else -> Result.success(GithubPage(listOf(webhook(11), webhook(12)), null))
        } }
        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubRepositoryWebhook>> {
            pages += page
            return response(page)
        }

        override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> =
            Result.failure(UnsupportedOperationException())
        override suspend fun readme(owner: String, name: String, ref: String?) = Result.success<String?>(null)
        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            Result.success<GithubBranchProtection?>(null)
        override suspend fun updateTopics(owner: String, name: String, topics: List<String>) =
            Result.success(topics)
        override suspend fun updateSettings(
            owner: String,
            name: String,
            edit: GithubRepositorySettingsEdit
        ): Result<GithubRepositorySettings> = Result.failure(UnsupportedOperationException())
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
        override suspend fun setCollaborator(owner: String, name: String, invite: GithubCollaboratorInvite) =
            Result.failure<GithubCollaboratorChange>(UnsupportedOperationException())
        override suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
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
