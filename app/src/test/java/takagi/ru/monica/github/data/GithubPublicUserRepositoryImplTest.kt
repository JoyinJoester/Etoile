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
import takagi.ru.monica.github.domain.GithubUserConnectionKind

class GithubPublicUserRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun userMapsProfileMetadata() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(USER_JSON))
        val repository = repository(token = null)

        val user = repository.user("joyins").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/users/joyins", request.path)
        assertEquals("joyins", user.login)
        assertEquals("Build things", user.bio)
        assertEquals(12, user.publicRepositories)
        assertTrue(user.isHireable == true)
    }

    @Test
    fun repositoriesUsePublicUserEndpointAndLinkPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/users/joyins/repos?page=2&per_page=30")}>; rel=\"next\"")
                .setBody(REPOSITORIES_JSON)
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.repositories("joyins").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/users/joyins/repos?per_page=30&page=1&sort=updated&direction=desc", request.path)
        assertEquals("joyins/etoile", page.items.single().fullName)
        assertEquals(2, page.nextPage)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    @Test
    fun followersUsePublicRelationshipEndpointAndLinkPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/users/joyins/followers?page=2")}>; rel=\"next\"")
                .setBody(USERS_JSON)
        )

        val page = repository(token = null).connections(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWERS
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/users/joyins/followers?per_page=50&page=1", request.path)
        assertEquals(listOf("alice", "bob"), page.items.map { it.login })
        assertEquals(2, page.nextPage)
    }

    @Test
    fun followingUsesFollowingEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(USERS_JSON))

        repository(token = null).connections(
            login = "joyins",
            kind = GithubUserConnectionKind.FOLLOWING
        ).getOrThrow()

        assertEquals("/users/joyins/following?per_page=50&page=1", server.takeRequest().path)
    }

    @Test
    fun connectionsReuseCachedBodyWhenServerReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"followers-v1\"")
                .setBody(USERS_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val repository = repository(token = null, cacheStore = TestGithubCacheStore())

        val first = repository.connections("joyins", GithubUserConnectionKind.FOLLOWERS).getOrThrow()
        val second = repository.connections("joyins", GithubUserConnectionKind.FOLLOWERS).getOrThrow()
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()

        assertEquals(first, second)
        assertEquals(null, firstRequest.getHeader("If-None-Match"))
        assertEquals("\"followers-v1\"", secondRequest.getHeader("If-None-Match"))
    }

    @Test
    fun viewerFollowingUses204And404AsBooleanStates() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(404))
        val repository = repository(token = "test_token_12345678901234567890")

        assertTrue(repository.viewerFollows("alice").getOrThrow())
        assertEquals(false, repository.viewerFollows("bob").getOrThrow())
        assertEquals("/user/following/alice", server.takeRequest().path)
        assertEquals("/user/following/bob", server.takeRequest().path)
    }

    @Test
    fun setFollowingUsesPutAndDeleteAndInvalidatesCache() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val cacheStore = TestGithubCacheStore()
        cacheStore.write(
            "stale",
            GithubCachedResponse("{}", null, null, 1L)
        )
        val repository = repository(token = "test_token_12345678901234567890", cacheStore = cacheStore)

        assertTrue(repository.setFollowing("alice", true).getOrThrow())
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(false, repository.setFollowing("alice", false).getOrThrow())
        assertEquals("DELETE", server.takeRequest().method)
        assertTrue(cacheStore.isEmpty())
    }

    private fun repository(
        token: String?,
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubPublicUserRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore(token)),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private class FakeTokenStore(private val token: String?) : GithubTokenStore {
        override fun read() = token
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val USER_JSON = """
            {
              "id": 7, "login": "joyins", "name": "Joyin", "bio": "Build things",
              "avatar_url": "https://avatars.example/joyins", "html_url": "https://github.com/joyins",
              "company": "Etoile", "location": "Earth", "blog": "https://joyins.dev",
              "public_repos": 12, "followers": 4, "following": 8, "hireable": true
            }
        """.trimIndent()

        val REPOSITORIES_JSON = """
            [
              {
                "id": 11, "name": "etoile", "full_name": "joyins/etoile",
                "description": "GitHub client", "language": "Kotlin", "stargazers_count": 3,
                "updated_at": "2026-08-16T00:00:00Z", "private": false,
                "html_url": "https://github.com/joyins/etoile"
              }
            ]
        """.trimIndent()

        val USERS_JSON = """
            [
              {
                "login": "alice",
                "avatar_url": "https://avatars.example/alice",
                "html_url": "https://github.com/alice"
              },
              {
                "login": "bob",
                "avatar_url": "https://avatars.example/bob",
                "html_url": "https://github.com/bob"
              }
            ]
        """.trimIndent()
    }
}
