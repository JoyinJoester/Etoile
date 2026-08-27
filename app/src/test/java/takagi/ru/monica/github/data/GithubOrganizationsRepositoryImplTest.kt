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

class GithubOrganizationsRepositoryImplTest {
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
    fun myOrganizationsRequestsUserOrgsAndParsesPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/user/orgs?page=2")}>; rel=\"next\"")
                .setBody(ORGANIZATIONS_JSON)
        )
        val repository = GithubOrganizationsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val page = repository.myOrganizations(page = 1).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/user/orgs", request.requestUrl?.encodedPath)
        assertEquals("30", request.requestUrl?.queryParameter("per_page"))
        assertEquals("1", request.requestUrl?.queryParameter("page"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(2, page.nextPage)
        val organization = page.items.single()
        assertEquals(42, organization.id)
        assertEquals("etoile-devs", organization.login)
        assertEquals("https://avatars.githubusercontent.com/u/42?v=4", organization.avatarUrl)
        assertEquals("Etoile core team", organization.description)
    }

    @Test
    fun myOrganizationsUseCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"orgs-v1\"")
                .setBody(ORGANIZATIONS_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = GithubOrganizationsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )

        val first = repository.myOrganizations().getOrThrow()
        val second = repository.myOrganizations().getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"orgs-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun myOrganizationsReturnFailureWhenGithubErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val repository = GithubOrganizationsRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val result = repository.myOrganizations()
        assertTrue(result.isFailure)
    }

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val ORGANIZATIONS_JSON = """
            [
              {
                "id": 42,
                "login": "etoile-devs",
                "avatar_url": "https://avatars.githubusercontent.com/u/42?v=4",
                "description": "Etoile core team"
              }
            ]
        """.trimIndent()
    }
}
