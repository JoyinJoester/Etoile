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

class GithubApiRepositorySearchRepositoryTest {
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
    fun searchUsesSharedHeadersClampsLimitAndMapsRepositories() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/search/repositories?page=2")}>; rel=\"next\"")
                .setBody(SEARCH_JSON)
        )
        val repository = GithubApiRepositorySearchRepository(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val result = repository.search("kotlin compose", page = 1, perPage = 100).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/search/repositories", request.requestUrl?.encodedPath)
        assertEquals("kotlin compose", request.requestUrl?.queryParameter("q"))
        assertEquals("50", request.requestUrl?.queryParameter("per_page"))
        assertEquals("1", request.requestUrl?.queryParameter("page"))
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertEquals("Etoile-GitHub-Client", request.getHeader("User-Agent"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("JetBrains/compose-multiplatform", result.items.single().fullName)
        assertEquals(123L, result.items.single().id)
        assertEquals(2, result.nextPage)
    }

    @Test
    fun searchExposesTypedApiFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val repository = GithubApiRepositorySearchRepository(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val error = repository.search("compose").exceptionOrNull()

        assertTrue(error is GithubApiException)
        assertEquals(403, (error as GithubApiException).statusCode)
    }

    @Test
    fun searchUsesCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"search-v1\"")
                .setBody(SEARCH_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = GithubApiRepositorySearchRepository(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )

        val first = repository.search("compose").getOrThrow()
        val second = repository.search("compose").getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"search-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val SEARCH_JSON = """
            {
              "items": [
                {
                  "id": 123,
                  "name": "compose-multiplatform",
                  "full_name": "JetBrains/compose-multiplatform",
                  "description": "Declarative UI framework for Kotlin",
                  "language": "Kotlin",
                  "stargazers_count": 18500,
                  "updated_at": "2026-08-16T00:00:00Z",
                  "private": false,
                  "html_url": "https://github.com/JetBrains/compose-multiplatform"
                }
              ]
            }
        """.trimIndent()
    }
}
