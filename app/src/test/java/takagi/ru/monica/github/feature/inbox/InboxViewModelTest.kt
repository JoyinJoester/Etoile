package takagi.ru.monica.github.feature.inbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubNotificationReason
import takagi.ru.monica.github.domain.GithubNotificationsRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectingMentionFilterOnlyExposesMentions() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        viewModel.onAction(InboxAction.SelectFilter(InboxFilter.MENTIONS))

        val state = viewModel.state.value
        assertTrue(state.visibleItems.isNotEmpty())
        assertTrue(state.visibleItems.all { it.reason == GithubNotificationReason.MENTION })
    }

    @Test
    fun openingNotificationMarksOnlyThatNotificationRead() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val initial = viewModel.state.value
        val openedId = initial.items.first().id

        viewModel.onAction(InboxAction.OpenNotification(openedId))
        advanceUntilIdle()

        val updated = viewModel.state.value
        assertFalse(openedId in updated.unreadIds)
        assertEquals(initial.unreadIds.size - 1, updated.unreadIds.size)
    }

    @Test
    fun markAllReadClearsUnreadSet() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.onAction(InboxAction.MarkAllRead)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.unreadIds.isEmpty())
    }

    @Test
    fun refreshKeepsExistingNotificationsVisibleWhileReplacingTheFirstPage() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val existing = viewModel.state.value.items

        viewModel.onAction(InboxAction.Refresh)

        assertEquals(existing, viewModel.state.value.items)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(existing, viewModel.state.value.items)
    }

    @Test
    fun refreshFailureKeepsExistingNotificationsAndIsRetriedAsARefresh() = runTest(dispatcher) {
        val repository = FailingRefreshNotificationsRepository()
        val viewModel = InboxViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(InboxAction.Refresh)
        advanceUntilIdle()

        assertEquals(listOf("1"), viewModel.state.value.items.map(GithubNotification::id))
        assertTrue(viewModel.state.value.error)
        assertTrue(viewModel.state.value.refreshError)

        repository.failRefresh = false
        viewModel.onAction(InboxAction.Refresh)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshError)
    }

    @Test
    fun loadMoreAppendsNotificationsAndUnreadIds() = runTest(dispatcher) {
        val viewModel = InboxViewModel(FakeNotificationsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.state.value.items.map(GithubNotification::id))
        assertEquals(setOf("1", "2"), viewModel.state.value.unreadIds)
    }

    @Test
    fun loadMoreFailureKeepsExistingNotificationsAndAllowsRetry() = runTest(dispatcher) {
        val repository = FailingPageNotificationsRepository()
        val viewModel = InboxViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("1"), viewModel.state.value.items.map(GithubNotification::id))
        assertTrue(viewModel.state.value.error)
        assertEquals(2, viewModel.state.value.nextPage)

        repository.failNextPage = false
        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.state.value.items.map(GithubNotification::id))
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun markReadFailureIsAnActionErrorAndDoesNotBlockPagination() = runTest(dispatcher) {
        val repository = FakeNotificationsRepository().apply { failMarkRead = true }
        val viewModel = InboxViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(InboxAction.OpenNotification("1"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.error)
        assertTrue("1" in viewModel.state.value.unreadIds)

        repository.failMarkRead = false
        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.state.value.items.map(GithubNotification::id))
    }

    @Test
    fun markingThreadDoneRemovesItFromTheInboxAndUnreadState() = runTest(dispatcher) {
        val repository = FakeNotificationsRepository()
        val viewModel = InboxViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(InboxAction.LoadMore)
        advanceUntilIdle()

        viewModel.onAction(InboxAction.MarkDone("1"))
        advanceUntilIdle()

        assertEquals(listOf("2"), viewModel.state.value.items.map(GithubNotification::id))
        assertFalse("1" in viewModel.state.value.unreadIds)
        assertEquals(listOf("1"), repository.doneIds)
        assertTrue(viewModel.state.value.triageBusyIds.isEmpty())
    }

    @Test
    fun unsubscribeFailureKeepsTheThreadAndExposesRowScopedError() = runTest(dispatcher) {
        val repository = FakeNotificationsRepository().apply { failUnsubscribe = true }
        val viewModel = InboxViewModel(repository)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(InboxAction.Unsubscribe("1"))
        advanceUntilIdle()

        assertEquals(listOf("1"), repository.unsubscribeIds)
        assertTrue(viewModel.state.value.items.any { it.id == "1" })
        assertTrue("1" in viewModel.state.value.triageErrorIds)
        assertFalse("1" in viewModel.state.value.triageBusyIds)
    }

    private suspend fun TestScope.loadedViewModel(): InboxViewModel {
        val viewModel = InboxViewModel(FakeNotificationsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        return viewModel
    }

    private class FakeNotificationsRepository : GithubNotificationsRepository {
        var failMarkRead = false
        var failUnsubscribe = false
        val doneIds = mutableListOf<String>()
        val unsubscribeIds = mutableListOf<String>()

        override suspend fun notifications(page: Int, perPage: Int) = Result.success(
            GithubPage(
                items = listOf(notifications[page - 1]),
                nextPage = if (page == 1) 2 else null
            )
        )
        override suspend fun markRead(id: String) = if (failMarkRead) {
            Result.failure(IllegalStateException("Mark read failed"))
        } else {
            Result.success(Unit)
        }
        override suspend fun markAllRead() = Result.success(Unit)
        override suspend fun markDone(id: String): Result<Unit> {
            doneIds += id
            return Result.success(Unit)
        }
        override suspend fun unsubscribeAndMarkDone(id: String): Result<Unit> {
            unsubscribeIds += id
            return if (failUnsubscribe) {
                Result.failure(IllegalStateException("Unsubscribe failed"))
            } else {
                Result.success(Unit)
            }
        }
    }

    private class FailingPageNotificationsRepository : GithubNotificationsRepository {
        var failNextPage = true

        override suspend fun notifications(page: Int, perPage: Int): Result<GithubPage<GithubNotification>> {
            if (page == 2 && failNextPage) return Result.failure(IllegalStateException("Page failed"))
            return Result.success(
                GithubPage(
                    items = listOf(notifications[page - 1]),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }

        override suspend fun markRead(id: String) = Result.success(Unit)
        override suspend fun markAllRead() = Result.success(Unit)
        override suspend fun markDone(id: String) = Result.success(Unit)
        override suspend fun unsubscribeAndMarkDone(id: String) = Result.success(Unit)
    }

    private class FailingRefreshNotificationsRepository : GithubNotificationsRepository {
        var failRefresh = true
        private var requestCount = 0

        override suspend fun notifications(
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubNotification>> {
            requestCount++
            if (requestCount > 1 && failRefresh) {
                return Result.failure(IllegalStateException("Refresh failed"))
            }
            return Result.success(GithubPage(listOf(notifications.first()), nextPage = 2))
        }

        override suspend fun markRead(id: String) = Result.success(Unit)
        override suspend fun markAllRead() = Result.success(Unit)
        override suspend fun markDone(id: String) = Result.success(Unit)
        override suspend fun unsubscribeAndMarkDone(id: String) = Result.success(Unit)
    }

    private fun account() = GithubAccount(1, "joyins", "Joyins", null, "", "https://github.com/joyins", 1, 1, 1)

    private companion object {
        val notifications = listOf(
            GithubNotification("1", GithubNotificationReason.REVIEW_REQUESTED, true, "Review", "PullRequest", "etoile/mobile", "https://github.com/etoile/mobile", "2026-08-16"),
            GithubNotification("2", GithubNotificationReason.MENTION, true, "Mention", "Issue", "etoile/mobile", "https://github.com/etoile/mobile", "2026-08-16")
        )
    }
}
