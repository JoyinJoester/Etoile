package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GithubStarsRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun loadsAuthenticatedStarredRepositories() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/user/starred?page=2")}>; rel=\"next\"")
                .setBody(REPOSITORIES_JSON)
        )
        val repository = GithubStarsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val result = repository.starredRepositories(page = 1, perPage = 100).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/user/starred?sort=updated&direction=desc&per_page=100&page=1", request.path)
        assertEquals("Bearer token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("joyins/etoile", result.items.single().fullName)
        assertEquals(2, result.nextPage)
    }

    @Test
    fun starredRepositoriesUseCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"stars-v1\"")
                .setBody(REPOSITORIES_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = GithubStarsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )

        val first = repository.starredRepositories().getOrThrow()
        val second = repository.starredRepositories().getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"stars-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test fun pagesBeyondOneHundredKeepTheirOwnCacheAndPaginationAfterNotModified() = runTest {
        val repository = GithubStarsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(), baseUrl = server.url("/").toString(),
            cacheStore = TestGithubCacheStore()
        )
        fun pageJson(ids: IntRange) = ids.joinToString(prefix = "[", postfix = "]") { id ->
            REPOSITORIES_JSON.trim().removePrefix("[").removeSuffix("]")
                .replace("\"id\": 1,", "\"id\": $id,")
                .replace("joyins/etoile", "joyins/etoile-$id")
        }
        server.enqueue(MockResponse().setHeader("ETag", "\"page-one\"")
            .setHeader("Link", "<${server.url("/user/starred?page=2&per_page=100")}>; rel=\"next\", <${server.url("/user/starred?page=3&per_page=100")}>; rel=\"last\"")
            .setBody(pageJson(1..100)))
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setHeader("Link", "<${server.url("/user/starred?page=1&per_page=100")}>; rel=\"prev\", <${server.url("/user/starred?page=3&per_page=100")}>; rel=\"next\"")
            .setBody(pageJson(101..200)))
        server.enqueue(MockResponse().setBody(pageJson(201..205)))
        val first = repository.starredRepositories().getOrThrow()
        val restored = repository.starredRepositories().getOrThrow()
        assertEquals(first, restored)
        val second = repository.starredRepositories(restored.nextPage!!).getOrThrow()
        val third = repository.starredRepositories(second.nextPage!!).getOrThrow()
        assertEquals((1L..205L).toList(), (first.items + second.items + third.items).map { it.id })
        assertEquals(null, third.nextPage)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
        val cached = server.takeRequest()
        assertEquals("\"page-one\"", cached.getHeader("If-None-Match"))
        for (page in listOf(2, 3)) {
            val request = server.takeRequest()
            assertEquals("$page", request.requestUrl!!.queryParameter("page"))
            assertEquals("100", request.requestUrl!!.queryParameter("per_page"))
            assertEquals(null, request.getHeader("If-None-Match"))
        }
    }

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val REPOSITORIES_JSON = """
            [{
              "id": 1,
              "name": "etoile",
              "full_name": "joyins/etoile",
              "description": "GitHub client",
              "language": "Kotlin",
              "stargazers_count": 100,
              "updated_at": "2026-08-16T00:00:00Z",
              "private": false,
              "html_url": "https://github.com/joyins/etoile"
            }]
        """.trimIndent()
    }
}
