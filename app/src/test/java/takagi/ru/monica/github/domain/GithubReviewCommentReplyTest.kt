package takagi.ru.monica.github.domain

import org.junit.Assert.*
import org.junit.Test

class GithubReviewCommentReplyTest {
    @Test fun replyDraftNormalizesTextAndValidatesTargetAndSize() {
        val draft = GithubReviewCommentReplyDraft.fromInput(42, "  Thanks\n已修改  ").getOrThrow()
        assertEquals(42L, draft.threadId)
        assertEquals("Thanks\n已修改", draft.body)
        assertTrue(GithubReviewCommentReplyDraft.fromInput(0, "reply").isFailure)
        assertTrue(GithubReviewCommentReplyDraft.fromInput(-1, "reply").isFailure)
        assertTrue(GithubReviewCommentReplyDraft.fromInput(42, " \n ").isFailure)
        assertTrue(GithubReviewCommentReplyDraft.fromInput(42, "x".repeat(65_537)).isFailure)
        assertTrue(GithubReviewCommentReplyDraft.fromInput(42, "x".repeat(65_536)).isSuccess)
    }

    @Test fun repliesAreGroupedUnderTheirRootEvenWhenTheRootArrivesLater() {
        val reply = comment(12, root = 11)
        val threads = listOf(reply, comment(20), comment(11), reply).reviewThreads()
        assertEquals(listOf(11L, 20L), threads.map { it.id })
        assertEquals(listOf(11L, 12L), threads.first().comments.map { it.id })
        assertTrue(threads.first().hasRoot)
    }

    @Test fun orphanRepliesStayVisibleAndKeepTheCorrectReplyTarget() {
        val thread = listOf(comment(12, root = 11), comment(13, root = 11)).reviewThreads().single()
        assertEquals(11L, thread.id)
        assertFalse(thread.hasRoot)
        assertEquals(listOf(12L, 13L), thread.comments.map { it.id })
    }

    private fun comment(id: Long, root: Long? = null) = GithubPullRequestReviewComment(
        id, "Body", "app.kt", null, null, "RIGHT", "", GithubUserSummary("alice", null, ""),
        "2026-10-21T00:00:00Z", "2026-10-21T00:00:00Z", "", inReplyToId = root
    )
}
