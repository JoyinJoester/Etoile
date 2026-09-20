package takagi.ru.monica.github.feature.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowDispatchInputsTest {
    @Test
    fun acceptsBlankFormAndBlankLines() {
        assertEquals(emptyMap<String, String>(), parseWorkflowDispatchInputs("\n  \n").values)
        assertNull(parseWorkflowDispatchInputs("").invalidLine)
        assertEquals(mapOf("target" to "staging"), parseWorkflowDispatchInputs("\n target = staging\n").values)
    }

    @Test
    fun preservesEqualsInValueAndAllowsEmptyValue() {
        val parsed = parseWorkflowDispatchInputs("url=https://example.com/?a=b\noptional=")
        assertNull(parsed.invalidLine)
        assertEquals(mapOf("url" to "https://example.com/?a=b", "optional" to ""), parsed.values)
    }

    @Test
    fun rejectsMalformedLineWithoutSubmittingPartialValues() {
        val parsed = parseWorkflowDispatchInputs("target=production\n\ninvalid")
        assertEquals(3, parsed.invalidLine)
        assertTrue(parsed.values.isEmpty())
        assertEquals(1, parseWorkflowDispatchInputs(" =value").invalidLine)
    }

    @Test
    fun rejectsDuplicateKeysRatherThanOverwriting() {
        val parsed = parseWorkflowDispatchInputs("target=staging\n target =production")
        assertEquals(2, parsed.invalidLine)
        assertTrue(parsed.values.isEmpty())
    }
}
