package takagi.ru.monica.github.feature.actions

internal data class WorkflowDispatchInputs(
    val values: Map<String, String>,
    val invalidLine: Int? = null
)

/** Validate every nonblank line; never silently omit or overwrite an input. */
internal fun parseWorkflowDispatchInputs(text: String): WorkflowDispatchInputs {
    val values = linkedMapOf<String, String>()
    text.lineSequence().forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val separator = line.indexOf('=')
        val key = if (separator >= 0) line.substring(0, separator).trim() else ""
        if (separator < 0 || key.isBlank() || values.containsKey(key)) {
            return WorkflowDispatchInputs(emptyMap(), invalidLine = index + 1)
        }
        values[key] = line.substring(separator + 1).trim()
    }
    return WorkflowDispatchInputs(values)
}
