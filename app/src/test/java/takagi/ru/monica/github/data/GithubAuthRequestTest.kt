package takagi.ru.monica.github.data

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GithubAuthRequestTest {
    @Test
    fun cancellationAbortsTheRequestBeforeHeadersArrive() = runTest {
        val call = PendingCall()
        var delivered = false
        val request = launch {
            call.readAuthResponse { it.body?.string() }
            delivered = true
        }
        runCurrent()
        assertTrue(call.isExecuted())

        request.cancelAndJoin()

        assertTrue(call.isCanceled())
        assertFalse(delivered)
    }

    @Test
    fun cancellationWhileReadingClosesTheBodyAndDiscardsTheParsedToken() = runTest {
        val call = PendingCall()
        val body = TrackingBody()
        var delivered = false
        lateinit var request: Job
        request = launch {
            call.readAuthResponse {
                request.cancel()
                "late-token-result"
            }
            delivered = true
        }
        runCurrent()

        call.respond(body)
        request.join()

        assertTrue(call.isCanceled())
        assertTrue(body.closed)
        assertFalse(delivered)
    }

    @Test
    fun responseDecodingErrorsReachTheCallerAndCloseTheBody() = runTest {
        val call = PendingCall()
        val body = TrackingBody()
        val decodingError = IOException("Invalid response")
        val request = async {
            runCatching { call.readAuthResponse { throw decodingError } }
        }
        runCurrent()

        call.respond(body)

        val receivedError = request.await().exceptionOrNull()
        assertTrue(receivedError is IOException)
        assertEquals(decodingError.message, receivedError?.message)
        // Coroutine stack-trace recovery can copy the error and retain the original as its cause.
        assertTrue(generateSequence(receivedError) { it.cause }.any { it === decodingError })
        assertTrue(body.closed)
    }

    private class PendingCall : Call {
        private var callback: Callback? = null
        private var cancelled = false
        override fun request(): Request = Request.Builder().url("https://example.test/auth").build()
        override fun execute(): Response = error("Use the asynchronous request in this test")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = PendingCall()

        fun respond(body: ResponseBody) {
            checkNotNull(callback).onResponse(
                this,
                Response.Builder()
                    .request(request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            )
        }
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val source = object : ForwardingSource(Buffer().writeUtf8("response")) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 8L
        override fun source(): BufferedSource = source
    }
}
