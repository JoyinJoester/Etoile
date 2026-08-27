package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
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
import takagi.ru.monica.github.domain.GithubDeviceFlowProtocolException
import takagi.ru.monica.github.domain.GithubDevicePollResult

class GithubOAuthDeviceAuthRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun startRequestsConfiguredScopesAndMapsSafeDeviceAuthorization() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "device_code": "1234567890123456789012345678901234567890",
                  "user_code": "ABCD-EFGH",
                  "verification_uri": "https://localhost/login/device",
                  "expires_in": 900,
                  "interval": 5
                }
                """.trimIndent()
            )
        )
        val repository = repository(nowEpochMillis = { 1_000L })

        val authorization = repository.start().getOrThrow()
        val request = server.takeRequest()

        assertEquals("/login/device/code", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            "client_id=Iv1.12345678901234567890&scope=notifications%20read%3Auser%20repo",
            request.body.readUtf8()
        )
        assertEquals("ABCD-EFGH", authorization.userCode)
        assertEquals(901_000L, authorization.expiresAtEpochMillis)
        assertEquals(5, authorization.intervalSeconds)
    }

    @Test
    fun pollingMapsPendingSlowDownAndAuthorizedWithoutSendingBearerToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"slow_down"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"gho_123456789012345678901234567890","token_type":"bearer","scope":"repo,notifications,read:user"}"""
            )
        )
        val repository = repository()

        assertEquals(GithubDevicePollResult.Pending, repository.poll(DEVICE_CODE).getOrThrow())
        assertEquals(GithubDevicePollResult.SlowDown, repository.poll(DEVICE_CODE).getOrThrow())
        val authorized = repository.poll(DEVICE_CODE).getOrThrow() as GithubDevicePollResult.Authorized

        repeat(3) {
            val request = server.takeRequest()
            assertEquals("/login/oauth/access_token", request.path)
            assertNull(request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
        }
        assertEquals("gho_123456789012345678901234567890", authorized.token.accessToken)
        assertEquals(setOf("repo", "notifications", "read:user"), authorized.token.scopes)
    }

    @Test
    fun untrustedVerificationHostIsRejected() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "device_code": "1234567890123456789012345678901234567890",
                  "user_code": "ABCD-EFGH",
                  "verification_uri": "https://example.com/device",
                  "expires_in": 900,
                  "interval": 5
                }
                """.trimIndent()
            )
        )

        val error = repository().start().exceptionOrNull()

        assertTrue(error is GithubDeviceFlowProtocolException)
    }

    @Test
    fun blankClientIdDisablesFlowWithoutNetworkRequest() = runTest {
        val repository = GithubOAuthDeviceAuthRepository(
            client = OkHttpClient(),
            clientId = "",
            baseUrl = server.url("/login/").toString()
        )

        assertFalse(repository.isConfigured)
        assertTrue(repository.start().isFailure)
        assertEquals(0, server.requestCount)
    }

    private fun repository(nowEpochMillis: () -> Long = { 0L }) = GithubOAuthDeviceAuthRepository(
        client = OkHttpClient(),
        clientId = "Iv1.12345678901234567890",
        baseUrl = server.url("/login/").toString(),
        nowEpochMillis = nowEpochMillis
    )

    private companion object {
        const val DEVICE_CODE = "1234567890123456789012345678901234567890"
    }
}
