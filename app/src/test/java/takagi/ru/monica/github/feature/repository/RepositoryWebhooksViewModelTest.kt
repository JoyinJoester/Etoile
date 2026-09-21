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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
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
import takagi.ru.monica.github.domain.GithubWebhookEdit

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryWebhooksViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun webhooksAppendWithoutDuplicates() = runTest(dispatcher) {
        val viewModel = RepositoryWebhooksViewModel("openai", "codex", FakeRepository(), viewerCanAdmin = false)
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
            val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = false)
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
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = false)
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
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = false)
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

    @Test
    fun anAdminTogglesActiveStateAndMergesTheServerConfirmation() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = true)
        advanceUntilIdle()
        assertTrue(model.state.value.items.single().isActive)

        model.onAction(RepositoryWebhooksAction.SetEnabled(11, false))
        advanceUntilIdle()

        // Only the active flag travels; the row is replaced with what the server confirmed.
        assertEquals(listOf(11L to false), repository.toggles)
        assertFalse(model.state.value.items.single().isActive)
        assertNull(model.state.value.webhookFailure)
        assertFalse(model.state.value.isUpdatingWebhook)
    }

    @Test
    fun aToggleThatAlreadyMatchesTheServerIsNeverSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = true)
        advanceUntilIdle()

        model.onAction(RepositoryWebhooksAction.SetEnabled(11, true))
        advanceUntilIdle()

        assertTrue(repository.toggles.isEmpty())
        assertFalse(model.state.value.isUpdatingWebhook)
    }

    @Test
    fun aRefusedToggleKeepsTheConfirmedStateAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.updateResult = Result.failure(GithubApiException(403))
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = true)
        advanceUntilIdle()

        model.onAction(RepositoryWebhooksAction.SetEnabled(11, false))
        advanceUntilIdle()

        assertTrue(model.state.value.items.single().isActive)
        assertEquals(RepositoryWriteFailure.Forbidden, model.state.value.webhookFailure)
        assertFalse(model.state.value.isUpdatingWebhook)
    }

    @Test
    fun deletingAWebhookRemovesTheRowAndClearsTheFailure() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = true)
        advanceUntilIdle()

        model.onAction(RepositoryWebhooksAction.Delete(11))
        advanceUntilIdle()

        assertEquals(listOf(11L), repository.deletes)
        assertTrue(model.state.value.items.isEmpty())
        assertNull(model.state.value.webhookFailure)
    }

    @Test
    fun aRefusedDeleteKeepsTheRowAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.deleteResult = Result.failure(GithubApiException(403))
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = true)
        advanceUntilIdle()

        model.onAction(RepositoryWebhooksAction.Delete(11))
        advanceUntilIdle()

        assertEquals(listOf(11L), model.state.value.items.map { it.id })
        assertEquals(RepositoryWriteFailure.Forbidden, model.state.value.webhookFailure)
    }

    @Test
    fun aViewerWithoutAdminRightsCanNeverWrite() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = RepositoryWebhooksViewModel("openai", "codex", repository, viewerCanAdmin = false)
        advanceUntilIdle()

        model.onAction(RepositoryWebhooksAction.SetEnabled(11, false))
        model.onAction(RepositoryWebhooksAction.Delete(11))
        advanceUntilIdle()

        assertTrue(repository.toggles.isEmpty())
        assertTrue(repository.deletes.isEmpty())
        assertFalse(model.state.value.canManage)
    }

    private class FakeRepository : GithubRepositoryDetailsRepository {
        val pages = mutableListOf<Int>()
        val toggles = mutableListOf<Pair<Long, Boolean>>()
        val deletes = mutableListOf<Long>()
        var response: suspend (Int) -> Result<GithubPage<GithubRepositoryWebhook>> = { page -> when (page) {
            1 -> Result.success(GithubPage(listOf(webhook(11)), 2))
            else -> Result.success(GithubPage(listOf(webhook(11), webhook(12)), null))
        } }
        var updateResult: Result<GithubRepositoryWebhook>? = null
        var deleteResult: Result<Unit>? = null

        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int): Result<GithubPage<GithubRepositoryWebhook>> {
            pages += page
            return response(page)
        }

        override suspend fun updateWebhook(
            owner: String,
            name: String,
            id: Long,
            edit: GithubWebhookEdit
        ): Result<GithubRepositoryWebhook> {
            toggles += id to (edit.active ?: false)
            return updateResult ?: Result.success(webhook(id).copy(isActive = edit.active ?: false))
        }

        override suspend fun deleteWebhook(owner: String, name: String, id: Long): Result<Unit> {
            deletes += id
            return deleteResult ?: Result.success(Unit)
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
