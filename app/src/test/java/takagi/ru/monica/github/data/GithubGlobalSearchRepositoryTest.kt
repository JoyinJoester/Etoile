package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubIssueSearchType

class GithubGlobalSearchRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() { server = MockWebServer().also { it.start() } }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun usersUseSearchEndpointAndMapIdentity() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(USERS_JSON))
        val repository = repository(token = null)

        val page = repository.users("joy", page = 2, perPage = 10).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/search/users?q=joy&per_page=10&page=2", request.path)
        assertEquals("joyins", page.items.single().login)
        assertEquals("User", page.items.single().accountType)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun codeMapsRepositoryAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/search/code?page=2&per_page=20&q=ktor")}>; rel=\"next\"")
                .setBody(CODE_JSON)
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.code("ktor", page = 1).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/search/code?q=ktor&per_page=20&page=1", request.path)
        assertEquals("ktor/ktor", page.items.single().repositoryFullName)
        assertEquals(2, page.nextPage)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    @Test
    fun issuesOverrideConflictingTypeQualifierAndMapNativeMetadata() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/search/issues?page=2")}>; rel=\"next\"")
                .setBody(ISSUES_JSON)
        )
        val repository = repository(token = null)

        val page = repository.issues("crash is:pr", page = 1, perPage = 20).getOrThrow()
        val request = server.takeRequest()
        val item = page.items.single()

        assertEquals("/search/issues", request.requestUrl?.encodedPath)
        assertEquals("crash is:issue", request.requestUrl?.queryParameter("q"))
        assertEquals("openai/codex", item.repositoryFullName)
        assertEquals("alice", item.author.login)
        assertEquals("bug", item.labels.single().name)
        assertEquals(2, page.nextPage)
        assertEquals(GithubIssueSearchType.ISSUE, item.type)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun pullRequestsOverrideTypeQualifierAndPreserveDraftState() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(PULL_REQUESTS_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.pullRequests("native type:issue", page = 3, perPage = 10).getOrThrow()
        val request = server.takeRequest()
        val item = page.items.single()

        assertEquals("native is:pr", request.requestUrl?.queryParameter("q"))
        assertEquals("3", request.requestUrl?.queryParameter("page"))
        assertEquals(GithubIssueSearchType.PULL_REQUEST, item.type)
        assertTrue(item.isDraft)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    private fun repository(token: String?) = GithubApiRepositorySearchRepository(
        requests = GithubAuthenticatedRequests(FakeTokenStore(token)),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString()
    )

    private class FakeTokenStore(private val token: String?) : GithubTokenStore {
        override fun read() = token
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val USERS_JSON = """
            { "total_count": 1, "items": [
              { "id": 7, "login": "joyins", "avatar_url": "https://avatars.example/joyins", "html_url": "https://github.com/joyins", "type": "User" }
            ] }
        """.trimIndent()

        val CODE_JSON = """
            { "total_count": 1, "items": [
              { "name": "Client.kt", "path": "src/Client.kt", "sha": "abc123", "html_url": "https://github.com/ktor/ktor/blob/main/src/Client.kt", "repository": { "full_name": "ktor/ktor" } }
            ] }
        """.trimIndent()

        val ISSUES_JSON = """
            { "total_count": 1, "items": [
              {
                "id": 42,
                "number": 17,
                "title": "Crash on launch",
                "state": "open",
                "draft": false,
                "user": { "login": "alice", "avatar_url": null, "html_url": "https://github.com/alice" },
                "labels": [{ "name": "bug", "color": "d73a4a", "description": "Problem" }],
                "comments": 4,
                "repository_url": "https://api.github.com/repos/openai/codex",
                "created_at": "2026-08-15T00:00:00Z",
                "updated_at": "2026-08-17T00:00:00Z",
                "html_url": "https://github.com/openai/codex/issues/17"
              }
            ] }
        """.trimIndent()

        val PULL_REQUESTS_JSON = """
            { "total_count": 1, "items": [
              {
                "id": 77,
                "number": 21,
                "title": "Native client",
                "state": "open",
                "draft": true,
                "user": { "login": "bob", "avatar_url": null, "html_url": "https://github.com/bob" },
                "labels": [],
                "comments": 2,
                "repository_url": "https://api.github.com/repos/openai/codex",
                "created_at": "2026-08-16T00:00:00Z",
                "updated_at": "2026-08-17T00:00:00Z",
                "html_url": "https://github.com/openai/codex/pull/21",
                "pull_request": { "url": "https://api.github.com/repos/openai/codex/pulls/21" }
              }
            ] }
        """.trimIndent()
    }
}
