package takagi.ru.monica.github.feature.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryWebhookDeliveriesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun waitsForSessionAndDoesNotReadWhileSignedOut() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(GithubSession.Loading)
        advanceUntilIdle()
        assertTrue(repository.cursors.isEmpty())
        model.onSessionChanged(GithubSession.SignedOut)
        model.onAction(RepositoryWebhookDeliveriesAction.Refresh)
        advanceUntilIdle()
        assertTrue(repository.cursors.isEmpty())
        assertFalse(model.state.value.isLoading)
        assertEquals(RepositoryWriteFailure.Forbidden, model.state.value.loadFailure)
    }

    @Test fun cursorPaginationAppendsWithoutDuplicateDeliveries() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        model.onAction(RepositoryWebhookDeliveriesAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf(null, "opaque+/=cursor"), repository.cursors)
        assertEquals(listOf(11L, 12L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.canLoadMore)
        assertTrue(model.state.value.canManage)
    }

    @Test fun failedRefreshKeepsHistoryAndRetryStartsFromTheBeginning() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        repository.read = { Result.failure(IllegalStateException("offline")) }
        model.onAction(RepositoryWebhookDeliveriesAction.Refresh)
        advanceUntilIdle()
        assertEquals(listOf(11L), model.state.value.items.map { it.id })
        assertEquals(RepositoryWriteFailure.Network, model.state.value.loadFailure)
        repository.read = { Result.success(GithubWebhookDeliveriesPage(listOf(delivery(99)), null)) }
        model.onAction(RepositoryWebhookDeliveriesAction.Retry)
        assertTrue(model.state.value.isRefreshing)
        advanceUntilIdle()
        assertEquals(listOf(null, null, null), repository.cursors)
        assertEquals(listOf(99L), model.state.value.items.map { it.id })
        assertNull(model.state.value.loadFailure)
    }

    @Test fun failedPaginationRetriesTheSameOpaqueCursor() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        repository.read = { Result.failure(IllegalStateException("offline")) }
        model.onAction(RepositoryWebhookDeliveriesAction.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf(11L), model.state.value.items.map { it.id })
        repository.read = { Result.success(GithubWebhookDeliveriesPage(listOf(delivery(12)), null)) }
        model.onAction(RepositoryWebhookDeliveriesAction.Retry)
        advanceUntilIdle()
        assertEquals(listOf(null, "opaque+/=cursor", "opaque+/=cursor"), repository.cursors)
        assertEquals(listOf(11L, 12L), model.state.value.items.map { it.id })
    }

    @Test fun loadMoreAndRetryCannotCancelPendingRefresh() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        val pending = CompletableDeferred<Result<GithubWebhookDeliveriesPage>>()
        repository.read = { pending.await() }
        model.onAction(RepositoryWebhookDeliveriesAction.Refresh)
        runCurrent()
        model.onAction(RepositoryWebhookDeliveriesAction.LoadMore)
        model.onAction(RepositoryWebhookDeliveriesAction.Retry)
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        runCurrent()
        assertEquals(listOf(null, null), repository.cursors)
        assertTrue(repository.writes.isEmpty())
        pending.complete(Result.success(GithubWebhookDeliveriesPage(listOf(delivery(99)), null)))
        advanceUntilIdle()
        assertEquals(listOf(99L), model.state.value.items.map { it.id })
    }

    @Test fun redeliveryIsSingleFlightAndAcceptedResponseRefreshesWithoutInventingSuccess() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        val pending = CompletableDeferred<Result<Unit>>()
        repository.write = { pending.await() }
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        runCurrent()
        assertEquals(11L, model.state.value.redeliveringId)
        assertEquals(listOf(Triple("openai/codex", 9L, 11L)), repository.writes)
        pending.complete(Result.success(Unit))
        advanceUntilIdle()
        assertEquals(listOf(null, null), repository.cursors)
        assertEquals(11L, model.state.value.acceptedDeliveryId)
        assertEquals(503, model.state.value.items.single().statusCode)
        assertNull(model.state.value.redeliveringId)
    }

    @Test fun forbiddenRedeliveryKeepsTheHistoryAndDoesNotRefresh() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.write = { Result.failure(GithubApiException(403)) }
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        advanceUntilIdle()
        assertEquals(RepositoryWriteFailure.Forbidden, model.state.value.redeliveryFailure)
        assertEquals(listOf(11L), model.state.value.items.map { it.id })
        assertEquals(1, repository.cursors.size)
        assertNull(model.state.value.acceptedDeliveryId)
    }

    @Test fun acceptedRequestWithFailedReloadCanRetryReadingWithoutRepeatingTheWrite() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        repository.read = { Result.failure(IllegalStateException("offline")) }
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        advanceUntilIdle()
        assertEquals(11L, model.state.value.acceptedDeliveryId)
        assertEquals(RepositoryWriteFailure.Network, model.state.value.loadFailure)
        repository.read = { Result.success(GithubWebhookDeliveriesPage(emptyList(), null)) }
        model.onAction(RepositoryWebhookDeliveriesAction.Retry)
        advanceUntilIdle()
        assertEquals(1, repository.writes.size)
        assertTrue(model.state.value.items.isEmpty())
        assertNull(model.state.value.loadFailure)
    }

    @Test fun viewerAndUnknownDeliveryCannotWrite() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.base.viewerRole = GithubCollaboratorRole.READ
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        assertTrue(repository.writes.isEmpty())
        repository.base.viewerRole = GithubCollaboratorRole.ADMIN
        model.onAction(RepositoryWebhookDeliveriesAction.Refresh)
        advanceUntilIdle()
        assertTrue(model.state.value.canManage)
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(999))
        advanceUntilIdle()
        assertTrue(repository.writes.isEmpty())
    }

    @Test fun signOutClearsHistoryAndIgnoresLateWriteCompletion() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        val pending = CompletableDeferred<Result<Unit>>()
        repository.write = { withContext(NonCancellable) { pending.await() } }
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        runCurrent()
        model.onSessionChanged(GithubSession.SignedOut)
        pending.complete(Result.success(Unit))
        advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.canManage)
        assertNull(model.state.value.redeliveringId)
        assertNull(model.state.value.acceptedDeliveryId)
        assertEquals(1, repository.cursors.size)
    }

    @Test fun accountSwitchIgnoresLateReadAndRechecksTheRole() = runTest(dispatcher) {
        val repository = FakeRepository()
        val pending = CompletableDeferred<Result<GithubWebhookDeliveriesPage>>()
        repository.read = { withContext(NonCancellable) { pending.await() } }
        val model = model(repository)
        model.onSessionChanged(session())
        runCurrent()
        repository.base.viewerRole = GithubCollaboratorRole.READ
        repository.read = { Result.success(GithubWebhookDeliveriesPage(listOf(delivery(22)), null)) }
        model.onSessionChanged(session(2))
        advanceUntilIdle()
        pending.complete(Result.success(GithubWebhookDeliveriesPage(listOf(delivery(11)), "old-cursor")))
        advanceUntilIdle()
        assertEquals(listOf(22L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.canManage)
        assertNull(model.state.value.nextCursor)
        assertEquals(2L, model.state.value.viewerId)
    }

    @Test fun repeatedCursorCannotCreateAnEndlessLoadMoreLoop() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.read = { Result.success(GithubWebhookDeliveriesPage(listOf(delivery(11)), "same")) }
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        model.onAction(RepositoryWebhookDeliveriesAction.LoadMore)
        advanceUntilIdle()
        assertFalse(model.state.value.canLoadMore)
    }

    @Test fun sessionRestorationWithdrawsOldAccessUntilTheAccountIsKnownAgain() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.onSessionChanged(session())
        advanceUntilIdle()
        assertTrue(model.state.value.canManage)
        model.onSessionChanged(GithubSession.Loading)
        assertFalse(model.state.value.canManage)
        assertTrue(model.state.value.items.isEmpty())
        model.onAction(RepositoryWebhookDeliveriesAction.Redeliver(11))
        advanceUntilIdle()
        assertTrue(repository.writes.isEmpty())
        model.onSessionChanged(session())
        advanceUntilIdle()
        assertEquals(2, repository.cursors.size)
        assertTrue(model.state.value.canManage)
    }

    private class FakeRepository(
        val base: TestGithubRepositoryDetailsRepository = TestGithubRepositoryDetailsRepository()
    ) : GithubRepositoryDetailsRepository by base {
        val cursors = mutableListOf<String?>()
        val writes = mutableListOf<Triple<String, Long, Long>>()
        var read: suspend (String?) -> Result<GithubWebhookDeliveriesPage> = { cursor ->
            Result.success(if (cursor == null) GithubWebhookDeliveriesPage(listOf(delivery(11)), "opaque+/=cursor")
            else GithubWebhookDeliveriesPage(listOf(delivery(11), delivery(12)), null))
        }
        var write: suspend () -> Result<Unit> = { Result.success(Unit) }

        override suspend fun webhookDeliveries(owner: String, name: String, id: Long, cursor: String?, perPage: Int): Result<GithubWebhookDeliveriesPage> {
            assertEquals("openai/codex", "$owner/$name")
            assertEquals(9L, id)
            cursors += cursor
            return read(cursor)
        }

        override suspend fun redeliverWebhook(owner: String, name: String, id: Long, deliveryId: Long): Result<Unit> {
            writes += Triple("$owner/$name", id, deliveryId)
            return write()
        }
    }

    private companion object {
        fun model(repository: GithubRepositoryDetailsRepository) = RepositoryWebhookDeliveriesViewModel("openai", "codex", 9, repository)
        fun delivery(id: Long) = GithubWebhookDelivery(id, statusCode = 503, event = "push")
        fun session(id: Long = 1) = GithubSession.SignedIn(GithubAccount(
            id, "user$id", null, null, "", "https://github.com/user$id", 0, 0, 0
        ))
    }
}
