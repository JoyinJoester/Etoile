package takagi.ru.monica.github.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
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
}
