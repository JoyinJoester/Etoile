package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubBuiltInIssueTemplatesTest {
    @Test
    fun eachLanguageHasMatchingBugFeatureAndQuestionForms() {
        val chinese = GithubBuiltInIssueTemplates.forLanguage(GithubIssueTemplateLanguage.CHINESE)
        val english = GithubBuiltInIssueTemplates.forLanguage(GithubIssueTemplateLanguage.ENGLISH)
        assertEquals(3, chinese.size)
        assertEquals(3, english.size)
        chinese.zip(english).forEach { (zh, en) ->
            assertEquals(zh.fields.map { it.id }, en.fields.map { it.id })
            assertEquals(zh.fields.map { it.required }, en.fields.map { it.required })
            assertTrue(zh.id.endsWith(":zh"))
            assertTrue(en.id.endsWith(":en"))
            assertFalse(zh.name == en.name)
            assertEquals(zh, GithubBuiltInIssueTemplates.find(zh.id))
        }
    }

    @Test
    fun builtInFormsProducePublishableMarkdownWithoutAssumingRepositoryMetadata() {
        GithubIssueTemplateLanguage.entries.flatMap(GithubBuiltInIssueTemplates::forLanguage).forEach { template ->
            val answers = template.fields.associate { it.id to GithubIssueFormAnswer("Example answer") }
            assertTrue(template.invalidFields(answers).isEmpty())
            assertTrue(GithubIssueDraft.fromInput(template.title, template.renderBody(answers)).isSuccess)
            assertTrue(template.labels.isEmpty())
            assertTrue(template.assignees.isEmpty())
            assertTrue(template.isBuiltIn && template.isForm && template.isSupported)
        }
    }

    @Test
    fun emptyRequiredAnswersAreRejectedInBothLanguages() {
        GithubIssueTemplateLanguage.entries.flatMap(GithubBuiltInIssueTemplates::forLanguage).forEach { template ->
            assertTrue(template.invalidFields(template.initialAnswers()).isNotEmpty())
        }
    }
}
