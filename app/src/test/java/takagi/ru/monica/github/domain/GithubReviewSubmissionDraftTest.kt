package takagi.ru.monica.github.domain

import org.junit.Assert.*
import org.junit.Test

class GithubReviewSubmissionDraftTest {
    private val line = GithubReviewLineComment("app/Main.kt", 12, GithubDiffSide.RIGHT, "  fixed  ")

    @Test fun batchAllowsCommentsWithoutSummaryAndNormalizesAllText() {
        val draft = GithubReviewSubmissionDraft.fromInput(" head ", GithubReviewEvent.COMMENT, "  ", listOf(line)).getOrThrow()
        assertEquals("head", draft.commitSha)
        assertNull(draft.body)
        assertEquals("fixed", draft.comments.single().body)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.APPROVE, "", emptyList()).isSuccess)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.COMMENT, "", emptyList()).isFailure)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.REQUEST_CHANGES, "", listOf(line)).isFailure)
    }

    @Test fun batchRejectsInvalidCoordinatesAndExcessiveDrafts() {
        for (comment in listOf(line.copy(line = 0), line.copy(path = ""), line.copy(path = "bad\npath"), line.copy(body = " "))) {
            assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.COMMENT, "", listOf(comment)).isFailure)
        }
        assertTrue(GithubReviewSubmissionDraft.fromInput("", GithubReviewEvent.COMMENT, "summary", emptyList()).isFailure)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.COMMENT, "", List(51) { line }).isFailure)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.COMMENT, "s".repeat(65_536), listOf(line)).isFailure)
        assertTrue(GithubReviewSubmissionDraft.fromInput("head", GithubReviewEvent.COMMENT, "s".repeat(65_536), emptyList()).isSuccess)
    }
}
