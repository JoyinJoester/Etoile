package takagi.ru.monica.github.feature.discussions

import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionsViewModelTest {
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    private fun snapshot(handle: SavedStateHandle) = SavedStateHandle(
        handle.keys().associateWith { handle.get<Any?>(it) }
    )

    @Test fun recreatingRestoresDraftAndComposerWithoutReposting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("o", "r", repo, saved, 10)
        advanceUntilIdle()
        vm.setComposing(true)
        vm.edit("Draft title", "Unpublished text", "cat")
        vm.cancelPendingRequests(clearAccountData = false)
        val restored = DiscussionsViewModel("o", "r", repo, snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("Draft title", restored.state.value.title)
        assertEquals("Unpublished text", restored.state.value.body)
        assertEquals("cat", restored.state.value.categoryId)
        assertTrue(restored.state.value.composing)
        assertFalse(restored.state.value.submitting)
        assertEquals(0, repo.createCalls)
        restored.setComposing(false)
        restored.setComposing(true)
        assertEquals("Unpublished text", restored.state.value.body)
    }

    @Test fun successfulPublishDoesNotRestoreConsumedDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val vm = DiscussionsViewModel("o", "r", FakeRepo(), saved, 10)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Title", "Body", "cat")
        vm.create(); advanceUntilIdle()
        val restored = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("", restored.state.value.body)
        assertNull(restored.state.value.categoryId)
        assertFalse(restored.state.value.composing)
    }

    @Test fun restoringInterruptedPublishKeepsDraftWithoutSubmittingAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val repo = FakeRepo().apply { createGate = CompletableDeferred() }
        val vm = DiscussionsViewModel("o", "r", repo, saved, 10)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Title", "Body", "cat")
        vm.create(); runCurrent()
        assertTrue(vm.state.value.submitting)
        val restored = DiscussionsViewModel("o", "r", repo, snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals(1, repo.createCalls)
        assertEquals("Body", restored.state.value.body)
        assertTrue(restored.state.value.composing)
        assertFalse(restored.state.value.submitting)
        vm.cancelPendingRequests(clearAccountData = false)
    }

    @Test fun failedPublishRetainsRestorableDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val repo = FakeRepo().apply {
            createGate = CompletableDeferred(Result.failure(java.io.IOException("offline")))
        }
        val vm = DiscussionsViewModel("o", "r", repo, saved, 10)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Title", "Retry this", "cat")
        vm.create(); advanceUntilIdle()
        assertTrue(vm.state.value.submitError)
        val restored = DiscussionsViewModel("o", "r", repo, snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("Retry this", restored.state.value.body)
        assertEquals("cat", restored.state.value.categoryId)
        assertTrue(restored.state.value.composing)
        assertEquals(1, repo.createCalls)
    }

    @Test fun savedDraftCannotMoveBetweenAccountsOrRepositories() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val vm = DiscussionsViewModel("o", "r", FakeRepo(), saved, 10)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Private", "Private text", "cat")
        val otherAccount = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 20)
        val otherRepository = DiscussionsViewModel("o", "different", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        listOf(otherAccount, otherRepository).forEach {
            assertEquals("", it.state.value.body)
            assertFalse(it.state.value.composing)
        }
        vm.cancelPendingRequests()
        val restored = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("", restored.state.value.body)
        assertFalse(restored.state.value.composing)
    }
    @Test fun loadsPagesAndDeduplicatesCursorItems() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("owner", "repo", repo)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        vm.load(true); advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(2, repo.listCalls)
    }

    @Test fun requiresCategoryAndContentBeforeCreating() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo(); val vm = DiscussionsViewModel("o", "r", repo); advanceUntilIdle()
        vm.edit("Title", "Body", null); vm.create(); advanceUntilIdle()
        assertTrue(vm.state.value.submitError); assertEquals(0, repo.createCalls)
        vm.edit("Title", "Body", "cat"); vm.create(); advanceUntilIdle()
        assertEquals(1, repo.createCalls); assertNotNull(vm.state.value.created)
    }

    @Test fun failedPublishPreservesDraftAndRejectsDuplicateSubmission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val gate = CompletableDeferred<Result<GithubDiscussion>>()
        repo.createGate = gate
        val vm = DiscussionsViewModel("o", "r", repo)
        advanceUntilIdle()
        vm.edit("Original title", "Original body", "cat")
        vm.create(); runCurrent()
        vm.create()
        vm.edit("Changed", "Changed", "cat")
        assertEquals(1, repo.createCalls)
        assertTrue(vm.state.value.submitting)
        gate.complete(Result.failure(java.io.IOException("offline")))
        advanceUntilIdle()
        assertFalse(vm.state.value.submitting)
        assertTrue(vm.state.value.submitError)
        assertEquals("Original title", vm.state.value.title)
        assertEquals("Original body", vm.state.value.body)
        assertEquals("cat", vm.state.value.categoryId)
        assertNull(vm.state.value.created)
        repo.createGate = null
        vm.create(); advanceUntilIdle()
        assertEquals(2, repo.createCalls)
        assertEquals("", vm.state.value.body)
        assertNotNull(vm.state.value.created)
    }

    @Test fun openingDetailPreservesLoadedListAndDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = DiscussionsViewModel("o", "r", FakeRepo())
        advanceUntilIdle()
        vm.edit("Draft", "Unpublished body", "cat")
        val before = vm.state.value
        vm.cancelPendingRequests(clearAccountData = false)
        assertEquals(before, vm.state.value)
        assertEquals("next", vm.state.value.nextCursor)
        assertEquals("Unpublished body", vm.state.value.body)
        vm.cancelPendingRequests()
        assertEquals(DiscussionsUiState(), vm.state.value)
    }

    @Test fun leavingAccountCancelsPublicationAndClearsItsDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val gate = CompletableDeferred<Result<GithubDiscussion>>()
        repo.createGate = gate
        val vm = DiscussionsViewModel("o", "r", repo)
        advanceUntilIdle()
        vm.edit("Private title", "Private body", "cat")
        vm.create(); runCurrent()
        vm.cancelPendingRequests()
        gate.complete(Result.failure(java.io.IOException("late response")))
        advanceUntilIdle()
        assertEquals(DiscussionsUiState(), vm.state.value)
        vm.load(); vm.loadCategories(); advanceUntilIdle()
        assertEquals("R", vm.state.value.repositoryId)
        assertEquals("", vm.state.value.body)
    }

    @Test fun categoryFailureCanRetryWithoutDiscardingDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo().apply { failCategories = true }
        val vm = DiscussionsViewModel("o", "r", repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.categoriesError)
        vm.edit("Draft", "Keep this", null)
        repo.failCategories = false
        vm.loadCategories(); advanceUntilIdle()
        assertFalse(vm.state.value.categoriesError)
        assertEquals("cat", vm.state.value.categories.single().id)
        assertEquals("Keep this", vm.state.value.body)
    }

    private class FakeRepo : GithubDiscussionsRepository {
        var createGate: CompletableDeferred<Result<GithubDiscussion>>? = null
        var failCategories = false
        var listCalls = 0; var createCalls = 0
        private val cat = GithubDiscussionCategory("cat", "Q&A", true)
        private fun item(id: String, number: Int) = GithubDiscussion(id, number, "Title $number", "Body", "https://github.com/o/r/discussions/$number", "user", cat, 0, false)
        override suspend fun list(owner: String, name: String, cursor: String?) = Result.success(
            GithubDiscussionPage("R", if (cursor == null) listOf(item("1", 1)) else listOf(item("1", 1), item("2", 2)), if (cursor == null) "next" else null).also { listCalls++ })
        override suspend fun categories(owner: String, name: String): Result<List<GithubDiscussionCategory>> =
            if (failCategories) Result.failure(java.io.IOException("offline")) else Result.success(listOf(cat))
        override suspend fun create(repositoryId: String, categoryId: String, title: String, body: String): Result<GithubDiscussion> {
            createCalls++
            return createGate?.await() ?: Result.success(item("3", 3))
        }
        override suspend fun reply(discussionId: String, body: String) = Result.success("comment")
    }
}
