package takagi.ru.monica.github.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.GithubCreatePullRequestDraft
import takagi.ru.monica.github.domain.GithubWorkflowRunAction

class GithubApiTransportTest {
    private val requests = GithubAuthenticatedRequests(object : GithubTokenStore {
        override fun read() = "synthetic-transport-token"
        override fun write(token: String) = error("unused")
        override fun clear() = error("unused")
    })

    @Test fun readOnlyGetKeepsTheCallersRetryAndRedirectPolicy() = runTest {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.start(); destination.start()
                source.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
                source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/read")))
                destination.enqueue(MockResponse().setBody("Read once after redirect"))
                val request = Request.Builder().url(source.url("/read")).get().build()
                val body = withContext(Dispatchers.IO) { OkHttpClient().readGithubResponse(request) { it.body!!.string() } }
                assertEquals("Read once after redirect", body)
                assertEquals(2, source.requestCount)
                assertEquals(1, destination.requestCount)
            }
        }
    }

    @Test fun cancellationDuringMutationResponseBodyReadClosesTheActualCall() = runTest {
        MockWebServer().use { server ->
            // QueueDispatcher applies throttleBody to the incoming POST as well.
            // Peek without throttling so only the response body is deliberately slow.
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun peek() = MockResponse()
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setResponseCode(201)
                    .setBody("{\"padding\":\"${"x".repeat(500)}\"}").throttleBody(1, 100, TimeUnit.MILLISECONDS)
            }
            server.start()
            val headers = CountDownLatch(1)
            val bodyStarted = CountDownLatch(1)
            val cancelled = AtomicBoolean()
            val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).eventListener(object : EventListener() {
                override fun responseHeadersEnd(call: Call, response: okhttp3.Response) { headers.countDown() }
                override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
                override fun canceled(call: Call) { cancelled.set(true) }
            }).build()
            val repository = GithubPullRequestsRepositoryImpl(requests, client, baseUrl = server.url("/").toString())
            val pending = async(Dispatchers.IO) { repository.create("o", "r", GithubCreatePullRequestDraft.fromInput("Title", "Body", "main", "feature").getOrThrow()) }
            withContext(Dispatchers.IO) {
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertTrue(headers.await(5, TimeUnit.SECONDS))
                assertTrue(bodyStarted.await(5, TimeUnit.SECONDS))
            }
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
            assertTrue(cancelled.get())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun mutationKeepsCallerInterceptorsTimeoutHeadersAndPayload() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(204))
            val actualTimeout = AtomicLong()
            val oneShot = AtomicBoolean()
            val client = OkHttpClient.Builder().callTimeout(77, TimeUnit.SECONDS).addInterceptor { chain ->
                actualTimeout.set(chain.call().timeout().timeoutNanos())
                oneShot.set(chain.request().body!!.isOneShot())
                chain.proceed(chain.request().newBuilder().header("X-Transport-Test", "retained").build())
            }.build()
            assertTrue(GithubActionsRepositoryImpl(requests, client, baseUrl = server.url("/").toString())
                .performRunAction("o", "r", 7, GithubWorkflowRunAction.CANCEL).isSuccess)
            val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("retained", sent.getHeader("X-Transport-Test"))
            assertEquals("Bearer synthetic-transport-token", sent.getHeader("Authorization"))
            assertEquals("{}", sent.body.readUtf8())
            assertEquals("application/json; charset=utf-8", sent.getHeader("Content-Type"))
            assertEquals(TimeUnit.SECONDS.toNanos(77), actualTimeout.get())
            assertTrue(oneShot.get())
            assertTrue(client.followRedirects)
            assertTrue(client.retryOnConnectionFailure)
        }
    }
}
