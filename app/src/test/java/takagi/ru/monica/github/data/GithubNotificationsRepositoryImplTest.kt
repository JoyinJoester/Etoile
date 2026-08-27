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
import takagi.ru.monica.github.domain.GithubNotificationReason

class GithubNotificationsRepositoryImplTest {
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
    fun notificationsUseAuthenticatedEndpointAndMapResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/notifications?page=2")}>; rel=\"next\"")
                .setBody(NOTIFICATIONS_JSON)
        )
        val repository = repository()

        val result = repository.notifications(page = 1, perPage = 50).getOrThrow()
        val request = server.takeRequest()

        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("/notifications?all=false&participating=false&per_page=50&page=1", request.path)
        assertEquals(1, result.items.size)
        assertEquals(2, result.nextPage)
        assertEquals(GithubNotificationReason.REVIEW_REQUESTED, result.items.single().reason)
        assertEquals("etoile/mobile", result.items.single().repository)
        assertEquals("https://github.com/etoile/mobile/pull/42", result.items.single().subjectUrl)
    }

    @Test
    fun notificationsUseEtagAndCachedBodyWhenServerReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"notifications-v1\"")
                .setBody(NOTIFICATIONS_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = repository(cache)

        val first = repository.notifications(page = 1, perPage = 50).getOrThrow()
        val second = repository.notifications(page = 1, perPage = 50).getOrThrow()
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()

        assertEquals(first.items, second.items)
        assertEquals(null, firstRequest.getHeader("If-None-Match"))
        assertEquals("\"notifications-v1\"", secondRequest.getHeader("If-None-Match"))
        assertEquals(NOTIFICATIONS_JSON, cache.read(cacheKeyForNotifications())?.body)
    }

    @Test
    fun releaseNotificationsOpenTheNativeReleaseList() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RELEASE_NOTIFICATION_JSON))

        val notification = repository().notifications().getOrThrow().items.single()

        assertEquals("https://github.com/etoile/mobile/releases", notification.subjectUrl)
    }

    @Test
    fun markReadUsesThreadPatchEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(205))

        val result = repository().markRead("123")
        val request = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("PATCH", request.method)
        assertEquals("/notifications/threads/123", request.path)
    }

    @Test
    fun markDoneDeletesTheNotificationThread() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().markDone("123")
        val request = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("DELETE", request.method)
        assertEquals("/notifications/threads/123", request.path)
    }

    @Test
    fun unsubscribeDeletesTheSubscriptionThenMarksTheThreadDone() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().unsubscribeAndMarkDone("123")
        val unsubscribeRequest = server.takeRequest()
        val doneRequest = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("DELETE", unsubscribeRequest.method)
        assertEquals("/notifications/threads/123/subscription", unsubscribeRequest.path)
        assertEquals("DELETE", doneRequest.method)
        assertEquals("/notifications/threads/123", doneRequest.path)
    }

    private fun repository(cacheStore: GithubCacheStore = NoOpGithubCacheStore) = GithubNotificationsRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private fun cacheKeyForNotifications(): String =
        GithubCacheKeys.endpoint(
            "notifications",
            GithubAuthenticatedRequests(FakeTokenStore()).cacheScope(),
            server.url("/notifications?all=false&participating=false&per_page=50&page=1").toString()
        )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val NOTIFICATIONS_JSON = """
            [
              {
                "id": "123",
                "reason": "review_requested",
                "unread": true,
                "updated_at": "2026-08-16T00:00:00Z",
                "subject": {
                  "title": "Review this change",
                  "type": "PullRequest",
                  "url": "https://api.github.com/repos/etoile/mobile/pulls/42"
                },
                "repository": {
                  "full_name": "etoile/mobile",
                  "html_url": "https://github.com/etoile/mobile"
                }
              }
            ]
        """.trimIndent()

        val RELEASE_NOTIFICATION_JSON = """
            [
              {
                "id": "release-123",
                "reason": "subscribed",
                "unread": true,
                "updated_at": "2026-08-16T00:00:00Z",
                "subject": {
                  "title": "Etoile 1.2",
                  "type": "Release",
                  "url": "https://api.github.com/repos/etoile/mobile/releases/42"
                },
                "repository": {
                  "full_name": "etoile/mobile",
                  "html_url": "https://github.com/etoile/mobile"
                }
              }
            ]
        """.trimIndent()
    }
}
