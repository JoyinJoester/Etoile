package takagi.ru.monica.gitlab.navigation

import org.junit.Assert.*
import org.junit.Test

class GitLabWebUrlsTest {
    @Test fun canonicalConversationMustStayInsideItsInstanceIncludingSelfHostedSubpath() {
        val instance = "https://gitlab.example.com/team/gitlab"
        val document = "$instance/group/repo/-/issues/7"
        assertEquals(document, gitLabDocumentUrl(instance, document))
        listOf("https://other.example.com/team/gitlab/group/repo/-/issues/7",
            "http://gitlab.example.com/team/gitlab/group/repo/-/issues/7",
            "https://gitlab.example.com/team/gitlab-other/7", "https://user:pass@gitlab.example.com/team/gitlab/7",
            "javascript:alert(1)").forEach { assertNull(it, gitLabDocumentUrl(instance, it)) }
    }

    @Test fun fileContextKeepsInstanceSubpathRevisionAndEncodedFilename() {
        val result = gitLabFileUrls("https://gitlab.example.com/team/gitlab", "group/repo", "feature/docs", "docs/Hello world.md")!!
        assertEquals("https://gitlab.example.com/team/gitlab/group/repo/-/blob/feature%2Fdocs/docs/Hello%20world.md", result.document)
        assertEquals("https://gitlab.example.com/team/gitlab/group/repo/-/raw/feature%2Fdocs/docs/Hello%20world.md", result.image)
    }
}
