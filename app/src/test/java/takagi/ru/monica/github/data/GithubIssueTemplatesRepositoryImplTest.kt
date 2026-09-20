package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GithubIssueTemplatesRepositoryImplTest {
    private lateinit var server: MockWebServer
    private val responses = ConcurrentHashMap<String, Pair<Int, String>>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body) = responses[request.path] ?: (404 to "")
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun readsTemplatesAndConfigThroughAuthenticatedContentsApiInFilenameOrder() = runTest {
        directory("project", "feature.md", "config.yml", "bug.yml")
        file("project", "config.yml", "blank_issues_enabled: false")
        file("project", "feature.md", MARKDOWN)
        file("project", "bug.yml", FORM)
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertEquals(listOf("bug.yml", "feature.md"), catalog.templates.map { it.fileName })
        assertTrue(catalog.templates.all { it.isSupported })
        assertFalse(catalog.blankIssuesEnabled)
        assertEquals(4, server.requestCount)
        repeat(4) {
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
            assertTrue(request.path!!.startsWith("/repos/sample/project/contents/.github/ISSUE_TEMPLATE"))
            if (request.path!!.endsWith(".md") || request.path!!.endsWith(".yml")) {
                assertEquals("application/vnd.github.v3+json", request.getHeader("Accept"))
            }
        }
    }

    @Test
    fun inheritsOwnerTemplatesOnlyWhenRepositoryHasNoLocalTemplates() = runTest {
        directory(".github", "feature.md", "config.yml")
        file(".github", "feature.md", MARKDOWN)
        file(".github", "config.yml", "blank_issues_enabled: false")
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertEquals("Feature request", catalog.templates.single().name)
        assertFalse(catalog.blankIssuesEnabled)
    }

    @Test
    fun configYmlTakesPrecedenceAndConfigFilesNeverBecomeTemplates() = runTest {
        directory("project", "config.yaml", "config.yml", "feature.md")
        file("project", "config.yaml", "blank_issues_enabled: true")
        file("project", "config.yml", "blank_issues_enabled: false")
        file("project", "feature.md", MARKDOWN)
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertFalse(catalog.blankIssuesEnabled)
        assertEquals(listOf("feature.md"), catalog.templates.map { it.fileName })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun localConfigAloneOverridesTheOwnerDefaults() = runTest {
        directory("project", "config.yml")
        file("project", "config.yml", "blank_issues_enabled: false")
        directory(".github", "feature.md")
        file(".github", "feature.md", MARKDOWN)
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertTrue(catalog.templates.isEmpty())
        assertFalse(catalog.blankIssuesEnabled)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun legacyTemplateInDocsTakesPrecedenceOverInheritedTemplates() = runTest {
        contentsFile("project", "docs/ISSUE_TEMPLATE.md", "## Report\n\nSteps")
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertEquals("## Report\n\nSteps", catalog.templates.single().body)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun missingTemplatesStillAllowOrdinaryIssueCreation() = runTest {
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertTrue(catalog.blankIssuesEnabled)
        assertTrue(catalog.templates.isEmpty())
        assertEquals(8, server.requestCount)
    }

    @Test
    fun authenticationRateLimitAndServerErrorsAreNotTreatedAsMissingTemplates() = runTest {
        listOf(401, 403, 429, 500).forEach { status ->
            responses["/repos/sample/project/contents/$DIRECTORY"] = status to ""
            val before = server.requestCount
            val result = repository().templates("sample", "project")
            assertEquals(status, (result.exceptionOrNull() as GithubApiException).statusCode)
            assertEquals(before + 1, server.requestCount)
        }
    }

    @Test
    fun invalidOrUnreadableConfigCannotEnableBlankIssues() = runTest {
        directory("project", "config.yml", "feature.md")
        file("project", "feature.md", MARKDOWN)
        file("project", "config.yml", "blank_issues_enabled: invalid")
        assertTrue(repository().templates("sample", "project").isFailure)
        responses["/repos/sample/project/contents/$DIRECTORY/config.yml"] = 500 to ""
        assertTrue(repository().templates("sample", "project").isFailure)
    }

    @Test
    fun unsupportedOrUnreadableTemplatesRemainVisibleAsWebChoices() = runTest {
        directory("project", "feature.md", "future.yml", "missing.md")
        file("project", "feature.md", MARKDOWN)
        file("project", "future.yml", FORM.replace("type: textarea", "type: upload"))
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertEquals(3, catalog.templates.size)
        assertTrue(catalog.templates.first().isSupported)
        assertTrue(catalog.templates.drop(1).none { it.isSupported })
    }

    @Test
    fun oversizedTemplatesAreNotDownloaded() = runTest {
        responses["/repos/sample/project/contents/$DIRECTORY"] = 200 to """
            [{"name":"huge.yml","path":"$DIRECTORY/huge.yml","sha":"a","type":"file","size":999999}]
        """.trimIndent()
        val catalog = repository().templates("sample", "project").getOrThrow()
        assertFalse(catalog.templates.single().isSupported)
        assertEquals(1, server.requestCount)
    }

    private fun directory(repository: String, vararg files: String) {
        responses["/repos/sample/$repository/contents/$DIRECTORY"] = 200 to files.joinToString(",", "[", "]") {
            """{"name":"$it","path":"$DIRECTORY/$it","sha":"a","type":"file","download_url":"https://untrusted.invalid/$it"}"""
        }
    }

    private fun file(repository: String, name: String, body: String) {
        contentsFile(repository, "$DIRECTORY/$name", body)
    }

    private fun contentsFile(repository: String, contentsPath: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        responses["/repos/sample/$repository/contents/$contentsPath"] = 200 to
            """{"path":"$contentsPath","sha":"a","size":${bytes.size},"encoding":"base64","content":"$encoded"}"""
    }

    private fun repository() = GithubIssueTemplatesRepositoryImpl(GithubRepositoryContentsRepositoryImpl(
        requests = GithubAuthenticatedRequests(object : GithubTokenStore {
            override fun read() = "test_token_12345678901234567890"
            override fun write(token: String) = Unit
            override fun clear() = Unit
        }), client = OkHttpClient(), baseUrl = server.url("/").toString()
    ))

    private companion object {
        const val DIRECTORY = ".github/ISSUE_TEMPLATE"
        val MARKDOWN = "---\nname: Feature request\nabout: Suggest an idea\ntitle: '[Feature] '\n---\n## Idea"
        val FORM = "name: Bug\ndescription: Report a bug\nbody:\n  - type: textarea\n    id: steps\n    attributes:\n      label: Steps"
    }
}
