package takagi.ru.monica.steam.friends.chat.richmedia.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatAttachmentCallTest {
    @Test
    fun cancellingUploadCoroutineCancelsTheActiveOkHttpCall() = runTest {
        val call = HangingCall()
        val request = async { call.awaitSteamChatResponse() }
        runCurrent()

        request.cancel()
        runCurrent()

        assertTrue(call.isCanceled())
        assertTrue(request.isCancelled)
    }

    private class HangingCall : Call {
        private val request = Request.Builder().url("https://steamcommunity.com/chat/").build()
        private var executed = false
        private var cancelled = false

        override fun request(): Request = request

        override fun execute(): Response = throw IOException("not used")

        override fun enqueue(responseCallback: Callback) {
            executed = true
        }

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = cancelled

        override fun timeout(): Timeout = Timeout().timeout(90, TimeUnit.SECONDS)

        override fun clone(): Call = HangingCall()
    }
}
