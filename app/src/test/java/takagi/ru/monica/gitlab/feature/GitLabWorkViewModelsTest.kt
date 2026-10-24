package takagi.ru.monica.gitlab.feature

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.gitlab.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabWorkViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = GitLabAccount(42, "https://gitlab.com/", "me", "Me", "")
    private val kind = GitLabConversationKind.MERGE_REQUEST
    private val sha = "a".repeat(40)
    private val item = GitLabConversation(900, 3, "Title", "Body", "opened", "alice", 8, "today",
        sourceBranch = "feature", targetBranch = "main", sha = sha, mergeStatus = "mergeable")
    private val note = GitLabNote(81, "new comment", "me", "today", false)
    private val pipeline = GitLabPipeline(17, "main", sha, "failed", "today")
    private val job = GitLabJob(23, 17, "deploy", "production", "manual")
    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun listPagingDeduplicatesAndSearchFilterAndSectionResetServerPage() = runTest(dispatcher) {
        val repo = Repo()
        repo.listBlock = { _, _, _, page -> Result.success(GitLabPage(if (page == 1) listOf(item) else listOf(item, item.copy(id = 901, number = 4)), if (page == 1) 2 else null)) }
        val vm = list(repo)
        runCurrent(); vm.more(); vm.more(); runCurrent()
        assertEquals(listOf(900L, 901L), vm.state.value.conversations.map { it.id })
        assertEquals(listOf(1, 2), repo.listCalls.map { it.page })
        vm.searchChanged("  hello  "); vm.search(); runCurrent()
        assertEquals("hello", repo.listCalls.last().search); assertEquals(1, repo.listCalls.last().page)
        vm.filterChanged("closed"); runCurrent()
        assertEquals("closed", repo.listCalls.last().state)
        vm.sectionChanged(GitLabWorkSection.PIPELINES); runCurrent()
        assertTrue(vm.state.value.conversations.isEmpty()); assertEquals(1, repo.pipelineListCalls)
    }

    @Test fun listFailurePreservesLoadedRowsAndRetryDoesNotRepeatAWritingOperation() = runTest(dispatcher) {
        val repo = Repo()
        repo.listBlock = { _, _, _, page -> if (page == 1) Result.success(GitLabPage(listOf(item), 2))
            else Result.failure(GitLabReadException(GitLabReadError.NETWORK)) }
        val vm = list(repo); runCurrent(); vm.more(); runCurrent()
        assertEquals(listOf(item), vm.state.value.conversations)
        vm.retry(); runCurrent()
        assertEquals(listOf(1, 2, 2), repo.listCalls.map { it.page }); assertEquals(0, repo.createCalls)
    }

    @Test fun listResumeRevalidatesLoadedPagesAtomicallyAndKeepsEditorDraft() = runTest(dispatcher) {
        val repo = Repo()
        val second = item.copy(id = 901, number = 4)
        repo.listBlock = { _, _, _, page -> Result.success(GitLabPage(listOf(if (page == 1) item else second), page + 1)) }
        val vm = list(repo); runCurrent(); vm.more(); runCurrent()
        vm.editor(true); vm.draftChanged(GitLabConversationDraft("Unsaved", "Keep this draft", "feature", "main"))
        val refreshed = CompletableDeferred<Result<GitLabPage<GitLabConversation>>>()
        repo.listBlock = { _, _, _, page -> if (page == 1) Result.success(GitLabPage(listOf(item.copy(title = "Updated")), 7)) else refreshed.await() }
        vm.refresh(); runCurrent()
        assertEquals(listOf(item, second), vm.state.value.conversations)
        assertEquals(3, vm.state.value.nextPage)
        assertTrue(vm.state.value.editorOpen)
        assertEquals("Unsaved", vm.state.value.draft.title)
        refreshed.complete(Result.success(GitLabPage(listOf(second.copy(title = "Changed")), 8))); runCurrent()
        assertEquals(listOf("Updated", "Changed"), vm.state.value.conversations.map { it.title })
        assertEquals(listOf(1, 2, 1, 7), repo.listCalls.map { it.page })
        assertEquals(8, vm.state.value.nextPage)
        assertEquals("Keep this draft", vm.state.value.draft.description)
    }

    @Test fun failedListResumeKeepsWholeWindowAndRetryRefreshesInsteadOfLoadingMore() = runTest(dispatcher) {
        val repo = Repo()
        val second = item.copy(id = 901, number = 4)
        repo.listBlock = { _, _, _, page -> Result.success(GitLabPage(listOf(if (page == 1) item else second), page + 1)) }
        val vm = list(repo); runCurrent(); vm.more(); runCurrent()
        var fail = true
        repo.listBlock = { _, _, _, page -> if (page == 1) Result.success(GitLabPage(listOf(item.copy(title = "Updated")), 7))
            else if (fail) Result.failure(GitLabReadException(GitLabReadError.NETWORK))
            else Result.success(GitLabPage(emptyList(), null)) }
        vm.refresh(); runCurrent()
        assertEquals(listOf(item, second), vm.state.value.conversations)
        assertEquals(3, vm.state.value.nextPage)
        fail = false; vm.retry(); runCurrent()
        assertEquals(listOf(1, 2, 1, 7, 1, 7), repo.listCalls.map { it.page })
        assertEquals(listOf(item.copy(title = "Updated")), vm.state.value.conversations)
        assertNull(vm.state.value.nextPage)
        assertNull(vm.state.value.error)
        assertEquals(0, repo.createCalls)
    }

    @Test fun creationFailureRestoresDraftWithoutSubmittingAndDoesNotRestoreQuickActionApproval() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repo = Repo(); val auth = Auth()
        val vm = list(repo, auth, saved); runCurrent()
        vm.editor(true); vm.draftChanged(GitLabConversationDraft("Title", "/close", "feature", "main"))
        vm.create(); runCurrent(); assertEquals(0, repo.createCalls)
        repo.createBlock = { Result.failure(GitLabReadException(GitLabReadError.PERMISSION)) }
        vm.create(confirmedQuickActions = true); runCurrent()
        assertEquals(1, repo.createCalls); assertEquals("/close", vm.state.value.draft.description)
        assertEquals(GitLabReadError.PERMISSION, vm.state.value.writeError)
        val restored = list(repo, auth, saved); runCurrent()
        assertTrue(restored.state.value.editorOpen); assertFalse(restored.state.value.writing)
        assertFalse(restored.state.value.draft.allowQuickActions)
        restored.create(); runCurrent(); assertEquals(1, repo.createCalls)
        assertNull(restored.state.value.created)
    }

    @Test fun accountSwitchCancelsCreationAndLateResultNeverNavigatesOrClearsSavedDraft() = runTest(dispatcher) {
        val repo = Repo(); val auth = Auth(); val deferred = CompletableDeferred<Result<GitLabConversation>>()
        repo.createBlock = { withContext(NonCancellable) { deferred.await() } }
        val vm = list(repo, auth); runCurrent()
        vm.draftChanged(GitLabConversationDraft("Title", "Body", "feature", "main")); vm.create(); vm.create(); runCurrent()
        assertEquals(1, repo.createCalls)
        auth.accounts.value = GitLabAccounts(listOf(account), "https://other.example/#42"); runCurrent()
        deferred.complete(Result.success(item)); runCurrent()
        assertNull(vm.state.value.account); assertNull(vm.state.value.created); assertFalse(vm.state.value.writing)
        auth.accounts.value = GitLabAccounts(listOf(account), account.key); runCurrent()
        assertEquals("Title", vm.state.value.draft.title)
    }

    @Test fun draftSectionsAndAccountsNeverShareSavedInputs() = runTest(dispatcher) {
        val saved = SavedStateHandle(); val repo = Repo(); val auth = Auth()
        val vm = list(repo, auth, saved); runCurrent()
        vm.draftChanged(GitLabConversationDraft("MR draft", "private", "feature", "main"))
        vm.sectionChanged(GitLabWorkSection.ISSUES); runCurrent()
        assertEquals("", vm.state.value.draft.title)
        vm.sectionChanged(GitLabWorkSection.MERGE_REQUESTS); runCurrent()
        assertEquals("MR draft", vm.state.value.draft.title)
        val other = account.copy(id = 43)
        auth.accounts.value = GitLabAccounts(listOf(account, other), other.key)
        val otherVm = GitLabWorkListViewModel(other.key, 5, GitLabWorkSection.MERGE_REQUESTS, auth, repo, saved); runCurrent()
        assertEquals("", otherVm.state.value.draft.title)
    }

    @Test fun commentIsSingleFlightAndLateOldNotesCannotEraseNewComment() = runTest(dispatcher) {
        val repo = Repo(); val delayedNotes = CompletableDeferred<Result<GitLabPage<GitLabNote>>>()
        repo.notesBlock = { withContext(NonCancellable) { delayedNotes.await() } }
        val vm = conversation(repo); runCurrent()
        vm.commentChanged("new comment"); vm.sendComment(); vm.sendComment(); runCurrent()
        assertEquals(1, repo.commentCalls); assertEquals(listOf(note), vm.state.value.notes)
        delayedNotes.complete(Result.success(GitLabPage(emptyList(), null))); runCurrent()
        assertEquals(listOf(note), vm.state.value.notes); assertEquals("", vm.state.value.comment)
        assertFalse(vm.state.value.writing)
    }

    @Test fun failedCommentSurvivesRecreationAndRefreshingDoesNotSendItAgain() = runTest(dispatcher) {
        val saved = SavedStateHandle(); val repo = Repo()
        repo.commentBlock = { _, _ -> Result.failure(GitLabReadException(GitLabReadError.NETWORK)) }
        val vm = conversation(repo, saved = saved); runCurrent()
        vm.commentChanged("draft comment"); vm.sendComment(); runCurrent()
        assertEquals("draft comment", vm.state.value.comment); assertEquals(1, repo.commentCalls)
        vm.refresh(); runCurrent(); assertEquals(1, repo.commentCalls)
        val restored = conversation(repo, saved = saved); runCurrent()
        assertEquals("draft comment", restored.state.value.comment); assertEquals(1, repo.commentCalls)
        assertFalse(restored.state.value.writing); assertNull(restored.state.value.writeError)
    }

    @Test fun quickActionCommentNeedsConfirmationEveryTimeAndLockedDiscussionCannotPost() = runTest(dispatcher) {
        val repo = Repo(); val vm = conversation(repo); runCurrent()
        vm.commentChanged("/close"); vm.sendComment(); runCurrent(); assertEquals(0, repo.commentCalls)
        vm.sendComment(true); runCurrent(); assertEquals(1, repo.commentCalls); assertTrue(repo.lastCommentApproved)
        repo.item = item.copy(discussionLocked = true); vm.refresh(); runCurrent()
        vm.commentChanged("hello"); vm.sendComment(); runCurrent(); assertEquals(1, repo.commentCalls)
    }

    @Test fun editingPreservesFailedDraftAndOnlyAcceptsCurrentStateOrMergeTarget() = runTest(dispatcher) {
        val repo = Repo(); val vm = conversation(repo); runCurrent()
        vm.editing(true); vm.draftChanged(GitLabConversationDraft("Edited", "new body"))
        repo.editBlock = { Result.failure(GitLabReadException(GitLabReadError.PERMISSION)) }
        vm.save(); runCurrent(); assertEquals("Edited", vm.state.value.draft.title); assertTrue(vm.state.value.editing)
        vm.changeState("closed"); runCurrent(); assertEquals(0, repo.stateCalls)
        vm.merge(GitLabMergeTarget(3, "b".repeat(40), "feature", "main")); runCurrent(); assertEquals(0, repo.mergeCalls)
        vm.merge(GitLabMergeTarget(3, sha, "feature", "main")); runCurrent(); assertEquals(1, repo.mergeCalls)
        assertEquals("merged", vm.state.value.item!!.state)
        assertFalse(vm.state.value.editing)
        assertEquals("Edited", vm.state.value.draft.title)
    }

    @Test fun lateCommentAfterAccountRemovalCannotRepopulateContentOrFeedback() = runTest(dispatcher) {
        val auth = Auth(); val repo = Repo(); val deferred = CompletableDeferred<Result<GitLabNote>>()
        repo.commentBlock = { _, _ -> withContext(NonCancellable) { deferred.await() } }
        val vm = conversation(repo, auth); runCurrent()
        vm.commentChanged("draft comment"); vm.sendComment(); runCurrent()
        auth.accounts.value = GitLabAccounts(); runCurrent()
        deferred.complete(Result.success(note)); runCurrent()
        assertNull(vm.state.value.account); assertNull(vm.state.value.item); assertTrue(vm.state.value.notes.isEmpty())
        assertFalse(vm.state.value.writeSucceeded); assertFalse(vm.state.value.writing)
    }

    @Test fun diffsLoadLazilyAndNotesPagingDeduplicatesAndPreservesErrors() = runTest(dispatcher) {
        val repo = Repo()
        repo.notesBlock = { page -> if (page == 1) Result.success(GitLabPage(listOf(note), 2)) else Result.failure(GitLabReadException(GitLabReadError.NETWORK)) }
        val vm = conversation(repo); runCurrent()
        assertEquals(0, repo.diffCalls); vm.showDiff(true); runCurrent(); assertEquals(1, repo.diffCalls)
        vm.notes(true); runCurrent(); assertEquals(listOf(note), vm.state.value.notes)
        assertEquals(GitLabReadError.NETWORK, vm.state.value.notesError)
        repo.notesBlock = { Result.success(GitLabPage(listOf(note, note.copy(id = 82)), null)) }
        vm.notes(true); runCurrent(); assertEquals(listOf(81L, 82L), vm.state.value.notes.map { it.id })
    }

    @Test fun detailResumeDoesNotDuplicateInitialLoadOrBlankVisibleDiffs() = runTest(dispatcher) {
        val repo = Repo()
        val initial = CompletableDeferred<Result<GitLabConversation>>()
        repo.conversationBlock = { initial.await() }
        val vm = conversation(repo)
        runCurrent()
        vm.refresh(); runCurrent()
        assertEquals(1, repo.conversationCalls)
        initial.complete(Result.success(item)); runCurrent()
        vm.showDiff(true); runCurrent()
        val visible = vm.state.value.diffs
        val resumed = CompletableDeferred<Result<GitLabConversation>>()
        repo.conversationBlock = { resumed.await() }
        vm.refresh(); runCurrent()
        assertEquals(visible, vm.state.value.diffs)
        assertTrue(vm.state.value.showDiff)
        resumed.complete(Result.success(item)); runCurrent()
        assertEquals(visible, vm.state.value.diffs)
    }

    @Test fun pipelineRefreshKeepsLogOpenAndReloadsItWithoutRunningAJob() = runTest(dispatcher) {
        val repo = Repo()
        val vm = pipeline(repo); runCurrent()
        vm.trace(job); runCurrent()
        assertEquals("log", vm.state.value.trace)
        val refreshedLog = CompletableDeferred<Result<String>>()
        repo.traceBlock = { refreshedLog.await() }
        vm.refresh(); runCurrent()
        assertEquals(job, vm.state.value.traceJob)
        assertEquals("log", vm.state.value.trace)
        assertTrue(vm.state.value.traceLoading)
        assertEquals(2, repo.traceCalls)
        refreshedLog.complete(Result.success("updated log")); runCurrent()
        assertEquals("updated log", vm.state.value.trace)
        assertEquals(0, repo.jobActionCalls)
        assertEquals(0, repo.pipelineActionCalls)
    }

    @Test fun permissionFailureWhileRefreshingPipelineClearsPrivateLogAndJobs() = runTest(dispatcher) {
        val repo = Repo()
        val vm = pipeline(repo); runCurrent(); vm.trace(job); runCurrent()
        repo.pipelineBlock = { Result.failure(GitLabReadException(GitLabReadError.PERMISSION)) }
        vm.refresh(); runCurrent()
        assertNull(vm.state.value.pipeline)
        assertNull(vm.state.value.traceJob)
        assertNull(vm.state.value.trace)
        assertTrue(vm.state.value.jobs.isEmpty())
        assertFalse(vm.state.value.traceLoading)
        assertEquals(GitLabReadError.PERMISSION, vm.state.value.error)
    }

    @Test fun pipelineWriteIsSingleFlightAndRefreshAfterFailureOnlyReads() = runTest(dispatcher) {
        val repo = Repo(); val deferred = CompletableDeferred<Result<GitLabPipeline>>()
        repo.pipelineActionBlock = { deferred.await() }
        val vm = pipeline(repo); runCurrent()
        val expected = vm.state.value.pipeline!!
        vm.pipelineAction(expected, GitLabRunAction.RETRY); vm.pipelineAction(expected, GitLabRunAction.RETRY); runCurrent()
        assertEquals(1, repo.pipelineActionCalls)
        deferred.complete(Result.failure(GitLabReadException(GitLabReadError.NETWORK))); runCurrent()
        assertNotNull(vm.state.value.writeError); vm.refresh(); runCurrent(); assertEquals(1, repo.pipelineActionCalls)
    }

    @Test fun jobActionsRequireLoadedIdentityAndTraceRetryNeverRunsAJob() = runTest(dispatcher) {
        val repo = Repo(); val vm = pipeline(repo); runCurrent()
        vm.jobAction(job.copy(id = 999), GitLabRunAction.PLAY); runCurrent(); assertEquals(0, repo.jobActionCalls)
        repo.traceBlock = { Result.failure(GitLabReadException(GitLabReadError.NETWORK)) }
        vm.trace(job); runCurrent(); vm.trace(job); runCurrent()
        assertEquals(2, repo.traceCalls); assertEquals(0, repo.jobActionCalls)
        vm.closeTrace(); vm.jobAction(job, GitLabRunAction.PLAY); runCurrent()
        assertEquals(1, repo.jobActionCalls); assertNull(vm.state.value.trace)
    }

    @Test fun accountSwitchErasesLogsAndIgnoresLateTraceAndJobWrite() = runTest(dispatcher) {
        val repo = Repo(); val auth = Auth(); val deferred = CompletableDeferred<Result<String>>()
        repo.traceBlock = { withContext(NonCancellable) { deferred.await() } }
        val vm = pipeline(repo, auth); runCurrent(); vm.trace(job); runCurrent()
        auth.accounts.value = GitLabAccounts(); runCurrent()
        deferred.complete(Result.success("private old account log")); runCurrent()
        assertEquals(GitLabPipelineState(), vm.state.value)
        auth.accounts.value = GitLabAccounts(listOf(account), account.key); runCurrent()
        val write = CompletableDeferred<Result<GitLabJob>>()
        repo.jobActionBlock = { withContext(NonCancellable) { write.await() } }
        vm.jobAction(job, GitLabRunAction.PLAY); runCurrent()
        auth.accounts.value = GitLabAccounts(); runCurrent()
        write.complete(Result.success(job.copy(status = "pending"))); runCurrent()
        assertEquals(GitLabPipelineState(), vm.state.value)
    }

    @Test fun inactiveRouteDoesNotReadAndPipelineCreationRequiresExplicitCurrentRef() = runTest(dispatcher) {
        val repo = Repo(); val auth = Auth(); auth.accounts.value = GitLabAccounts()
        val vm = GitLabWorkListViewModel(account.key, 5, GitLabWorkSection.PIPELINES, auth, repo)
        runCurrent(); assertEquals(0, repo.pipelineListCalls)
        auth.accounts.value = GitLabAccounts(listOf(account), account.key); runCurrent()
        vm.searchChanged("main"); vm.runPipeline("other"); runCurrent(); assertEquals(0, repo.runCalls)
        vm.runPipeline("main"); runCurrent(); assertEquals(1, repo.runCalls); assertEquals(17L, vm.state.value.createdPipeline!!.id)
    }

    private fun list(repo: Repo, auth: Auth = Auth(), saved: SavedStateHandle = SavedStateHandle()) =
        GitLabWorkListViewModel(account.key, 5, GitLabWorkSection.MERGE_REQUESTS, auth, repo, saved)
    private fun conversation(repo: Repo, auth: Auth = Auth(), saved: SavedStateHandle = SavedStateHandle()) =
        GitLabConversationViewModel(account.key, 5, kind, 3, auth, repo, saved)
    private fun pipeline(repo: Repo, auth: Auth = Auth()) = GitLabPipelineViewModel(account.key, 5, 17, auth, repo)

    private inner class Auth : GitLabAuthRepository {
        override val accounts = MutableStateFlow(GitLabAccounts(listOf(account), account.key))
        override suspend fun restore() = Result.success(Unit)
        override suspend fun signInWithToken(instance: String, token: String) = Result.success(account)
        override suspend fun startBrowserSignIn(instance: String, clientId: String, allowWrites: Boolean) = Result.success("https://gitlab.com/")
        override suspend fun completeBrowserSignIn(callback: String) = Result.success(account)
        override suspend fun cancelBrowserSignIn() = Unit
        override suspend fun activate(key: String) = Result.success(Unit)
        override suspend fun remove(key: String) = Result.success(Unit)
    }
    private data class ListCall(val kind: GitLabConversationKind, val state: String, val search: String, val page: Int)
    private inner class Repo : GitLabWorkRepository {
        var item = this@GitLabWorkViewModelsTest.item
        val listCalls = mutableListOf<ListCall>()
        var createCalls = 0; var commentCalls = 0; var stateCalls = 0; var mergeCalls = 0; var diffCalls = 0
        var pipelineListCalls = 0; var pipelineActionCalls = 0; var jobActionCalls = 0; var traceCalls = 0; var runCalls = 0
        var lastCommentApproved = false
        var conversationCalls = 0
        var conversationBlock: suspend () -> Result<GitLabConversation> = { Result.success(item) }
        var pipelineBlock: suspend () -> Result<GitLabPipeline> = { Result.success(this@GitLabWorkViewModelsTest.pipeline) }
        var listBlock: suspend (GitLabConversationKind, String, String, Int) -> Result<GitLabPage<GitLabConversation>> = { _, _, _, _ -> Result.success(GitLabPage(emptyList(), null)) }
        var createBlock: suspend (GitLabConversationDraft) -> Result<GitLabConversation> = { Result.success(item) }
        var editBlock: suspend (GitLabConversationDraft) -> Result<GitLabConversation> = { Result.success(item.copy(title = it.title, description = it.description)) }
        var commentBlock: suspend (String, Boolean) -> Result<GitLabNote> = { _, _ -> Result.success(note) }
        var notesBlock: suspend (Int) -> Result<GitLabPage<GitLabNote>> = { Result.success(GitLabPage(emptyList(), null)) }
        var pipelineActionBlock: suspend (GitLabRunAction) -> Result<GitLabPipeline> = { Result.success(this@GitLabWorkViewModelsTest.pipeline.copy(status = "pending")) }
        var jobActionBlock: suspend (GitLabRunAction) -> Result<GitLabJob> = { Result.success(job.copy(status = "pending")) }
        var traceBlock: suspend () -> Result<String> = { Result.success("log") }
        override suspend fun conversations(key: String, projectId: Long, kind: GitLabConversationKind, state: String, search: String, page: Int): Result<GitLabPage<GitLabConversation>> {
            listCalls += ListCall(kind, state, search, page); return listBlock(kind, state, search, page)
        }
        override suspend fun conversation(key: String, projectId: Long, kind: GitLabConversationKind, number: Long): Result<GitLabConversation> {
            conversationCalls++; return conversationBlock()
        }
        override suspend fun create(key: String, projectId: Long, kind: GitLabConversationKind, draft: GitLabConversationDraft): Result<GitLabConversation> { createCalls++; return createBlock(draft) }
        override suspend fun edit(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, draft: GitLabConversationDraft) = editBlock(draft)
        override suspend fun changeState(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, close: Boolean): Result<GitLabConversation> {
            stateCalls++; item = item.copy(state = if (close) "closed" else "opened"); return Result.success(item)
        }
        override suspend fun notes(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, page: Int) = notesBlock(page)
        override suspend fun comment(key: String, projectId: Long, kind: GitLabConversationKind, number: Long, body: String, allowQuickActions: Boolean): Result<GitLabNote> {
            commentCalls++; lastCommentApproved = allowQuickActions; return commentBlock(body, allowQuickActions)
        }
        override suspend fun diffs(key: String, projectId: Long, number: Long, expectedSha: String, page: Int): Result<GitLabPage<GitLabDiff>> {
            diffCalls++; return Result.success(GitLabPage(listOf(GitLabDiff("a", "a", "diff", false)), null))
        }
        override suspend fun merge(key: String, projectId: Long, target: GitLabMergeTarget): Result<GitLabConversation> {
            mergeCalls++; item = item.copy(state = "merged"); return Result.success(item)
        }
        override suspend fun pipelines(key: String, projectId: Long, ref: String, page: Int): Result<GitLabPage<GitLabPipeline>> {
            pipelineListCalls++; return Result.success(GitLabPage(listOf(this@GitLabWorkViewModelsTest.pipeline), null))
        }
        override suspend fun pipeline(key: String, projectId: Long, id: Long) = pipelineBlock()
        override suspend fun jobs(key: String, projectId: Long, pipelineId: Long, page: Int) = Result.success(GitLabPage(listOf(job), null))
        override suspend fun trace(key: String, projectId: Long, pipelineId: Long, jobId: Long): Result<String> { traceCalls++; return traceBlock() }
        override suspend fun runPipeline(key: String, projectId: Long, ref: String): Result<GitLabPipeline> { runCalls++; return Result.success(this@GitLabWorkViewModelsTest.pipeline) }
        override suspend fun pipelineAction(key: String, projectId: Long, id: Long, action: GitLabRunAction): Result<GitLabPipeline> { pipelineActionCalls++; return pipelineActionBlock(action) }
        override suspend fun jobAction(key: String, projectId: Long, pipelineId: Long, jobId: Long, action: GitLabRunAction): Result<GitLabJob> { jobActionCalls++; return jobActionBlock(action) }
    }
}
