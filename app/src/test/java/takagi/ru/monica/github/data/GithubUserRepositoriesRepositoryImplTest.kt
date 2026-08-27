package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GithubUserRepositoriesRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun repositoriesIncludePrivateAffiliationsAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/user/repos?page=2")}>; rel=\"next\"")
                .setBody(REPOSITORIES_JSON)
        )
        val repository = GithubUserRepositoriesRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val page = repository.repositories(page = 1, perPage = 30).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/user/repos", request.requestUrl?.encodedPath)
        assertEquals("owner,collaborator,organization_member", request.requestUrl?.queryParameter("affiliation"))
        assertEquals("all", request.requestUrl?.queryParameter("visibility"))
        assertEquals("updated", request.requestUrl?.queryParameter("sort"))
        assertEquals("desc", request.requestUrl?.queryParameter("direction"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(2, page.nextPage)
        assertTrue(page.items.single().isPrivate)
    }

    @Test
    fun userRepositoriesUseCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"repos-v1\"")
                .setBody(REPOSITORIES_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = GithubUserRepositoriesRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )

        val first = repository.repositories().getOrThrow()
        val second = repository.repositories().getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"repos-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val REPOSITORIES_JSON = """
            [
              {
                "id": 20,
                "name": "private-app",
                "full_name": "joyins/private-app",
                "description": "Private project",
                "language": "Kotlin",
                "stargazers_count": 3,
                "updated_at": "2026-08-16T00:00:00Z",
                "private": true,
                "html_url": "https://github.com/joyins/private-app"
              }
            ]
        """.trimIndent()
    }
}
