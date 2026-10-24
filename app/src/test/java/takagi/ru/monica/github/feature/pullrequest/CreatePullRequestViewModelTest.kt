package takagi.ru.monica.github.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePullRequestViewModelTest {
    @After fun reset() { Dispatchers.resetMain() }

    @Test fun templatesOnlyReplaceTheDescriptionTheUserConfirmed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val template = GithubPullRequestTemplate("owner/repo", ".github/PULL_REQUEST_TEMPLATE.md", "## Summary")
        val saved = SavedStateHandle()
        var fail = true
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("not creating") }, saved,
            readTemplates = { if (fail) Result.failure(java.io.IOException("offline")) else Result.success(listOf(template)) })
        advanceUntilIdle()
        vm.edit(vm.state.value.copy(body = "My text", title = "Title"))
        vm.loadTemplates(); advanceUntilIdle()
        assertTrue(vm.state.value.templatesError)
        assertEquals("My text", vm.state.value.body)
        fail = false; vm.loadTemplates(); advanceUntilIdle()
        assertEquals("My text", vm.state.value.body)
        vm.edit(vm.state.value.copy(body = "Newer text"))
        vm.applyTemplate(template.id, "My text")
        assertEquals("Newer text", vm.state.value.body)
        vm.applyTemplate(template.id, "Newer text")
        assertEquals("## Summary", vm.state.value.body)
        assertEquals("## Summary", saved.get<String>("body"))
        assertEquals("Title", vm.state.value.title)
    }

    @Test fun previewPassesTheSelectedForkAndRejectsUnqualifiedForkInput() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val inputs = mutableListOf<Triple<String, String, String?>>()
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("not creating") }, SavedStateHandle(),
            { base, head, fork -> inputs += Triple(base, head, fork); Result.success(GithubBranchComparison("ahead", 1, 0, 1, emptyList(), false)) })
        advanceUntilIdle()
        vm.edit(vm.state.value.copy(base = " main ", head = "alice:feature", headRepository = " fork "))
        vm.preview(); advanceUntilIdle()
        assertEquals(listOf(Triple("main", "alice:feature", "fork")), inputs)
        vm.edit(vm.state.value.copy(head = "feature"))
        vm.preview(); advanceUntilIdle()
        assertEquals(1, inputs.size)
        assertNull(vm.state.value.comparison)
    }

    @Test fun switchingBranchesRejectsLateComparisonButEditingTitleKeepsPreview() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Result<GithubBranchComparison>>()
        var calls = 0
        val fresh = GithubBranchComparison("ahead", 2, 0, 2, emptyList(), false)
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("not creating") }, SavedStateHandle(),
            { _, _, _ -> if (++calls == 1) withContext(NonCancellable) { gate.await() } else Result.success(fresh) })
        advanceUntilIdle()
        vm.edit(vm.state.value.copy(base = "main", head = "old"))
        vm.preview(); runCurrent()
        vm.edit(vm.state.value.copy(head = "new"))
        assertNull(vm.state.value.comparison); assertFalse(vm.state.value.comparing)
        vm.preview(); runCurrent()
        gate.complete(Result.success(fresh.copy(aheadBy = 99))); advanceUntilIdle()
        assertEquals(2, vm.state.value.comparison!!.aheadBy)
        vm.edit(vm.state.value.copy(title = "New title"))
        assertEquals(fresh, vm.state.value.comparison)
        vm.edit(vm.state.value.copy(headRepository = "fork"))
        assertNull(vm.state.value.comparison)
    }

    @Test fun failedComparisonCanRetryWithoutLosingDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail = true
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("not creating") }, SavedStateHandle(),
            { _, _, _ -> if (fail) Result.failure(java.io.IOException("offline")) else Result.success(GithubBranchComparison("behind", 0, 2, 0, emptyList(), false)) })
        advanceUntilIdle()
        vm.edit(vm.state.value.copy(title = "Keep", base = "main", head = "feature"))
        vm.preview(); advanceUntilIdle(); assertTrue(vm.state.value.compareError)
        fail = false; vm.preview(); advanceUntilIdle()
        assertFalse(vm.state.value.compareError)
        assertEquals("Keep", vm.state.value.title)
        assertEquals(0, vm.state.value.comparison!!.aheadBy)
    }

    @Test fun successClearsSavedInputAndEmitsOneConsumableDestination() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val created = GithubPullRequest(
            id = 10, number = 10, title = "Created", body = "Body", state = GithubPullRequestState.OPEN,
            isDraft = true, isMerged = false, mergeable = null, mergeableState = null,
            author = GithubUserSummary("sample", null, "https://github.com/sample"),
            labels = emptyList(), assignees = emptyList(), requestedReviewers = emptyList(),
            head = GithubPullRequestRef("feature", "feature", "head", "sample/repo"),
            base = GithubPullRequestRef("main", "main", "base", "sample/repo"),
            comments = 0, reviewComments = 0, commits = 1, additions = 1, deletions = 0, changedFiles = 1,
            createdAt = "", updatedAt = "", closedAt = null, mergedAt = null, htmlUrl = "https://github.com/sample/repo/pull/10")
        val saved = SavedStateHandle()
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { Result.success(created) }, saved)
        advanceUntilIdle()
        vm.edit(vm.state.value.copy(title = "Title", body = "Body", base = "main", head = "feature", draft = true))
        vm.submit(); advanceUntilIdle()
        assertEquals(created, vm.state.value.created)
        assertEquals("", vm.state.value.title)
        assertEquals("", saved.get<String>("body"))
        assertFalse(vm.state.value.submitting)
        vm.consumeCreated(); assertNull(vm.state.value.created)
        val restored = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("must not submit") },
            SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("", restored.state.value.title)
        assertNull(restored.state.value.created)
    }

    @Test fun preservesFailedDraftAndPreventsConcurrentOrInvalidSubmissions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Result<GithubPullRequest>>()
        var calls = 0
        val saved = SavedStateHandle()
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { calls++; gate.await() }, saved)
        advanceUntilIdle()
        vm.submit(); runCurrent(); assertEquals(0, calls)
        vm.edit(vm.state.value.copy(title = "Title", body = "Keep body", base = "main", head = "feature", draft = true))
        vm.submit(); runCurrent(); vm.submit()
        vm.edit(vm.state.value.copy(body = "Changed during request"))
        assertEquals(1, calls)
        assertEquals("Keep body", vm.state.value.body)
        gate.complete(Result.failure(java.io.IOException("offline"))); advanceUntilIdle()
        assertTrue(vm.state.value.failed); assertFalse(vm.state.value.submitting)
        assertEquals("Keep body", saved.get<String>("body"))
        val restored = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("must not auto-submit") },
            SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertTrue(restored.state.value.draft)
        assertEquals("feature", restored.state.value.head)
        assertEquals("Keep body", restored.state.value.body)
    }

    @Test fun branchPaginationRetriesWithoutLosingInput() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail = true
        val pages = mutableListOf<Int>()
        val vm = CreatePullRequestViewModel({ page ->
            pages += page
            if (fail) Result.failure(java.io.IOException("offline"))
            else Result.success(GithubPage(listOf(GithubBranch("branch$page", "sha", false)), if (page == 1) 2 else null))
        }, { error("not submitting") }, SavedStateHandle())
        advanceUntilIdle(); assertTrue(vm.state.value.branchesError)
        vm.edit(vm.state.value.copy(title = "Draft"))
        fail = false; vm.loadBranches(); advanceUntilIdle()
        vm.loadBranches(); advanceUntilIdle()
        assertEquals(listOf(1, 1, 2), pages)
        assertEquals(listOf("branch1", "branch2"), vm.state.value.branches)
        assertEquals("Draft", vm.state.value.title)
        assertNull(vm.state.value.branchPage)
    }
    @Test fun permanentAccountObserverClearsRetainedPageAndRestoresOnlyItsOwnSavedDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        val saved = SavedStateHandle(mapOf("title" to "Unowned legacy draft", "body" to "Unknown owner"))
        var reads = 0
        val vm = CreatePullRequestViewModel({ reads++; Result.success(GithubPage(listOf(GithubBranch("private-a", "sha", false)), null)) },
            { error("must not submit") }, saved, sessions = sessions, expectedAccountId = 1)
        assertEquals("", vm.state.value.title)
        runCurrent()
        assertEquals(1L, vm.state.value.viewerAccountId)
        assertEquals("", vm.state.value.body)
        vm.edit(vm.state.value.copy(title = "A title", body = "A private body", base = "main", head = "feature", draft = true))
        val previous = vm.state.value
        sessions.value = signedIn(2)
        runCurrent()
        assertFalse(vm.state.value.accountReady)
        assertEquals("", vm.state.value.title)
        assertTrue(vm.state.value.branches.isEmpty())
        vm.edit(previous.copy(title = "stale callback"))
        vm.submit(); vm.preview(); vm.loadBranches(); vm.loadTemplates()
        runCurrent()
        assertEquals(1, reads)
        assertEquals("", vm.state.value.title)
        // Even an accidentally shared restored SavedStateHandle cannot expose A's draft to B.
        val copyForB = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val vmB = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("must not submit") },
            copyForB, sessions = sessions, expectedAccountId = 2)
        runCurrent()
        assertEquals("", vmB.state.value.body)
        vmB.edit(vmB.state.value.copy(title = "B title", body = "B private body"))
        sessions.value = signedIn(1)
        runCurrent()
        assertEquals("A title", vm.state.value.title)
        assertEquals("A private body", vm.state.value.body)
        assertTrue(vm.state.value.draft)
        assertEquals("", vmB.state.value.body)
        val restored = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) }, { error("must not auto-submit") },
            SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }), sessions = sessions, expectedAccountId = 1)
        runCurrent()
        assertEquals("A title", restored.state.value.title)
        assertEquals("feature", restored.state.value.head)
        assertFalse(restored.state.value.submitting)
        assertNull(restored.state.value.created)
    }

    @Test fun lateBranchesTemplatesAndComparisonCannotReturnToRetainedPageForAnotherAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        lateinit var branches: Continuation<Result<GithubPage<GithubBranch>>>
        lateinit var templates: Continuation<Result<List<GithubPullRequestTemplate>>>
        lateinit var comparison: Continuation<Result<GithubBranchComparison>>
        val vm = CreatePullRequestViewModel({ suspendCoroutine { branches = it } }, { error("must not submit") }, SavedStateHandle(),
            compare = { _, _, _ -> suspendCoroutine { comparison = it } },
            readTemplates = { suspendCoroutine { templates = it } }, sessions = sessions, expectedAccountId = 1)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "A", base = "main", head = "feature"))
        vm.loadTemplates(); vm.preview(); runCurrent()
        sessions.value = signedIn(2)
        runCurrent()
        branches.resume(Result.success(GithubPage(listOf(GithubBranch("secret", "sha", false)), null)))
        templates.resume(Result.success(listOf(GithubPullRequestTemplate("o/r", "secret.md", "private template"))))
        comparison.resume(Result.success(GithubBranchComparison("ahead", 1, 0, 1, emptyList(), false)))
        runCurrent()
        assertTrue(vm.state.value.branches.isEmpty())
        assertTrue(vm.state.value.templates.isEmpty())
        assertNull(vm.state.value.comparison)
        assertFalse(vm.state.value.accountReady)
        assertFalse(vm.state.value.failed)
        assertEquals("", vm.state.value.title)
    }

    @Test fun oldSuccessCannotEmitDestinationEraseSavedDraftOrNavigateAfterReturningToAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        val saved = SavedStateHandle()
        lateinit var completion: Continuation<Result<GithubPullRequest>>
        var submissions = 0
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) },
            { submissions++; suspendCoroutine { completion = it } }, saved, sessions = sessions, expectedAccountId = 1)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "A title", body = "A body", base = "main", head = "feature"))
        vm.submit(); runCurrent()
        sessions.value = signedIn(2)
        runCurrent()
        vm.submit()
        completion.resume(Result.success(createdPullRequest()))
        runCurrent()
        assertEquals(1, submissions)
        assertNull(vm.state.value.created)
        assertNull(vm.consumeCreatedForAccount(2))
        sessions.value = signedIn(1)
        runCurrent()
        assertEquals("A title", vm.state.value.title)
        assertEquals("A body", vm.state.value.body)
        assertNull(vm.consumeCreatedForAccount(1))
        assertFalse(vm.state.value.submitting)
    }

    @Test fun successIsConsumedOnlyByOwningAccountAndDisposalDoesNotReplayNavigation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        var submissions = 0
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) },
            { submissions++; Result.success(createdPullRequest()) }, SavedStateHandle(), sessions = sessions, expectedAccountId = 1)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "A", base = "main", head = "feature"))
        vm.submit(); runCurrent()
        assertNull(vm.consumeCreatedForAccount(2))
        assertNotNull(vm.state.value.created)
        assertEquals(createdPullRequest(), vm.consumeCreatedForAccount(1))
        assertNull(vm.consumeCreatedForAccount(1))
        vm.edit(vm.state.value.copy(title = "Another", base = "main", head = "feature"))
        vm.submit(); runCurrent()
        vm.cancel() // The retained destination left composition before its result could navigate.
        vm.onEntered(); runCurrent()
        assertNull(vm.consumeCreatedForAccount(1))
        assertEquals(2, submissions)
        sessions.value = GithubSession.SignedOut
        runCurrent()
        assertFalse(vm.state.value.accountReady)
        sessions.value = signedIn(1)
        runCurrent()
        assertTrue(vm.state.value.accountReady) // cancel() did not cancel the auth observer.
        assertNull(vm.state.value.created)
    }

    @Test fun reenteringRetainedPageRetriesCancelledFirstBranchPageWithoutResubmitting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        val pending = CompletableDeferred<Result<GithubPage<GithubBranch>>>()
        var reads = 0
        val vm = CreatePullRequestViewModel({
            if (++reads == 1) pending.await()
            else Result.success(GithubPage(listOf(GithubBranch("fresh", "sha", false)), null))
        }, { error("must not submit") }, SavedStateHandle(), sessions = sessions, expectedAccountId = 1)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "Saved A draft"))
        vm.cancel()
        vm.onEntered(); runCurrent()
        assertEquals(2, reads)
        assertEquals(listOf("fresh"), vm.state.value.branches)
        assertEquals("Saved A draft", vm.state.value.title)
        assertNull(vm.state.value.created)
    }


    @Test fun changedLiveSessionBlocksOldSubmitBeforeCollectorRunsAndRejectsStaleEditorState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        var submissions = 0
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) },
            { submissions++; Result.success(createdPullRequest()) }, SavedStateHandle(), sessions = sessions)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "A title", body = "A private body", base = "main", head = "feature"))
        val previous = vm.state.value
        sessions.value = signedIn(2)
        vm.submit() // Deliberately before runCurrent(): the permanent collector still holds A's state.
        runCurrent()
        assertEquals(0, submissions)
        assertEquals(2L, vm.state.value.viewerAccountId)
        assertEquals("", vm.state.value.body)
        vm.edit(previous)
        assertEquals("", vm.state.value.body)
        vm.edit(vm.state.value.copy(title = "B title", base = "main", head = "feature"))
        vm.submit(); runCurrent()
        assertEquals(1, submissions)
        assertEquals(createdPullRequest(), vm.consumeCreatedForAccount(2))
    }


    @Test fun queuedSubmitDoesNotStartAfterLiveAccountChangesBeforeAuthCollectorRuns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        var submissions = 0
        val vm = CreatePullRequestViewModel({ Result.success(GithubPage(emptyList(), null)) },
            { submissions++; Result.success(createdPullRequest()) }, SavedStateHandle(),
            sessions = sessions, expectedAccountId = 1)
        runCurrent()
        vm.edit(vm.state.value.copy(title = "A private title", body = "A private body", base = "main", head = "feature"))
        vm.submit()
        assertTrue(vm.state.value.submitting) // The request passed its entry guard and is already queued.
        assertEquals(0, submissions)
        // The queued write is ahead of the collector in this scheduler's queue.
        sessions.value = signedIn(2)
        runCurrent()
        assertEquals(0, submissions)
        assertFalse(vm.state.value.accountReady)
        assertNull(vm.state.value.created)
        sessions.value = signedIn(1)
        runCurrent()
        assertEquals("A private body", vm.state.value.body)
        assertNull(vm.state.value.created)
    }

    @Test fun queuedPrivateReadsDoNotStartAfterLiveAccountChangesBeforeAuthCollectorRuns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MutableStateFlow<GithubSession>(signedIn(1))
        val pages = mutableListOf<Int>()
        var templateReads = 0
        var comparisons = 0
        val vm = CreatePullRequestViewModel({ page ->
            pages += page
            Result.success(GithubPage(listOf(GithubBranch("private-a", "sha", false)), 2))
        }, { error("must not submit") }, SavedStateHandle(), compare = { _, _, _ ->
            comparisons++
            Result.success(GithubBranchComparison("ahead", 1, 0, 1, emptyList(), false))
        }, readTemplates = {
            templateReads++
            Result.success(emptyList())
        }, sessions = sessions, expectedAccountId = 1)
        runCurrent()
        assertEquals(listOf(1), pages)
        vm.edit(vm.state.value.copy(title = "A draft", base = "main", head = "feature"))
        vm.loadBranches(); vm.loadTemplates(); vm.preview()
        assertTrue(vm.state.value.loadingBranches)
        assertTrue(vm.state.value.loadingTemplates)
        assertTrue(vm.state.value.comparing)
        sessions.value = signedIn(2) // All three request coroutines are queued before the collector.
        runCurrent()
        assertEquals(listOf(1), pages)
        assertEquals(0, templateReads)
        assertEquals(0, comparisons)
        assertTrue(vm.state.value.branches.isEmpty())
        assertTrue(vm.state.value.templates.isEmpty())
        assertNull(vm.state.value.comparison)
        assertFalse(vm.state.value.accountReady)
    }

    private fun signedIn(id: Long) = GithubSession.SignedIn(
        GithubAccount(id, "same-login", null, null, "", "", 0, 0, 0))

    private fun createdPullRequest() = GithubPullRequest(
            id = 10, number = 10, title = "Created", body = "Body", state = GithubPullRequestState.OPEN,
            isDraft = true, isMerged = false, mergeable = null, mergeableState = null,
            author = GithubUserSummary("sample", null, "https://github.com/sample"),
            labels = emptyList(), assignees = emptyList(), requestedReviewers = emptyList(),
            head = GithubPullRequestRef("feature", "feature", "head", "sample/repo"),
            base = GithubPullRequestRef("main", "main", "base", "sample/repo"),
            comments = 0, reviewComments = 0, commits = 1, additions = 1, deletions = 0, changedFiles = 1,
            createdAt = "", updatedAt = "", closedAt = null, mergedAt = null, htmlUrl = "https://github.com/sample/repo/pull/10")

}
