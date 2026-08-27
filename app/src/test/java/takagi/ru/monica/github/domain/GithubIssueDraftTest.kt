package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubIssueDraftTest {
    @Test
    fun issueDraftTrimsTitleAndRejectsBlankOrOversizedInput() {
        val valid = GithubIssueDraft.fromInput("  Crash on launch  ", " Details ").getOrThrow()

        assertEquals("Crash on launch", valid.title)
        assertEquals("Details", valid.body)
        assertTrue(GithubIssueDraft.fromInput("   ", null).isFailure)
        assertTrue(GithubIssueDraft.fromInput("x".repeat(257), null).isFailure)
        assertTrue(GithubIssueDraft.fromInput("Title", "x".repeat(65_537)).isFailure)
    }

    @Test
    fun commentDraftRejectsBlankAndOversizedBodies() {
        assertEquals("Looks good", GithubIssueCommentDraft.fromInput("  Looks good  ").getOrThrow().body)
        assertTrue(GithubIssueCommentDraft.fromInput("  ").isFailure)
        assertTrue(GithubIssueCommentDraft.fromInput("x".repeat(65_537)).isFailure)
    }
}
