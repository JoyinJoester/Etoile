package takagi.ru.monica.github.domain

import kotlinx.serialization.Serializable

@Serializable
data class GithubIssueTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val title: String = "",
    val body: String = "",
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    val fields: List<GithubIssueFormField> = emptyList(),
    val isForm: Boolean = false,
    val isSupported: Boolean = true,
    val isBuiltIn: Boolean = false
) {
    val fileName: String get() = id.substringAfterLast('/')

    fun initialAnswers(): Map<String, GithubIssueFormAnswer> = fields
        .filter { it.type != GithubIssueFormFieldType.MARKDOWN }
        .associate { it.id to GithubIssueFormAnswer(it.value, it.defaultSelections) }

    fun invalidFields(answers: Map<String, GithubIssueFormAnswer>): Set<String> = fields
        .filter { field ->
            val answer = answers[field.id] ?: GithubIssueFormAnswer()
            when (field.type) {
                GithubIssueFormFieldType.MARKDOWN -> false
                GithubIssueFormFieldType.INPUT, GithubIssueFormFieldType.TEXTAREA ->
                    field.required && answer.text.isBlank()
                GithubIssueFormFieldType.DROPDOWN ->
                    (field.required && answer.selections.isEmpty()) ||
                        (!field.multiple && answer.selections.size > 1) ||
                        answer.selections.any { it !in field.options.indices }
                GithubIssueFormFieldType.CHECKBOXES ->
                    field.options.indices.any { field.options[it].required && it !in answer.selections }
            }
        }.mapTo(linkedSetOf()) { it.id }

    /** GitHub's REST endpoint accepts a Markdown body, not the Issue Forms YAML schema. */
    fun renderBody(answers: Map<String, GithubIssueFormAnswer>): String = fields
        .filter { it.type != GithubIssueFormFieldType.MARKDOWN }
        .joinToString("\n\n") { field ->
            val answer = answers[field.id] ?: GithubIssueFormAnswer()
            val value = when (field.type) {
                GithubIssueFormFieldType.INPUT, GithubIssueFormFieldType.TEXTAREA -> {
                    val text = answer.text.trim()
                    if (text.isEmpty()) "_No response_"
                    else if (field.render != null) {
                        // A log containing ``` must not close its own code block.
                        val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
                        val fence = "`".repeat(maxOf(3, longestRun + 1))
                        "$fence${field.render}\n$text\n$fence"
                    } else text
                }
                GithubIssueFormFieldType.DROPDOWN -> answer.selections.sorted()
                    .mapNotNull { field.options.getOrNull(it)?.label }
                    .joinToString(", ").ifBlank { "_No response_" }
                GithubIssueFormFieldType.CHECKBOXES -> field.options.mapIndexed { index, option ->
                    "- [${if (index in answer.selections) "x" else " "}] ${option.label}"
                }.joinToString("\n")
                GithubIssueFormFieldType.MARKDOWN -> ""
            }
            "### ${field.label}\n\n$value"
        }
}

@Serializable
enum class GithubIssueFormFieldType { MARKDOWN, INPUT, TEXTAREA, DROPDOWN, CHECKBOXES }

@Serializable
data class GithubIssueFormField(
    val id: String,
    val type: GithubIssueFormFieldType,
    val label: String = "",
    val description: String = "",
    val placeholder: String = "",
    val value: String = "",
    val required: Boolean = false,
    val options: List<GithubIssueFormOption> = emptyList(),
    val multiple: Boolean = false,
    val defaultSelections: List<Int> = emptyList(),
    val render: String? = null
)

@Serializable
data class GithubIssueFormOption(val label: String, val required: Boolean = false)

@Serializable
data class GithubIssueFormAnswer(val text: String = "", val selections: List<Int> = emptyList())

data class GithubIssueTemplateCatalog(
    val templates: List<GithubIssueTemplate> = emptyList(),
    val blankIssuesEnabled: Boolean = true,
    val contactLinks: List<GithubIssueContactLink> = emptyList()
)

data class GithubIssueContactLink(val name: String, val url: String, val about: String)

fun interface GithubIssueTemplatesRepository {
    suspend fun templates(owner: String, name: String): Result<GithubIssueTemplateCatalog>
}
