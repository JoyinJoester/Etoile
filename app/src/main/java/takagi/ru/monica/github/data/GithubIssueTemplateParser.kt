package takagi.ru.monica.github.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import takagi.ru.monica.github.domain.GithubIssueContactLink
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueFormField
import takagi.ru.monica.github.domain.GithubIssueFormFieldType
import takagi.ru.monica.github.domain.GithubIssueFormOption
import takagi.ru.monica.github.domain.GithubIssueTemplate
import takagi.ru.monica.github.domain.GithubIssueTemplateCatalog
import java.io.StringReader

/** Reads scalar nodes directly: YAML 1.1 words such as Yes, No and On stay intact. */
internal object GithubIssueTemplateParser {
    const val MAX_TEMPLATE_BYTES = 262_144L

    fun parse(path: String, source: String, legacy: Boolean = false): GithubIssueTemplate {
        require(source.length <= MAX_TEMPLATE_BYTES)
        val text = source.removePrefix("\uFEFF").replace("\r\n", "\n")
        val isForm = path.substringAfterLast('.').lowercase() in setOf("yml", "yaml")
        val header: Map<String, Node>
        val body: String
        if (isForm) {
            header = mapping(text)
            body = ""
        } else if (text.lineSequence().firstOrNull()?.trim() == "---") {
            val lines = text.lines()
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
                ?: error("Unclosed front matter")
            header = mapping(lines.subList(1, end).joinToString("\n"))
            body = lines.drop(end + 1).joinToString("\n").trim()
        } else {
            require(legacy) { "Missing template metadata" }
            header = emptyMap()
            body = text.trim()
        }
        // These features cannot be reproduced by a Create Issue REST request.
        require(header["projects"].strings().isEmpty() && header["type"].string().isBlank())
        val fields = if (isForm) header["body"].sequence().mapIndexed(::parseField) else emptyList()
        require(!isForm || fields.any { it.type != GithubIssueFormFieldType.MARKDOWN })
        require(fields.size <= 80 && fields.map { it.id }.distinct().size == fields.size)
        val template = GithubIssueTemplate(
            id = path,
            name = header["name"].string().ifBlank { if (legacy) path.substringAfterLast('/') else error("Missing name") },
            description = header[if (isForm) "description" else "about"].string(),
            title = header["title"].string(),
            body = body,
            labels = header["labels"].strings(),
            assignees = header["assignees"].strings(),
            fields = fields,
            isForm = isForm
        )
        require(template.title.length <= GithubIssueDraft.MAX_TITLE_LENGTH)
        require((if (isForm) template.renderBody(template.initialAnswers()) else body).length <= GithubIssueDraft.MAX_BODY_LENGTH)
        return template
    }

    fun config(source: String): GithubIssueTemplateCatalog {
        val header = mapping(source)
        val contacts = header["contact_links"].sequence().map { node ->
            val link = node.mapping()
            val url = link["url"].string().toHttpUrlOrNull() ?: error("Invalid contact URL")
            require(url.username.isEmpty() && url.password.isEmpty())
            GithubIssueContactLink(
                name = link["name"].string().also { require(it.isNotBlank()) },
                url = url.toString(),
                about = link["about"].string()
            )
        }
        return GithubIssueTemplateCatalog(
            blankIssuesEnabled = header["blank_issues_enabled"].boolean(default = true),
            contactLinks = contacts
        )
    }

    private fun parseField(index: Int, node: Node): GithubIssueFormField {
        val field = node.mapping()
        val type = when (field["type"].string()) {
            "markdown" -> GithubIssueFormFieldType.MARKDOWN
            "input" -> GithubIssueFormFieldType.INPUT
            "textarea" -> GithubIssueFormFieldType.TEXTAREA
            "dropdown" -> GithubIssueFormFieldType.DROPDOWN
            "checkboxes" -> GithubIssueFormFieldType.CHECKBOXES
            else -> error("Unsupported form field")
        }
        val attributes = field["attributes"]?.mapping() ?: error("Missing field attributes")
        val validations = field["validations"]?.mapping().orEmpty()
        require(validations.keys.all { it == "required" }) { "Unsupported validation" }
        val options = attributes["options"].sequence().map { option ->
            if (type == GithubIssueFormFieldType.CHECKBOXES) {
                val item = option.mapping()
                GithubIssueFormOption(item["label"].string(), item["required"].boolean())
            } else GithubIssueFormOption(option.string())
        }
        val required = validations["required"].boolean()
        val multiple = attributes["multiple"].boolean()
        val selections = attributes["default"]?.let { listOf(it.string().toInt()) }.orEmpty()
        if (type == GithubIssueFormFieldType.DROPDOWN || type == GithubIssueFormFieldType.CHECKBOXES) {
            require(options.isNotEmpty() && options.size <= 100 && options.all { it.label.isNotBlank() })
            require(selections.all { it in options.indices })
        }
        // Checkbox requirements belong to individual options in GitHub's schema.
        require(type != GithubIssueFormFieldType.CHECKBOXES || !required)
        val label = attributes["label"].string()
        require(type == GithubIssueFormFieldType.MARKDOWN || label.isNotBlank())
        val render = attributes["render"].string().takeIf { it.isNotBlank() }
        require(render == null || render.matches(Regex("[A-Za-z0-9_+.-]{1,50}")))
        return GithubIssueFormField(
            id = field["id"].string().ifBlank { "field_$index" },
            type = type,
            label = label,
            description = attributes["description"].string(),
            placeholder = attributes["placeholder"].string(),
            value = attributes["value"].string(),
            required = required,
            options = options,
            multiple = multiple,
            defaultSelections = selections,
            render = render
        )
    }

    private fun mapping(source: String): Map<String, Node> {
        require(source.length <= MAX_TEMPLATE_BYTES)
        val options = LoaderOptions().apply {
            codePointLimit = MAX_TEMPLATE_BYTES.toInt()
            nestingDepthLimit = 30
            maxAliasesForCollections = 0
            isAllowDuplicateKeys = false
        }
        // compose() never constructs arbitrary Java objects from repository YAML tags.
        return Yaml(SafeConstructor(options)).compose(StringReader(source))?.mapping().orEmpty()
    }

    private fun Node.mapping(): Map<String, Node> {
        require(this is MappingNode)
        val result = linkedMapOf<String, Node>()
        value.forEach { entry ->
            val key = entry.keyNode.string()
            require(!result.containsKey(key)) { "Duplicate YAML key" }
            result[key] = entry.valueNode
        }
        return result
    }

    private fun Node?.string(): String = when (this) {
        null -> ""
        is ScalarNode -> if (tag.value == "tag:yaml.org,2002:null") "" else value
        else -> error("Expected scalar")
    }

    private fun Node?.sequence(): List<Node> = when (this) {
        null -> emptyList()
        is SequenceNode -> value
        else -> error("Expected sequence")
    }

    private fun Node?.boolean(default: Boolean = false): Boolean =
        if (this == null) default else string().lowercase().toBooleanStrict()

    private fun Node?.strings(): List<String> = when (this) {
        null -> emptyList()
        is SequenceNode -> value.map { it.string() }
        else -> string().split(',')
    }.map(String::trim).filter(String::isNotEmpty).distinct()
}
