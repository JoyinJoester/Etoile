package takagi.ru.monica.steam.token.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginRsaKeyProviderTest {
    @Test
    fun usesModernNestedResponseWithoutCallingFallback() {
        val communityCalls = AtomicInteger()
        val provider = provider { request ->
            when (request.url.host) {
                "api.steampowered.com" -> {
                    assertEquals("okhttp/4.9.2", request.header("User-Agent"))
                    assertTrue(request.header("Cookie").orEmpty().contains("mobileClient=android"))
                    jsonResponse(
                        request,
                        200,
                        """{"response":${validKeyJson()}}"""
                    )
                }
                else -> {
                    communityCalls.incrementAndGet()
                    jsonResponse(request, 500, "{}")
                }
            }
        }

        val result = provider.load("example") as SteamLoginRsaResult.Success

        assertEquals(SteamLoginRsaSource.AUTH_API, result.source)
        assertEquals("010001", result.key.exponentHex)
        assertEquals(0, communityCalls.get())
    }

    @Test
    fun fallsBackToCommunityWhenModernEndpointIsUnavailable() {
        val requestedHosts = mutableListOf<String>()
        val provider = provider { request ->
            requestedHosts += request.url.host
            when (request.url.host) {
                "api.steampowered.com" -> jsonResponse(request, 503, "{}")
                else -> jsonResponse(request, 200, validKeyJson())
            }
        }

        val result = provider.load("example") as SteamLoginRsaResult.Success

        assertEquals(SteamLoginRsaSource.COMMUNITY_FALLBACK, result.source)
        assertEquals(
            listOf("api.steampowered.com", "steamcommunity.com"),
            requestedHosts
        )
        assertTrue(result.key.modulusHex.length >= 32)
    }

    @Test
    fun rejectsRecoveryCodeOrMalformedPayloadAsRsaMaterial() {
        val malformed = Json.parseToJsonElement(
            """{"success":true,"publickey_mod":"R12345","publickey_exp":"010001","timestamp":"now"}"""
        ).jsonObject

        assertEquals(null, parseSteamLoginRsaKey(malformed))
    }

    @Test
    fun returnsActionableFailureAfterBothEndpointsFail() {
        val events = mutableListOf<String>()
        val provider = provider(events::add) { request ->
            jsonResponse(request, 502, "{}")
        }

        val result = provider.load("example") as SteamLoginRsaResult.Failure

        assertTrue(result.reason.contains("新版与兼容接口"))
        assertTrue(events.any { it.contains("source=auth_api result=fallback") })
        assertTrue(events.any { it.contains("source=community result=failed") })
        assertTrue(events.none { it.contains("example") })
    }

    private fun provider(
        logger: (String) -> Unit = {},
        responder: (okhttp3.Request) -> Response
    ): SteamLoginRsaKeyProvider {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responder(chain.request()) })
            .build()
        return SteamLoginRsaKeyProvider(client, Json { ignoreUnknownKeys = true }, logger)
    }

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun validKeyJson(): String =
        """{"success":true,"publickey_mod":"${"ab".repeat(128)}","publickey_exp":"010001","timestamp":"303723700000"}"""
}
