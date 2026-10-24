package takagi.ru.monica.github.feature.discussions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = MutableStateFlow<GithubSession>(signedIn(1))
    private val discussion = GithubDiscussion("D1", 7, "Title", "Private body", "https://github.com/o/r/discussions/7",
        "same-login", GithubDiscussionCategory("Q", "Q&A", true), 1, false, true)
    private val repo = Repository()

    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private fun vm(saved: SavedStateHandle = SavedStateHandle(), accountId: Long? = 1, number: Int = 7) =
        DiscussionDetailViewModel("o", "r", number, accountId, repo, sessions, saved)

    @Test fun retainedPageDropsPrivateContentAndRestoresOnlyItsAccountAndTargetDrafts() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved); runCurrent()
        first.saveEdit("A edit", "A body"); first.saveReply("A reply"); first.saveThreadReply("C1", "A thread")
        first.saveCommentEdit("C2", "A comment edit")
        sessions.value = signedIn(2); runCurrent()
        assertNull(first.state.value.discussion)
        assertFalse(first.state.value.accountReady)
        assertEquals(DiscussionDetailDraft(), first.state.value.draft)
        val second = vm(saved, 2); runCurrent()
        assertEquals("", second.state.value.draft.reply)
        second.saveReply("B reply")
        sessions.value = signedIn(1); runCurrent()
        assertEquals("A edit", first.state.value.draft.editTitle)
        assertEquals("A reply", first.state.value.draft.reply)
        assertEquals("A thread", first.state.value.draft.threadReplies["C1"])
        assertEquals("A comment edit", first.state.value.draft.commentEdits["C2"])
        assertEquals("", second.state.value.draft.reply)
        val otherTarget = vm(saved, number = 8); runCurrent()
        assertEquals(DiscussionDetailDraft(), otherTarget.state.value.draft)
    }

    @Test fun savedStateRecreationRestoresTextWithoutSubmittingAnyOperation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved); runCurrent()
        first.saveEdit("", "Changed body"); first.saveReply("Unsent reply"); first.saveThreadReply("C1", "Nested reply")
        first.saveCommentEdit("C2", "Unsent comment edit")
        val restored = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals(first.state.value.draft, restored.state.value.draft)
        assertEquals("", restored.state.value.draft.editTitle)
        assertEquals(0, repo.writes)
    }

    @Test fun liveSwitchBeforeCollectorBlocksEveryOldEntryAndDoesNotOverwriteSavedDraft() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveReply("Keep A")
        val before = repo.reads
        sessions.value = signedIn(2)
        model.saveReply("Wrong account"); model.saveEdit("Wrong", "Wrong"); model.saveThreadReply("C1", "Wrong")
        model.load(); model.onEntered()
        for (call in listOf<suspend () -> Any>(
            { model.reply("stale") }, { model.edit("stale", "stale") }, { model.replyToComment("C1", "stale") },
            { model.editComment("C1", "stale") }, { model.deleteComment("C1") }, { model.markAnswer("C1", true) },
            { model.comments(null) }, { model.replies("C1", null) })) {
            try { call(); fail("Old account request was accepted") } catch (_: CancellationException) { }
        }
        assertEquals(0, repo.writes)
        assertEquals(before, repo.reads)
        sessions.value = signedIn(1); runCurrent()
        assertEquals("Keep A", model.state.value.draft.reply)
    }

    @Test fun queuedWriteCannotUseAnAccountSelectedBeforeItsCoroutineStarts() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        val result = async { model.reply("old account") }
        sessions.value = signedIn(2)
        runCurrent()
        assertTrue(result.isCancelled)
        assertEquals(0, repo.writes)
    }

    @Test fun lateNonCooperativeReadCannotRepopulatePrivateDetailAfterSwitch() = runTest(dispatcher) {
        lateinit var completion: Continuation<Result<GithubDiscussion>>
        repo.detailBlock = { suspendCoroutine { completion = it } }
        val model = vm(); runCurrent()
        sessions.value = signedIn(2); runCurrent()
        completion.resume(Result.success(discussion)); runCurrent()
        assertNull(model.state.value.discussion)
        assertFalse(model.state.value.failed)
        assertFalse(model.state.value.loading)
    }

    @Test fun lateNonCooperativeReplyCannotEraseOriginalDraftAfterAccountReturns() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveReply("Keep original")
        lateinit var completion: Continuation<Result<String>>
        repo.replyBlock = { suspendCoroutine { completion = it } }
        val result = async { model.reply("Keep original") }; runCurrent()
        sessions.value = signedIn(2); runCurrent()
        sessions.value = signedIn(1); runCurrent()
        completion.resume(Result.success("old-success")); runCurrent()
        assertTrue(result.isCancelled)
        assertEquals("Keep original", model.state.value.draft.reply)
        assertEquals(setOf("reply"), model.state.value.unconfirmedOperations)
    }

    @Test fun failedAndDuplicateRepliesRetainDraftAndSuccessfulSendClearsOnlyItsTarget() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveReply("Top draft"); model.saveThreadReply("C1", "Nested draft")
        val pending = CompletableDeferred<Result<String>>()
        repo.replyBlock = { pending.await() }
        val first = async { model.reply("Top draft") }; runCurrent()
        assertTrue(model.reply("Top draft").isFailure)
        assertEquals(1, repo.writes)
        pending.complete(Result.failure(java.io.IOException("response lost"))); runCurrent()
        assertTrue(first.await().isFailure)
        assertEquals("Top draft", model.state.value.draft.reply)
        repo.replyBlock = { Result.success("sent") }
        assertTrue(model.reply("Top draft").isSuccess)
        assertEquals("", model.state.value.draft.reply)
        assertEquals("Nested draft", model.state.value.draft.threadReplies["C1"])
        assertEquals(2, repo.writes)
    }

    @Test fun successfulEditClearsOnlyEditDraftAndHonorsCurrentServerPermission() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveEdit("New", "Body"); model.saveReply("Keep reply")
        assertTrue(model.edit("New", "Body").isSuccess)
        assertEquals("New", model.state.value.discussion?.title)
        assertNull(model.state.value.draft.editTitle)
        assertEquals("Keep reply", model.state.value.draft.reply)
        repo.detailBlock = { Result.success(discussion.copy(canEdit = false)) }
        model.load(); runCurrent()
        assertTrue(model.edit("Not permitted", "body").isFailure)
        assertEquals(1, repo.writes)
        assertTrue(model.state.value.unconfirmedOperations.isEmpty())
    }

    @Test fun uncertainOutcomeSurvivesRecreationAndReadOnlyRefreshUntilExplicitReview() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = vm(saved); runCurrent()
        model.saveReply("Possibly sent")
        repo.replyBlock = { Result.failure(java.io.IOException("response lost")) }
        assertTrue(model.reply("Possibly sent").isFailure)
        assertEquals(setOf("reply"), model.state.value.unconfirmedOperations)
        val restored = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals(setOf("reply"), restored.state.value.unconfirmedOperations)
        val revision = restored.state.value.readRevision
        restored.load(); runCurrent()
        assertEquals(revision + 1, restored.state.value.readRevision)
        assertEquals(setOf("reply"), restored.state.value.unconfirmedOperations)
        assertEquals("Possibly sent", restored.state.value.draft.reply)
        restored.acknowledgeUnconfirmed()
        assertTrue(restored.state.value.unconfirmedOperations.isEmpty())
        assertEquals("Possibly sent", restored.state.value.draft.reply)
        assertEquals(1, repo.writes)
    }

    @Test fun cancelledOldSuccessCannotClearNewUnconfirmedOperationOrItsDraft() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = vm(saved); runCurrent()
        lateinit var oldCompletion: Continuation<Result<String>>
        repo.replyBlock = { suspendCoroutine { oldCompletion = it } }
        val old = async { model.reply("First attempt") }; runCurrent()
        model.cancelPendingRequests()
        val pending = CompletableDeferred<Result<String>>()
        repo.replyBlock = { pending.await() }
        model.saveReply("New attempt")
        val newer = async { model.reply("New attempt") }; runCurrent()
        model.acknowledgeUnconfirmed()
        oldCompletion.resume(Result.success("old-success")); runCurrent()
        assertTrue(old.isCancelled)
        pending.complete(Result.failure(java.io.IOException("response lost"))); runCurrent()
        assertTrue(newer.await().isFailure)
        assertEquals(setOf("reply"), model.state.value.unconfirmedOperations)
        val restored = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals(setOf("reply"), restored.state.value.unconfirmedOperations)
        assertEquals("New attempt", restored.state.value.draft.reply)
        assertEquals(2, repo.writes)
    }

    @Test fun successfulCommentEditClearsOnlyThatCommentAndFailedEditKeepsDraft() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveCommentEdit("C1", "Edited first"); model.saveCommentEdit("C2", "Edited second")
        model.saveReply("Unsent reply")
        assertTrue(model.editComment("C1", "Edited first").isSuccess)
        assertNull(model.state.value.draft.commentEdits["C1"])
        assertEquals("Edited second", model.state.value.draft.commentEdits["C2"])
        assertEquals("Unsent reply", model.state.value.draft.reply)
        repo.commentEditResult = Result.failure(java.io.IOException("response lost"))
        assertTrue(model.editComment("C2", "Edited second").isFailure)
        assertEquals("Edited second", model.state.value.draft.commentEdits["C2"])
    }

    @Test fun disposalCancelsPendingConnectionAndReentryRetriesReadWithoutReplayingWrite() = runTest(dispatcher) {
        val waiting = CompletableDeferred<Result<GithubDiscussion>>()
        repo.detailBlock = { waiting.await() }
        val model = vm(); runCurrent()
        assertTrue(model.state.value.loading)
        model.cancelPendingRequests()
        repo.detailBlock = { Result.success(discussion) }
        model.onEntered(); runCurrent()
        assertEquals(discussion, model.state.value.discussion)
        assertEquals(2, repo.reads)
        assertEquals(0, repo.writes)
    }

    @Test fun signedOutLoadingAndAuthErrorNeverExposeSavedTextOrStartRequests() = runTest(dispatcher) {
        val model = vm(); runCurrent()
        model.saveReply("Private draft")
        val before = repo.reads
        for (session in listOf(GithubSession.Loading, GithubSession.Error(true), GithubSession.SignedOut)) {
            sessions.value = session; runCurrent()
            assertFalse(model.state.value.accountReady)
            assertNull(model.state.value.discussion)
            assertEquals("", model.state.value.draft.reply)
            model.load(); runCurrent()
        }
        assertEquals(before, repo.reads)
        val anonymous = vm(accountId = null); runCurrent()
        assertFalse(anonymous.state.value.accountReady)
        assertEquals(before, repo.reads)
    }

    private inner class Repository : GithubDiscussionsRepository {
        var reads = 0
        var writes = 0
        var detailBlock: suspend () -> Result<GithubDiscussion> = { Result.success(discussion) }
        var replyBlock: suspend () -> Result<String> = { Result.success("new") }
        var commentEditResult: Result<GithubDiscussionComment>? = null
        override suspend fun detail(owner: String, name: String, number: Int): Result<GithubDiscussion> { reads++; return detailBlock() }
        override suspend fun list(owner: String, name: String, cursor: String?) = Result.success(GithubDiscussionPage("R1", emptyList(), null))
        override suspend fun categories(owner: String, name: String) = Result.success(emptyList<GithubDiscussionCategory>())
        override suspend fun create(repositoryId: String, categoryId: String, title: String, body: String) = Result.success(discussion)
        override suspend fun reply(discussionId: String, body: String): Result<String> { writes++; return replyBlock() }
        override suspend fun replyToComment(discussionId: String, commentId: String, body: String): Result<String> { writes++; return replyBlock() }
        override suspend fun edit(id: String, title: String, body: String): Result<GithubDiscussion> {
            writes++; return Result.success(discussion.copy(title = title, body = body))
        }
        override suspend fun comments(id: String, cursor: String?): Result<GithubDiscussionComments> { reads++; return Result.success(GithubDiscussionComments(emptyList(), null)) }
        override suspend fun replies(commentId: String, cursor: String?): Result<GithubDiscussionComments> { reads++; return Result.success(GithubDiscussionComments(emptyList(), null)) }
        override suspend fun editComment(id: String, body: String): Result<GithubDiscussionComment> { writes++; return commentEditResult ?: Result.success(GithubDiscussionComment(id, body, null, false)) }
        override suspend fun deleteComment(id: String): Result<Unit> { writes++; return Result.success(Unit) }
        override suspend fun markAnswer(commentId: String, answered: Boolean): Result<Unit> { writes++; return Result.success(Unit) }
    }

    private fun signedIn(id: Long) = GithubSession.SignedIn(GithubAccount(id, "same-login", null, null, "", "", 0, 0, 0))
}
