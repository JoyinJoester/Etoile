package takagi.ru.monica.github.data

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import takagi.ru.monica.github.domain.*

/** Uses real repository entry points; no production tokens or remote services. */
@RunWith(Parameterized::class)
class GithubMutationTransportTest(private val operation: Operation) {
    enum class Operation(val method: String) {
        WORKFLOW_RUN("POST"), ISSUE_COMMENT("POST"), PULL_REQUEST_CREATE("POST"), RELEASE_CREATE("POST"),
        RELEASE_UPLOAD("POST"), RELEASE_DELETE("DELETE"), WEBHOOK_REDELIVERY("POST"), FILE_WRITE("PUT"),
        STAR_REMOVE("DELETE"), REPOSITORY_CREATE("POST"), BLOCK_USER("PUT"), NOTIFICATION_READ("PATCH"), REVIEW_THREAD_RESOLVE("POST")
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun operations() = Operation.entries.map { arrayOf(it) }
    }
    private val requests = GithubAuthenticatedRequests(object : GithubTokenStore {
        override fun read() = "synthetic-mutation-test-token"
        override fun write(token: String) = error("unused")
        override fun clear() = error("unused")
    })
    private var uploadOpens = 0
    private var uploadCloses = 0
    private fun client(listener: EventListener = EventListener.NONE) = OkHttpClient.Builder()
        .eventListener(listener).callTimeout(10, TimeUnit.SECONDS).build()

    @Test fun writeReturnsTheFirst503WithoutReplayingEvenWhenRetryAfterIsZero() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(500))
            val result = invoke(server, client())
            assertEquals("Must expose the original response", 503, (result.exceptionOrNull() as GithubApiException).statusCode)
            assertEquals(1, server.requestCount)
            val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals(operation.method, sent.method)
            assertEquals("Bearer synthetic-mutation-test-token", sent.getHeader("Authorization"))
            if (operation == Operation.RELEASE_UPLOAD) {
                assertEquals("payload", sent.body.readUtf8())
                assertEquals(1, uploadOpens); assertEquals(1, uploadCloses)
            }
        }
    }

    @Test fun writesRejectRedirectsWithoutCallingAnotherOrigin() = runTest {
        for (status in listOf(301, 302, 303, 307, 308)) {
            MockWebServer().use { source ->
                MockWebServer().use { destination ->
                    source.start(); destination.start()
                    source.enqueue(MockResponse().setResponseCode(status).setHeader("Location", destination.url("/unexpected")))
                    destination.enqueue(MockResponse().setResponseCode(500))
                    val result = invoke(source, client())
                    assertEquals("Redirect $status must remain unconsumed", status, (result.exceptionOrNull() as GithubApiException).statusCode)
                    assertEquals(1, source.requestCount)
                    assertEquals(0, destination.requestCount)
                }
            }
        }
        if (operation == Operation.RELEASE_UPLOAD) { assertEquals(5, uploadOpens); assertEquals(5, uploadCloses) }
    }

    @Test fun receivedWriteIsNotReplayedWhenServerDisconnects() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(500))
            assertTrue(invoke(server, client()).isFailure)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
            if (operation == Operation.RELEASE_UPLOAD) { assertEquals(1, uploadOpens); assertEquals(1, uploadCloses) }
        }
    }

    @Test fun cancellingWriteCancelsTheActualCallAndNeverStartsAnotherAttempt() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val cancelled = AtomicBoolean()
            val client = client(object : EventListener() { override fun canceled(call: Call) { cancelled.set(true) } })
            val pending = async { invoke(server, client) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
            assertTrue("The socket must be cancelled, not just its UI result", cancelled.get())
            assertEquals(1, server.requestCount)
        }
    }

    private suspend fun invoke(server: MockWebServer, client: OkHttpClient): Result<*> {
        val base = server.url("/").toString()
        return when (operation) {
            Operation.WORKFLOW_RUN -> GithubActionsRepositoryImpl(requests, client, baseUrl = base)
                .performRunAction("o", "r", 7, GithubWorkflowRunAction.RERUN)
            Operation.ISSUE_COMMENT -> GithubIssuesRepositoryImpl(requests, client, baseUrl = base)
                .addComment("o", "r", 7, GithubIssueCommentDraft.fromInput("Publish once").getOrThrow())
            Operation.PULL_REQUEST_CREATE -> GithubPullRequestsRepositoryImpl(requests, client, baseUrl = base)
                .create("o", "r", GithubCreatePullRequestDraft.fromInput("Title", "Body", "main", "feature").getOrThrow())
            Operation.RELEASE_CREATE -> GithubReleasesRepositoryImpl(requests, client, baseUrl = base)
                .createRelease("o", "r", GithubReleaseDraft.fromInput("v1", "Title", "Notes", "main", true, false).getOrThrow())
            Operation.RELEASE_UPLOAD -> GithubReleasesRepositoryImpl(requests, client, baseUrl = base)
                .uploadAsset("o", "r", 7, GithubReleaseAssetUpload.fromFile("test.bin", "", "application/octet-stream", 7) {
                    uploadOpens++
                    val input = ByteArrayInputStream("payload".toByteArray())
                    object : GithubAssetInput {
                        override fun read(target: ByteArray, offset: Int, count: Int) = input.read(target, offset, count)
                        override fun close() { uploadCloses++; input.close() }
                    }
                }.getOrThrow())
            Operation.RELEASE_DELETE -> GithubReleasesRepositoryImpl(requests, client, baseUrl = base).deleteRelease("o", "r", 7)
            Operation.WEBHOOK_REDELIVERY -> GithubRepositoryDetailsRepositoryImpl(requests, client, baseUrl = base).redeliverWebhook("o", "r", 7, 8)
            Operation.FILE_WRITE -> GithubRepositoryContentsRepositoryImpl(requests, client, baseUrl = base)
                .write("o", "r", GithubFileWrite.fromInput("test.md", "main", "Change", "New text", null).getOrThrow())
            Operation.STAR_REMOVE -> GithubRepositoryActionsRepositoryImpl(requests, client, baseUrl = base).setStarred("o", "r", false)
            Operation.REPOSITORY_CREATE -> GithubUserRepositoriesRepositoryImpl(requests, client, baseUrl = base)
                .create(GithubRepositoryCreateDraft.fromInput("repo", "Description", true, false).getOrThrow())
            Operation.BLOCK_USER -> GithubPublicUserRepositoryImpl(requests, client, baseUrl = base).setBlocked("someone", true)
            Operation.NOTIFICATION_READ -> GithubNotificationsRepositoryImpl(requests, client, baseUrl = base).markRead("7")
            Operation.REVIEW_THREAD_RESOLVE -> GithubPullRequestsRepositoryImpl(requests, client, baseUrl = base).setReviewThreadResolved("T1", true)
        }
    }
}
