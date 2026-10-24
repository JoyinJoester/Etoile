package takagi.ru.monica.github.feature.pullrequest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestDiffTest {
    @Test
    fun tracksBothSidesAcrossHunksAndIgnoresMetadata() {
        val diff = parsePullRequestDiff("@@ -10,2 +20,3 @@\n context\n-old\n+new\n++extra\n\\ No newline at end of file\n@@ -30 +40 @@\n-before\n+after")
        assertEquals(listOf(null, 10, 11, null, null, null, null, 30, null), diff.lines.map { it.oldLine })
        assertEquals(listOf(null, 20, null, 21, 22, null, null, null, 40), diff.lines.map { it.newLine })
        assertEquals(PullRequestDiffLineKind.ADDITION, diff.lines[4].kind)
    }

    @Test
    fun missingMalformedAndExhaustedHunksNeverProduceClickableLineNumbers() {
        for (patch in listOf("+unlocated\n-old", "@@ broken @@\n+new", "@@ -1 +1 @@\ninvalid prefix\n context", "@@ -0,0 +0,0 @@\n+extra", "@@ -999999999999999999999,1 +1,0 @@\n-old")) {
            assertTrue(parsePullRequestDiff(patch).lines.all { it.oldLine == null && it.newLine == null })
        }
        val diff = parsePullRequestDiff("@@ -0,0 +1,1 @@\n+++code\n+outside")
        assertEquals(1, diff.lines[1].newLine)
        assertEquals(PullRequestDiffLineKind.ADDITION, diff.lines[1].kind)
        assertEquals(null, diff.lines.last().newLine)
    }
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
