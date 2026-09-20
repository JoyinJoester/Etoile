package takagi.ru.monica.github.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueFormAnswer
import takagi.ru.monica.github.domain.GithubIssueFormFieldType

class GithubIssueTemplateParserTest {
    @Test
    fun markdownFrontMatterPrefillsMetadataWithoutLeakingIntoTheBody() {
        val template = GithubIssueTemplateParser.parse(".github/ISSUE_TEMPLATE/bug.md", "\uFEFF" + """
            ---
            name: Bug report
            about: |
              Explain the problem.
              Include reproduction steps.
            title: '[Bug] '
            labels: [bug, 'needs, review']
            assignees: 'alice, bob'
            ---
            ## Steps

            ---

            ## Expected behavior
        """.trimIndent().replace("\n", "\r\n"))
        assertEquals("Bug report", template.name)
        assertEquals("[Bug] ", template.title)
        assertEquals(listOf("bug", "needs, review"), template.labels)
        assertEquals(listOf("alice", "bob"), template.assignees)
        assertEquals("## Steps\n\n---\n\n## Expected behavior", template.body)
        assertTrue(template.description.contains("\nInclude reproduction steps."))
        assertFalse(template.isForm)
    }

    @Test
    fun legacyTemplateWithoutMetadataKeepsTheWholeBody() {
        val template = GithubIssueTemplateParser.parse("ISSUE_TEMPLATE.md", "## Report\n\nDetails", legacy = true)
        assertEquals("## Report\n\nDetails", template.body)
        assertTrue(runCatching { GithubIssueTemplateParser.parse("broken.md", "## Report") }.isFailure)
    }

    @Test
    fun yamlFormPreservesScalarSpellingsAndInitialDropdownSelection() {
        val template = GithubIssueTemplateParser.parse("bug.yaml", FORM)
        assertTrue(template.isForm)
        assertEquals("[Bug] ", template.title)
        assertEquals(listOf("Yes", "No", "On", "1.20"), template.fields[2].options.map { it.label })
        assertEquals("0.1.0", template.initialAnswers()["version"]?.text)
        assertEquals(listOf(1), template.initialAnswers()["choice"]?.selections)
        assertEquals(GithubIssueFormFieldType.MARKDOWN, template.fields.first().type)
        assertEquals(setOf("steps", "checks"), template.invalidFields(template.initialAnswers()))
    }

    @Test
    fun formValidationRequiresEveryRequiredCheckboxAndNonblankText() {
        val template = GithubIssueTemplateParser.parse("bug.yml", FORM)
        val answers = template.initialAnswers() + mapOf(
            "steps" to GithubIssueFormAnswer("  "),
            "checks" to GithubIssueFormAnswer(selections = listOf(1))
        )
        assertEquals(setOf("steps", "checks"), template.invalidFields(answers))
        assertTrue(template.invalidFields(answers + mapOf(
            "steps" to GithubIssueFormAnswer("Open the app"),
            "checks" to GithubIssueFormAnswer(selections = listOf(0))
        )).isEmpty())
        assertTrue("choice" in template.invalidFields(answers + ("choice" to GithubIssueFormAnswer(selections = listOf(99)))))
    }

    @Test
    fun formBodyContainsAnswersAndChecklistButExcludesIntroAndYaml() {
        val template = GithubIssueTemplateParser.parse("bug.yml", FORM)
        val body = template.renderBody(template.initialAnswers() + mapOf(
            "steps" to GithubIssueFormAnswer("Open the app\nIt crashes"),
            "checks" to GithubIssueFormAnswer(selections = listOf(0))
        ))
        assertEquals("""
            ### Version

            0.1.0

            ### Reproducible

            No

            ### Steps

            Open the app
            It crashes

            ### Before submitting

            - [x] I searched existing issues.
            - [ ] I can test a fix.
        """.trimIndent(), body)
        assertFalse(body.contains("Thanks for reporting"))
    }

    @Test
    fun renderedLogsCannotCloseTheirCodeFence() {
        val template = GithubIssueTemplateParser.parse("logs.yml", """
            name: Logs
            description: Diagnostic output
            body:
              - type: textarea
                id: logs
                attributes:
                  label: Logs
                  render: shell
        """.trimIndent())
        assertEquals("### Logs\n\n````shell\n```\nlog\n```\n````", template.renderBody(mapOf("logs" to GithubIssueFormAnswer("```\nlog\n```"))))
    }

    @Test
    fun unsupportedFieldsAndMetadataDoNotSilentlyBecomeIncompleteForms() {
        listOf(
            FORM.replace("type: input", "type: upload"),
            FORM.replace("id: steps", "id: version"),
            "projects: [sample/1]\n$FORM",
            FORM.replace("required: true", "required: true\n      pattern: '[a-z]+'")
        ).forEach { assertTrue(runCatching { GithubIssueTemplateParser.parse("bug.yml", it) }.isFailure) }
    }

    @Test
    fun malformedDuplicateAndAliasedYamlIsRejected() {
        listOf(
            "name: One\nname: Two\nbody: []",
            "!!java.net.URL [https://example.com]",
            "name: Form\nbody: &loop [*loop]",
            "name: [unterminated",
            "name: One\n---\nname: Two"
        ).forEach { assertTrue(runCatching { GithubIssueTemplateParser.parse("bug.yml", it) }.isFailure) }
    }

    @Test
    fun templatesCannotStartBeyondTheIssueCharacterLimits() {
        assertTrue(runCatching {
            GithubIssueTemplateParser.parse("ISSUE_TEMPLATE.md", "x".repeat(GithubIssueDraft.MAX_BODY_LENGTH + 1), legacy = true)
        }.isFailure)
        assertTrue(runCatching {
            GithubIssueTemplateParser.parse("bug.yml", FORM.replace("'[Bug] '", "'${"x".repeat(257)}'"))
        }.isFailure)
    }

    @Test
    fun configHandlesBlankPolicyAndContactLinks() {
        val config = GithubIssueTemplateParser.config("""
            blank_issues_enabled: false
            contact_links:
              - name: Community support
                url: https://github.com/sample/project/discussions
                about: Ask for help here.
        """.trimIndent())
        assertFalse(config.blankIssuesEnabled)
        assertEquals("Community support", config.contactLinks.single().name)
        assertTrue(GithubIssueTemplateParser.config("").blankIssuesEnabled)
        assertTrue(runCatching { GithubIssueTemplateParser.config("blank_issues_enabled: perhaps") }.isFailure)
        assertTrue(runCatching { GithubIssueTemplateParser.config("contact_links: [{name: Link, url: 'javascript:alert(1)'}]") }.isFailure)
    }

    private companion object {
        val FORM = """
            name: Bug report
            description: Help us reproduce the bug.
            title: '[Bug] '
            labels: [bug, triage]
            body:
              - type: markdown
                attributes:
                  value: Thanks for reporting!
              - type: input
                id: version
                attributes:
                  label: Version
                  value: 0.1.0
                validations:
                  required: true
              - type: dropdown
                id: choice
                attributes:
                  label: Reproducible
                  options: [Yes, No, On, 1.20]
                  default: 1
                validations:
                  required: true
              - type: textarea
                id: steps
                attributes:
                  label: Steps
                validations:
                  required: true
              - type: checkboxes
                id: checks
                attributes:
                  label: Before submitting
                  options:
                    - label: I searched existing issues.
                      required: true
                    - label: I can test a fix.
        """.trimIndent()
    }
}
