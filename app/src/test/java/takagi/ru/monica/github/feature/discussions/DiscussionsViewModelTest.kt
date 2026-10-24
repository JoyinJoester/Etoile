package takagi.ru.monica.github.feature.discussions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
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
    @Test fun switchingAwayClearsFetchedDataButPreservesScopedDraftForReturn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val vm = DiscussionsViewModel("o", "r", FakeRepo(), saved, 10)
        advanceUntilIdle()
        vm.setComposing(true)
        vm.edit("Account A title", "Account A private draft", "cat")
        vm.cancelPendingRequests()
        assertEquals(DiscussionsUiState(), vm.state.value)
        val other = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 20)
        advanceUntilIdle()
        assertEquals("", other.state.value.body)
        vm.restorePendingDraft()
        assertEquals("Account A private draft", vm.state.value.body)
        assertTrue(vm.state.value.composing)
        assertTrue(vm.state.value.items.isEmpty())
        val recreated = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("Account A private draft", recreated.state.value.body)
    }

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
        assertEquals("Private text", restored.state.value.body)
        assertTrue(restored.state.value.composing)
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

    @Test fun draftsForTwoAccountsSurviveTheSameSavedStateHandleAndSuccessfulPublish() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val first = DiscussionsViewModel("o", "r", FakeRepo(), saved, 10)
        advanceUntilIdle()
        first.setComposing(true); first.edit("First", "First private draft", "cat")
        first.cancelPendingRequests(clearAccountData = false)
        val second = DiscussionsViewModel("o", "r", FakeRepo(), saved, 20)
        advanceUntilIdle()
        assertEquals("", second.state.value.body)
        second.setComposing(true); second.edit("Second", "Second private draft", "cat")
        val firstRestored = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("First private draft", firstRestored.state.value.body)
        assertTrue(firstRestored.state.value.composing)
        second.create(); advanceUntilIdle()
        val recreatedFirst = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        val recreatedSecond = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 20)
        advanceUntilIdle()
        assertEquals("First private draft", recreatedFirst.state.value.body)
        assertEquals("", recreatedSecond.state.value.body)
        assertFalse(recreatedSecond.state.value.composing)
    }

    @Test fun backgroundSwitchSuppressesLatePublishAndRestoresOnlyTheReturningAccountDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession("same-login").let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val bob = GithubSession.SignedIn(alice.account.copy(id = 20))
        val sessions = MutableStateFlow<GithubSession>(alice)
        val saved = SavedStateHandle()
        val gate = CompletableDeferred<Result<GithubDiscussion>>()
        val repo = FakeRepo().apply { createRequest = { withContext(NonCancellable) { gate.await() } } }
        val first = DiscussionsViewModel("o", "r", repo, saved, 10, sessions)
        advanceUntilIdle()
        first.setComposing(true); first.edit("First", "Unpublished private draft", "cat")
        first.create(); runCurrent()
        first.cancelPendingRequests(clearAccountData = false)
        sessions.value = bob
        runCurrent()
        assertEquals(DiscussionsUiState(), first.state.value)
        val second = DiscussionsViewModel("o", "r", repo, saved, 20, sessions)
        advanceUntilIdle()
        second.setComposing(true); second.edit("Second", "Another account draft", "cat")
        first.restorePendingDraft(); first.create(); first.load(); first.loadCategories()
        gate.complete(Result.success(GithubDiscussion("late", 99, "Late", "Private", "https://github.com/o/r/discussions/99", "alice", GithubDiscussionCategory("cat", "Q&A", true), 0, false)))
        advanceUntilIdle()
        assertEquals(DiscussionsUiState(), first.state.value)
        assertEquals("Another account draft", second.state.value.body)
        assertNull(second.state.value.created)
        val recreated = DiscussionsViewModel("o", "r", repo, snapshot(saved), 10, sessions)
        assertEquals(DiscussionsUiState(), recreated.state.value)
        sessions.value = alice
        advanceUntilIdle()
        listOf(first, recreated).forEach {
            assertEquals("Unpublished private draft", it.state.value.body)
            assertTrue(it.state.value.composing)
            assertFalse(it.state.value.submitting)
            assertNull(it.state.value.created)
            assertFalse(it.state.value.items.any { row -> row.id == "late" })
        }
        assertEquals(DiscussionsUiState(), second.state.value)
        assertEquals(1, repo.createCalls)
    }

    @Test fun latePrivateReadsCannotReplaceDataFetchedAfterAccountReturns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession().let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val sessions = MutableStateFlow<GithubSession>(alice)
        val list = CompletableDeferred<Result<GithubDiscussionPage>>()
        val categories = CompletableDeferred<Result<List<GithubDiscussionCategory>>>()
        val repo = FakeRepo().apply {
            listRequest = { withContext(NonCancellable) { list.await() } }
            categoriesRequest = { withContext(NonCancellable) { categories.await() } }
        }
        val vm = DiscussionsViewModel("o", "r", repo, accountId = 10, sessions = sessions)
        runCurrent()
        sessions.value = GithubSession.SignedIn(alice.account.copy(id = 20))
        runCurrent()
        assertEquals(DiscussionsUiState(), vm.state.value)
        repo.listRequest = null; repo.categoriesRequest = null
        sessions.value = alice
        runCurrent()
        assertEquals("R", vm.state.value.repositoryId)
        list.complete(Result.success(GithubDiscussionPage("OLD-PRIVATE", emptyList(), "old-cursor")))
        categories.complete(Result.success(listOf(GithubDiscussionCategory("old-category", "Private", true))))
        advanceUntilIdle()
        assertEquals("R", vm.state.value.repositoryId)
        assertEquals("next", vm.state.value.nextCursor)
        assertEquals("cat", vm.state.value.categories.single().id)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test fun unavailableAndAnonymousSessionsCannotPublishOrRestoreAnAccountDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession().let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val sessions = MutableStateFlow<GithubSession>(alice)
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("o", "r", repo, accountId = 10, sessions = sessions)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Private", "Account draft", "cat")
        listOf(GithubSession.Loading, GithubSession.Error(true), GithubSession.SignedOut).forEach { session ->
            sessions.value = session
            runCurrent()
            vm.restorePendingDraft(); vm.setComposing(true); vm.edit("Wrong", "Wrong", "cat")
            vm.create(); vm.load(); vm.loadCategories()
            advanceUntilIdle()
            assertEquals(DiscussionsUiState(), vm.state.value)
        }
        assertEquals(1, repo.listCalls)
        assertEquals(0, repo.createCalls)
        val publicVm = DiscussionsViewModel("o", "r", repo, sessions = sessions)
        advanceUntilIdle()
        assertEquals("R", publicVm.state.value.repositoryId)
        publicVm.setComposing(true); publicVm.edit("Anonymous", "No write", "cat"); publicVm.create()
        assertFalse(publicVm.state.value.composing)
        assertEquals("", publicVm.state.value.body)
        assertEquals(0, repo.createCalls)
        sessions.value = alice
        advanceUntilIdle()
        assertEquals("Account draft", vm.state.value.body)
        assertTrue(vm.state.value.composing)
        assertEquals(DiscussionsUiState(), publicVm.state.value)
    }

    @Test fun legacyDraftMigratesOnlyForItsOwnerAndSurvivesAnUnrelatedRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle(mapOf(
            "discussion.scope" to "10:o/r",
            "discussion.draft" to arrayListOf("Legacy", "Private legacy text", "cat"),
            "discussion.composing" to true
        ))
        val other = DiscussionsViewModel("o", "r", FakeRepo(), saved, 20)
        advanceUntilIdle()
        assertEquals("", other.state.value.body)
        val original = DiscussionsViewModel("o", "r", FakeRepo(), saved, 10)
        advanceUntilIdle()
        assertEquals("Private legacy text", original.state.value.body)
        assertTrue(original.state.value.composing)
        val recreated = DiscussionsViewModel("o", "r", FakeRepo(), snapshot(saved), 10)
        advanceUntilIdle()
        assertEquals("Private legacy text", recreated.state.value.body)
        assertFalse(saved.contains("discussion.scope"))
    }

    @Test fun liveAccountSwitchRejectsSubmissionAndDraftAccessBeforeCollectorRuns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession().let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val bob = GithubSession.SignedIn(alice.account.copy(id = 20))
        val sessions = MutableStateFlow<GithubSession>(alice)
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("o", "r", repo, accountId = 10, sessions = sessions)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Private title", "Original private draft", "cat")
        sessions.value = bob
        // No scheduler advance: the collector still holds Alice when these entry points run.
        vm.create(); vm.edit("Wrong", "Wrong account edit", "cat"); vm.restorePendingDraft()
        vm.setComposing(true); vm.load(); vm.loadCategories()
        assertEquals(DiscussionsUiState(), vm.state.value)
        advanceUntilIdle()
        assertEquals(0, repo.createCalls)
        assertEquals(1, repo.listCalls)
        sessions.value = alice
        vm.restorePendingDraft()
        assertEquals("Original private draft", vm.state.value.body)
        assertTrue(vm.state.value.composing)
        advanceUntilIdle()
    }

    @Test fun requestQueuedBeforeAccountSwitchNeverStartsUnderReplacementCredentials() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession().let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val sessions = MutableStateFlow<GithubSession>(alice)
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("o", "r", repo, accountId = 10, sessions = sessions)
        advanceUntilIdle()
        vm.edit("Private", "Keep this", "cat")
        vm.create()
        vm.load()
        // The requests are queued before the collector receives the account switch.
        sessions.value = GithubSession.SignedIn(alice.account.copy(id = 20))
        advanceUntilIdle()
        assertEquals(0, repo.createCalls)
        assertEquals(1, repo.listCalls)
        assertEquals(DiscussionsUiState(), vm.state.value)
        sessions.value = alice
        advanceUntilIdle()
        assertEquals("Keep this", vm.state.value.body)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun liveIdentityIsCheckedBeforeSuccessfulPublishCanConsumeTheDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alice = signedInGithubSession().let { GithubSession.SignedIn(it.account.copy(id = 10)) }
        val sessions = MutableStateFlow<GithubSession>(alice)
        val saved = SavedStateHandle()
        val repo = FakeRepo()
        val vm = DiscussionsViewModel("o", "r", repo, saved, 10, sessions)
        advanceUntilIdle()
        vm.setComposing(true); vm.edit("Private", "Keep after late success", "cat")
        repo.createRequest = {
            // Change identity inside the request, before a queued collector can run.
            sessions.value = GithubSession.SignedIn(alice.account.copy(id = 20))
            Result.success(GithubDiscussion("old-publish", 9, "Old", "Old body", "https://github.com/o/r/discussions/9", "alice", GithubDiscussionCategory("cat", "Q&A", true), 0, false))
        }
        vm.create(); advanceUntilIdle()
        assertEquals(DiscussionsUiState(), vm.state.value)
        sessions.value = alice
        advanceUntilIdle()
        assertEquals("Keep after late success", vm.state.value.body)
        assertTrue(vm.state.value.composing)
        assertNull(vm.state.value.created)
        val recreated = DiscussionsViewModel("o", "r", repo, snapshot(saved), 10, sessions)
        assertEquals("Keep after late success", recreated.state.value.body)
        assertEquals(1, repo.createCalls)
        advanceUntilIdle()
    }

    @Test fun returningAfterCancelledInitialReadsRetriesThemAndIgnoresTheirLateResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val list = CompletableDeferred<Result<GithubDiscussionPage>>()
        val categories = CompletableDeferred<Result<List<GithubDiscussionCategory>>>()
        val repo = FakeRepo().apply {
            listRequest = { withContext(NonCancellable) { list.await() } }
            categoriesRequest = { withContext(NonCancellable) { categories.await() } }
        }
        val vm = DiscussionsViewModel("o", "r", repo)
        runCurrent()
        vm.edit("Draft", "Preserve on return", null)
        vm.cancelPendingRequests(clearAccountData = false)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.repositoryId)
        repo.listRequest = null; repo.categoriesRequest = null
        // Match the list destination's on-return behavior.
        vm.restorePendingDraft()
        if (vm.state.value.repositoryId == null && !vm.state.value.loading) vm.load()
        if (vm.state.value.categories.isEmpty()) vm.loadCategories()
        runCurrent()
        assertEquals("R", vm.state.value.repositoryId)
        assertEquals("cat", vm.state.value.categories.single().id)
        assertEquals("Preserve on return", vm.state.value.body)
        assertEquals(2, repo.listCalls)
        list.complete(Result.success(GithubDiscussionPage("CANCELLED", emptyList(), null)))
        categories.complete(Result.success(emptyList()))
        advanceUntilIdle()
        assertEquals("R", vm.state.value.repositoryId)
        assertEquals("cat", vm.state.value.categories.single().id)
        assertEquals("Preserve on return", vm.state.value.body)
    }

    private class FakeRepo : GithubDiscussionsRepository {
        var createGate: CompletableDeferred<Result<GithubDiscussion>>? = null
        var createRequest: (suspend () -> Result<GithubDiscussion>)? = null
        var listRequest: (suspend () -> Result<GithubDiscussionPage>)? = null
        var categoriesRequest: (suspend () -> Result<List<GithubDiscussionCategory>>)? = null
        var failCategories = false
        var listCalls = 0; var createCalls = 0
        private val cat = GithubDiscussionCategory("cat", "Q&A", true)
        private fun item(id: String, number: Int) = GithubDiscussion(id, number, "Title $number", "Body", "https://github.com/o/r/discussions/$number", "user", cat, 0, false)
        override suspend fun list(owner: String, name: String, cursor: String?): Result<GithubDiscussionPage> {
            listCalls++
            return listRequest?.invoke() ?: Result.success(GithubDiscussionPage("R",
                if (cursor == null) listOf(item("1", 1)) else listOf(item("1", 1), item("2", 2)),
                if (cursor == null) "next" else null))
        }
        override suspend fun categories(owner: String, name: String): Result<List<GithubDiscussionCategory>> =
            categoriesRequest?.invoke() ?: if (failCategories) Result.failure(java.io.IOException("offline")) else Result.success(listOf(cat))
        override suspend fun create(repositoryId: String, categoryId: String, title: String, body: String): Result<GithubDiscussion> {
            createCalls++
            return createRequest?.invoke() ?: createGate?.await() ?: Result.success(item("3", 3))
        }
        override suspend fun reply(discussionId: String, body: String) = Result.success("comment")
    }
}
