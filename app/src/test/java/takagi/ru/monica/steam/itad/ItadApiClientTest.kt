package takagi.ru.monica.steam.itad

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.itad.data.ItadApiClient
import takagi.ru.monica.steam.itad.data.ItadApiResult

class ItadApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ItadApiClient
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ItadApiClient(
            client = OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .build(),
            baseUrl = server.url("/"),
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookupUsesSteamShopIdWithoutPuttingApiKeyInUrl() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"app/620":"018d937f-012f-73b8-ab2c-898516969e6a"}"""
            )
        )

        val result = client.lookupSteamAppId(620)

        assertEquals(
            "018d937f-012f-73b8-ab2c-898516969e6a",
            (result as ItadApiResult.Success).value
        )
        val request = server.takeRequest()
        assertEquals("/lookup/id/shop/61/v1", request.requestUrl?.encodedPath)
        assertNull(request.getHeader("ITAD-API-Key"))
        assertFalse(request.requestUrl.toString().contains("key="))
        assertEquals("[\"app/620\"]", request.body.readUtf8())
    }

    @Test
    fun historyLowUsesHeaderCountryAndPreservesOfficialValues() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "id":"018d937f-012f-73b8-ab2c-898516969e6a",
                  "low":{
                    "shop":{"id":61,"name":"Steam"},
                    "price":{"amount":9.99,"amountInt":999,"currency":"CNY"},
                    "regular":{"amount":99.99,"amountInt":9999,"currency":"CNY"},
                    "cut":90,
                    "timestamp":"2026-01-01T00:00:00Z"
                  }
                }]
                """.trimIndent()
            )
        )

        val result = client.loadHistoryLow(
            "018d937f-012f-73b8-ab2c-898516969e6a",
            "CN",
            "secret-test-key"
        ) as ItadApiResult.Success

        assertEquals(999L, result.value.price.amountInt)
        assertEquals(9.99, result.value.price.amount, 0.0)
        assertEquals("CNY", result.value.price.currency)
        assertEquals("Steam", result.value.shopName)
        assertEquals("2026-01-01T00:00:00Z", result.value.timestamp)
        val request = server.takeRequest()
        assertEquals("CN", request.requestUrl?.queryParameter("country"))
        assertEquals("secret-test-key", request.getHeader("ITAD-API-Key"))
        assertFalse(request.requestUrl.toString().contains("secret-test-key"))
    }

    @Test
    fun gameInfoPreservesOfficialItadUrl() {
        val url = "https://isthereanydeal.com/game/portal-2/"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"urls":{"game":"$url"}}"""
            )
        )

        val result = client.loadGameUrl(
            "018d937f-012f-73b8-ab2c-898516969e6a",
            "secret-test-key"
        )

        assertEquals(url, (result as ItadApiResult.Success).value)
    }

    @Test
    fun gameInfoRejectsNavigationOutsideOfficialItadHosts() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"urls":{"game":"https://example.com/redirect"}}"""
            )
        )

        val result = client.loadGameUrl(
            "018d937f-012f-73b8-ab2c-898516969e6a",
            "secret-test-key"
        )

        assertTrue(result is ItadApiResult.InvalidResponse)
    }

    @Test
    fun rateLimitHonorsRetryAfterSeconds() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "120")
                .setBody("""{"status_code":429,"reason_phrase":"Too Many Requests"}""")
        )

        val result = client.loadGameUrl(
            "018d937f-012f-73b8-ab2c-898516969e6a",
            "secret-test-key"
        )

        assertTrue(result is ItadApiResult.RateLimited)
        assertEquals(
            now + 120_000L,
            (result as ItadApiResult.RateLimited).retryAfterEpochMillis
        )
    }
}
