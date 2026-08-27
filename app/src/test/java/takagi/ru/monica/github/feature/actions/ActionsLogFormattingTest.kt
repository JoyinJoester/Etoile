package takagi.ru.monica.github.feature.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionsLogFormattingTest {
    @Test
    fun removesAnsiSequencesAndUnsafeControlCharactersWhileNormalizingLines() {
        val raw = "\u001B[32mPASS\u001B[0m\r\nline\u0000 two\rthree\tvalue"

        assertEquals(
            "PASS\nline two\nthree\tvalue",
            formatGithubActionsLog(raw)
        )
    }
}
