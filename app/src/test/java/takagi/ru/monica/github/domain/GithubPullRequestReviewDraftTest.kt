package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubPullRequestReviewDraftTest {
    @Test
    fun approvalMayOmitBodyButCommentsAndChangeRequestsRequireOne() {
        val approval = GithubPullRequestReviewDraft.fromInput(GithubReviewEvent.APPROVE, "  ").getOrThrow()
        val comment = GithubPullRequestReviewDraft.fromInput(GithubReviewEvent.COMMENT, " Looks good ").getOrThrow()

        assertNull(approval.body)
        assertEquals("Looks good", comment.body)
        assertTrue(GithubPullRequestReviewDraft.fromInput(GithubReviewEvent.COMMENT, " ").isFailure)
        assertTrue(GithubPullRequestReviewDraft.fromInput(GithubReviewEvent.REQUEST_CHANGES, " ").isFailure)
        assertTrue(
            GithubPullRequestReviewDraft.fromInput(
                GithubReviewEvent.APPROVE,
                "x".repeat(65_537)
            ).isFailure
        )
    }
}
