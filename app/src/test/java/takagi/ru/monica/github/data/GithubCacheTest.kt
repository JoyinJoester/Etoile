package takagi.ru.monica.github.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubCacheFallbackSnapshot

class GithubCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulResponseIsStoredAndEtagIsSentOnNextRequest() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Link", "<https://api.github.com/next>; rel=\"next\"")
                .setBody("payload-v1")
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val store = TestGithubCacheStore()
        val executor = GithubCachedGetExecutor(store) { 100L }

        val first = execute(executor, "cache-key")
        val second = execute(executor, "cache-key")

        assertEquals("payload-v1", first.first)
        assertEquals("payload-v1", second.first)
        assertEquals("<https://api.github.com/next>; rel=\"next\"", second.second)
        assertEquals(null, server.takeRequest().getHeader("If-None-Match"))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(100L, store.read("cache-key")?.savedAtEpochMillis)
    }

    @Test
    fun freshCachedResponseSkipsNetworkUntilItExpires() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v1\"").setBody("payload-v1"))
        server.enqueue(MockResponse().setResponseCode(304))
        val store = TestGithubCacheStore()
        var now = 100L
        val executor = GithubCachedGetExecutor(store, nowEpochMillis = { now })

        val first = execute(executor, "cache-key", maxAgeMillis = 1_000L)
        now = 500L
        val fresh = execute(executor, "cache-key", maxAgeMillis = 1_000L)

        assertEquals(first, fresh)
        assertEquals(1, server.requestCount)

        now = 1_101L
        val validated = execute(executor, "cache-key", maxAgeMillis = 1_000L)

        assertEquals(first, validated)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(1_101L, store.read("cache-key")?.savedAtEpochMillis)
    }

    @Test
    fun defaultFreshnessWindowSkipsRepeatedNavigationRequests() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("payload-v1")
        )
        val store = TestGithubCacheStore()
        var now = 100L
        val executor = GithubCachedGetExecutor(store, nowEpochMillis = { now })

        execute(executor, "cache-key", maxAgeMillis = GithubCachePolicy.DEFAULT_MAX_AGE_MILLIS)
        now += GithubCachePolicy.DEFAULT_MAX_AGE_MILLIS - 1L
        val cached = execute(executor, "cache-key", maxAgeMillis = GithubCachePolicy.DEFAULT_MAX_AGE_MILLIS)

        assertEquals("payload-v1", cached.first)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun exhaustedRateLimitFallsBackToCacheButOrdinaryForbiddenDoesNot() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("cached"))
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
        )
        server.enqueue(MockResponse().setResponseCode(403))
        val executor = GithubCachedGetExecutor(TestGithubCacheStore())

        execute(executor, "cache-key")
        assertEquals("cached", execute(executor, "cache-key").first)

        val error = runCatching { execute(executor, "cache-key") }.exceptionOrNull()
        assertTrue(error is GithubApiException)
        assertEquals(403, (error as GithubApiException).statusCode)
    }

    @Test
    fun serverFailureFallsBackToCachedBodyButPermissionFailureDoesNot() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("cached")
        )
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(401))
        val store = TestGithubCacheStore()
        val executor = GithubCachedGetExecutor(store)

        execute(executor, "cache-key")
        val fallback = execute(executor, "cache-key")
        assertEquals("cached", fallback.first)

        var thrown = false
        try {
            execute(executor, "cache-key")
        } catch (error: GithubApiException) {
            thrown = true
            assertEquals(401, error.statusCode)
        }
        assertTrue(thrown)
    }

    @Test
    fun fallbackStatusTracksCacheAgeAndClearsAfterServerValidation() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setBody("cached")
        )
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(304))
        val store = TestGithubCacheStore()
        val statusStore = GithubCacheFallbackStore()
        var now = 100L
        val executor = GithubCachedGetExecutor(store, statusStore) { now }

        execute(executor, "cache-key")
        now = 200L
        execute(executor, "cache-key")

        assertEquals(
            GithubCacheFallbackSnapshot(cachedAtEpochMillis = 100L, detectedAtEpochMillis = 200L),
            statusStore.state.value
        )

        now = 300L
        execute(executor, "cache-key")

        assertNull(statusStore.state.value)
    }

    @Test
    fun validatingOneCacheKeyKeepsOtherFallbackVisible() {
        val statusStore = GithubCacheFallbackStore()
        statusStore.onFallback("inbox", cachedAtEpochMillis = 10L, detectedAtEpochMillis = 20L)
        statusStore.onFallback("stars", cachedAtEpochMillis = 30L, detectedAtEpochMillis = 40L)

        statusStore.onValidated("stars")

        assertEquals(
            GithubCacheFallbackSnapshot(cachedAtEpochMillis = 10L, detectedAtEpochMillis = 20L),
            statusStore.state.value
        )

        statusStore.clear()
        assertNull(statusStore.state.value)
    }

    @Test
    fun invalidatingStoreClearsFallbackStatusWithCachedBodies() {
        val delegate = TestGithubCacheStore().apply {
            write(
                "cache-key",
                GithubCachedResponse("cached", null, null, savedAtEpochMillis = 10L)
            )
        }
        val statusStore = GithubCacheFallbackStore().apply {
            onFallback("cache-key", cachedAtEpochMillis = 10L, detectedAtEpochMillis = 20L)
        }
        val store = GithubInvalidatingCacheStore(delegate, statusStore::clear)

        store.clear()

        assertTrue(delegate.isEmpty())
        assertNull(statusStore.state.value)
    }

    @Test
    fun cacheScopesAreOneWayAndDifferentForDifferentTokens() {
        val first = GithubAuthenticatedRequests(FakeTokenStore("token-one")).cacheScope()
        val second = GithubAuthenticatedRequests(FakeTokenStore("token-two")).cacheScope()

        assertNotEquals("token-one", first)
        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertFalse(first.contains("token"))
    }

    private fun execute(
        executor: GithubCachedGetExecutor,
        key: String,
        maxAgeMillis: Long = 0L
    ): Pair<String, String?> = executor.execute(
        client = client,
        cacheKey = key,
        request = { etag ->
            Request.Builder()
                .url(server.url("/notifications"))
                .apply { if (etag != null) header("If-None-Match", etag) }
                .get()
                .build()
        },
        decode = { body, link -> body to link },
        maxAgeMillis = maxAgeMillis
    )

    private class FakeTokenStore(private val token: String?) : GithubTokenStore {
        override fun read(): String? = token
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }
}
