package takagi.ru.monica.github.feature.pullrequest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestDiffTest {
    @Test
    fun parsesSemanticLineKindsWithoutTreatingFileHeadersAsChanges() {
        val diff = parsePullRequestDiff(
            """
            --- a/app/Main.kt
            +++ b/app/Main.kt
            @@ -1,2 +1,2 @@
             context
            -old
            +new
            \ No newline at end of file
            """.trimIndent()
        )

        assertEquals(
            listOf(
                PullRequestDiffLineKind.METADATA,
                PullRequestDiffLineKind.METADATA,
                PullRequestDiffLineKind.HUNK,
                PullRequestDiffLineKind.CONTEXT,
                PullRequestDiffLineKind.DELETION,
                PullRequestDiffLineKind.ADDITION,
                PullRequestDiffLineKind.METADATA
            ),
            diff.lines.map(PullRequestDiffLine::kind)
        )
    }

    @Test
    fun capsRenderedLinesAndReportsTruncation() {
        val patch = (1..300).joinToString("\n") { "+line $it" }

        val diff = parsePullRequestDiff(patch, maxLines = 240)

        assertEquals(240, diff.lines.size)
        assertTrue(diff.isTruncated)
        assertEquals("+line 240", diff.lines.last().text)
    }
}
