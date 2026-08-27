package takagi.ru.monica.github.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubRateLimitSnapshot

class GithubRateLimitStoreTest {
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
    fun interceptorCapturesTypedRateLimitHeaders() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-RateLimit-Resource", "core")
                .setHeader("X-RateLimit-Limit", "5000")
                .setHeader("X-RateLimit-Remaining", "9")
                .setHeader("X-RateLimit-Used", "4991")
                .setHeader("X-RateLimit-Reset", "1786827600")
        )
        val store = GithubRateLimitStore()
        val client = OkHttpClient.Builder()
            .addInterceptor(GithubRateLimitInterceptor(store))
            .build()

        client.newCall(Request.Builder().url(server.url("/rate")).build()).execute().close()

        val snapshot = store.state.value.getValue("core")
        assertEquals(5000, snapshot.limit)
        assertEquals(9, snapshot.remaining)
        assertEquals(4991, snapshot.used)
        assertTrue(snapshot.isLow)
    }

    @Test
    fun newerUsageWinsWhenConcurrentResponsesArriveOutOfOrder() {
        val store = GithubRateLimitStore()
        store.update(
            GithubRateLimitSnapshot("core", 5000, 4000, 1000, 2000)
        )
        store.update(
            GithubRateLimitSnapshot("core", 5000, 4500, 500, 2000)
        )
        store.update(
            GithubRateLimitSnapshot("core", 5000, 4999, 1, 3000)
        )

        assertEquals(1, store.state.value.getValue("core").used)
        assertEquals(3000, store.state.value.getValue("core").resetAtEpochSeconds)
    }
}
