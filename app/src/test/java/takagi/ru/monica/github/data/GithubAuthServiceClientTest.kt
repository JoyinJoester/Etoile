package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GithubAuthServiceClientTest {
    @Test fun exchangesWithoutSecretAndMarksAppCredential() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"access_token":"ghu_123456789012345678901234567890","refresh_token":"ghr_123456789012345678901234567890","token_type":"bearer","expires_in":28800,"refresh_token_expires_in":15897600}"""))
            val client = GithubAuthServiceClient(OkHttpClient(), server.url("/").toString(), { 1000 })
            val token = client.exchange("abcdefgh123", "v".repeat(64)).getOrThrow()
            assertEquals("github_app", token.authorizationSource)
            assertEquals(28_801_000L, token.expiresAtEpochMillis)
            val request = server.takeRequest()
            assertEquals("/v1/exchange", request.path)
            assertNull(request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("code_verifier"))
            assertFalse(body.contains("client_secret"))
        }
    }

    @Test fun refusesRedirectsForCredentialRequests() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/unexpected")))
            val client = GithubAuthServiceClient(OkHttpClient(), server.url("/").toString())
            assertTrue(client.refresh("ghr_" + "a".repeat(40)).isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
