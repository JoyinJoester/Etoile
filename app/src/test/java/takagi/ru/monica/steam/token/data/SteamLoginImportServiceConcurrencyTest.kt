package takagi.ru.monica.steam.token.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginImportServiceConcurrencyTest {
    @Test
    fun simultaneousCredentialLoginDoesNotSendASecondNetworkRequest() {
        runBlocking {
            val requestCount = AtomicInteger(0)
            val firstRequestStarted = CountDownLatch(1)
            val releaseFirstRequest = CountDownLatch(1)
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val index = requestCount.incrementAndGet()
                    if (index == 1) {
                        firstRequestStarted.countDown()
                        check(releaseFirstRequest.await(5, TimeUnit.SECONDS))
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            val service = SteamLoginImportService(client)

            val first = async(Dispatchers.Default) {
                service.beginLogin("account", "password")
            }
            assertTrue(firstRequestStarted.await(5, TimeUnit.SECONDS))

            val second = service.beginLogin("account", "password")

            assertTrue(second is SteamLoginImportService.LoginResult.Failure)
            assertTrue(
                (second as SteamLoginImportService.LoginResult.Failure).message.contains("处理中")
            )
            assertEquals(1, requestCount.get())

            releaseFirstRequest.countDown()
            first.await()
        }
    }
}
