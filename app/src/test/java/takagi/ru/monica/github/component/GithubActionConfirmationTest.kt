package takagi.ru.monica.github.component

import org.junit.Assert.*
import org.junit.Test

class GithubActionConfirmationTest {
    @Test fun destructiveActionRequiresAnExplicitConfirmationAndRunsOnce() {
        val gate = GithubActionConfirmation()
        var writes = 0
        gate.confirm { writes++ }
        assertEquals(0, writes)
        gate.request()
        assertEquals(0, writes)
        assertTrue(gate.requested)
        gate.confirm {
            writes++
            // A double tap or reentrant callback cannot dispatch the same action again.
            gate.confirm { writes++ }
        }
        gate.confirm { writes++ }
        assertEquals(1, writes)
        assertFalse(gate.requested)
    }

    @Test fun dismissingAnActionPreventsItsExecution() {
        val gate = GithubActionConfirmation()
        gate.request()
        gate.cancel()
        gate.confirm { fail("Cancelled operation was dispatched") }
        assertFalse(gate.requested)
    }
}
