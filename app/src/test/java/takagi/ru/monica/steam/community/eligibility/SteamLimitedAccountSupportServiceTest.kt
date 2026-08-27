package takagi.ru.monica.steam.community.eligibility

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.eligibility.data.SteamLimitedAccountSupportService
import takagi.ru.monica.steam.data.SteamAccount

class SteamLimitedAccountSupportServiceTest {
    @Test
    fun supportRequestReadsSpendRatioFromLocalizedSupportHomePage() {
        val requestedUrl = AtomicReference<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrl.set(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <html><body>
                          <p>Steam 上的花费额度： ${'$'}0.00 / ${'$'}5.00 USD</p>
                        </body></html>
                        """.trimIndent().toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val progress = SteamLimitedAccountSupportService(client).fetch(account())

        assertEquals("https://help.steampowered.com/zh-cn/", requestedUrl.get())
        assertEquals(0, progress?.spentUsdCents)
        assertEquals(500, progress?.thresholdUsdCents)
    }

    @Test
    fun supportRequestFallsBackToLimitedAccountWizardWhenHomePageHasNoProgress() {
        val requestCount = AtomicInteger()
        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls += chain.request().url.toString()
                val body = if (requestCount.getAndIncrement() == 0) {
                    "<html><body>Steam Support home</body></html>"
                } else {
                    """
                    <html><body>
                      <p>Your account has spent ${'$'}2.35 USD out of the ${'$'}5.00 USD required.</p>
                    </body></html>
                    """.trimIndent()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val progress = SteamLimitedAccountSupportService(client).fetch(account())

        assertEquals(
            listOf(
                "https://help.steampowered.com/zh-cn/",
                "https://help.steampowered.com/en/wizard/HelpWithLimitedAccount"
            ),
            requestedUrls
        )
        assertEquals(235, progress?.spentUsdCents)
    }

    @Test
    fun supportRequestEncodesSteamLoginSecureCookieBeforeReadingOfficialProgress() {
        val cookie = AtomicReference<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                cookie.set(chain.request().header("Cookie").orEmpty())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <html><body>
                          <p>Your account has spent ${'$'}2.35 USD out of the ${'$'}5.00 USD required.</p>
                        </body></html>
                        """.trimIndent().toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val progress = SteamLimitedAccountSupportService(client).fetch(account())

        assertEquals(235, progress?.spentUsdCents)
        assertTrue(cookie.get().contains("steamLoginSecure=76561198000000000%7C%7Ctoken"))
        assertFalse(cookie.get().contains("steamLoginSecure=76561198000000000||token"))
    }

    private fun account(): SteamAccount = SteamAccount(
        id = 1L,
        steamId = "76561198000000000",
        accountName = "test",
        displayName = "test",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = "refresh",
        steamLoginSecure = "76561198000000000||token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
