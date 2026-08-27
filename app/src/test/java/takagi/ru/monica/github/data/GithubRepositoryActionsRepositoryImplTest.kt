package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GithubRepositoryActionsRepositoryImplTest {
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
    fun viewerStateUsesAuthenticatedStarAndSubscriptionChecks() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"subscribed":true,"ignored":false}"""
            )
        )
        val repository = repository()

        val state = repository.viewerState("openai", "codex").getOrThrow()
        val starRequest = server.takeRequest()
        val watchRequest = server.takeRequest()

        assertEquals("/user/starred/openai/codex", starRequest.path)
        assertEquals("/repos/openai/codex/subscription", watchRequest.path)
        assertEquals("Bearer test_token_12345678901234567890", starRequest.getHeader("Authorization"))
        assertTrue(state.isStarred)
        assertTrue(state.isWatching)
    }

    @Test
    fun starAndWatchWritesUseExpectedMethodsAndSerializedBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"subscribed":true,"ignored":false}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository()

        assertTrue(repository.setStarred("openai", "codex", true).getOrThrow())
        val starRequest = server.takeRequest()
        assertFalse(repository.setStarred("openai", "codex", false).getOrThrow())
        val unstarRequest = server.takeRequest()
        assertTrue(repository.setWatching("openai", "codex", true).getOrThrow())
        val watchRequest = server.takeRequest()
        assertFalse(repository.setWatching("openai", "codex", false).getOrThrow())
        val unwatchRequest = server.takeRequest()

        assertEquals("PUT", starRequest.method)
        assertEquals(0L, starRequest.bodySize)
        assertEquals("DELETE", unstarRequest.method)
        assertEquals("PUT", watchRequest.method)
        assertEquals("{\"subscribed\":true,\"ignored\":false}", watchRequest.body.readUtf8())
        assertEquals("DELETE", unwatchRequest.method)
    }

    @Test
    fun forkReturnsMappedRepositoryAndRequiresAuthentication() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody(FORK_JSON))
        val repository = repository()

        val fork = repository.fork("openai", "codex").getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/forks", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("joyins/codex", fork.fullName)
    }

    @Test
    fun successfulRepositoryWriteInvalidatesCachedReads() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val cacheStore = TestGithubCacheStore().apply {
            write(
                "sentinel",
                GithubCachedResponse("cached", null, null, savedAtEpochMillis = 1L)
            )
        }
        val repository = repository(cacheStore)

        repository.setStarred("openai", "codex", true).getOrThrow()

        assertTrue(cacheStore.isEmpty())
    }

    private fun repository(cacheStore: GithubCacheStore = NoOpGithubCacheStore) = GithubRepositoryActionsRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val FORK_JSON = """
            {
              "id": 900,
              "name": "codex",
              "full_name": "joyins/codex",
              "description": "Fork",
              "language": "Kotlin",
              "stargazers_count": 0,
              "updated_at": "2026-08-16T00:00:00Z",
              "private": false,
              "html_url": "https://github.com/joyins/codex"
            }
        """.trimIndent()
    }
}
