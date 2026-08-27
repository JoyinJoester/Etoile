package takagi.ru.monica.steam.token.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginRiskIntegrationTest {
    @Test
    fun throttledModernLoginDoesNotSubmitCredentialsAgain() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when {
                request.url.encodedPath.contains("GetPasswordRSAPublicKey") ->
                    jsonResponse(request, validRsaResponse())
                request.url.encodedPath.contains("BeginAuthSessionViaCredentials") ->
                    protoErrorResponse(request, eResult = 87)
                else -> errorResponse(request)
            }
        }

        val result = SteamLoginImportService(client).beginSessionLogin("account", "password")

        assertTrue(result is SteamLoginImportService.LoginResult.Failure)
        val message = (result as SteamLoginImportService.LoginResult.Failure).message
        assertTrue(message.contains("限制"))
        assertTrue(message.contains("等待"))
        assertEquals(2, requests.size)
        assertTrue(requests.none { it.url.host == "steamcommunity.com" })
        requests.filter { it.url.host == "api.steampowered.com" }.forEach { request ->
            assertEquals("okhttp/4.9.2", request.header("User-Agent"))
            assertTrue(request.header("Cookie").orEmpty().contains("mobileClient=android"))
        }
    }

    @Test
    fun malformedModernResponseDoesNotSubmitCredentialsAgain() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when {
                request.url.encodedPath.contains("GetPasswordRSAPublicKey") ->
                    jsonResponse(request, validRsaResponse())
                request.url.encodedPath.contains("BeginAuthSessionViaCredentials") ->
                    protoResponse(request, ByteArray(0))
                else -> errorResponse(request)
            }
        }

        val result = SteamLoginImportService(client).beginSessionLogin("account", "password")

        assertTrue(result is SteamLoginImportService.LoginResult.Failure)
        assertEquals(2, requests.size)
        assertTrue(requests.none { it.url.host == "steamcommunity.com" })
    }

    private fun client(responder: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build()

    private fun validRsaResponse(): String =
        """{"response":{"publickey_mod":"${"ab".repeat(128)}","publickey_exp":"010001","timestamp":"303723700000"}}"""

    private fun jsonResponse(request: Request, body: String): Response =
        response(request, body.toByteArray(), "application/json")

    private fun protoResponse(request: Request, body: ByteArray): Response =
        response(request, body, "application/octet-stream", headers = mapOf("x-eresult" to "1"))

    private fun protoErrorResponse(request: Request, eResult: Int): Response =
        response(
            request = request,
            body = ByteArray(0),
            mediaType = "application/octet-stream",
            headers = mapOf(
                "x-eresult" to eResult.toString(),
                "x-error_message" to "Account login denied due to throttling"
            )
        )

    private fun errorResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(500)
        .message("Unexpected request")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()

    private fun response(
        request: Request,
        body: ByteArray,
        mediaType: String,
        headers: Map<String, String> = emptyMap()
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .body(body.toResponseBody(mediaType.toMediaType()))
        .build()
}
