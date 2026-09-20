package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GithubTokenRefreshRepositoryTest {
    @Test fun rotatesBothTokensAndUsesRefreshGrant() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"access_token":"ghu_123456789012345678901234567890","token_type":"bearer","refresh_token":"ghr_123456789012345678901234567890","expires_in":28800,"refresh_token_expires_in":15897600}"""))
            val repository = GithubTokenRefreshRepository(OkHttpClient(), "test-client", "test-secret",
                server.url("/token").toString(), { 1000L })
            val result = repository.refresh("ghr_" + "x".repeat(40)).getOrThrow()
            assertEquals(28_801_000L, result.expiresAtEpochMillis)
            assertEquals(15_897_601_000L, result.refreshExpiresAtEpochMillis)
            assertTrue(result.refreshToken!!.startsWith("ghr_123"))
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("grant_type=refresh_token"))
        }
    }

    @Test fun rejectsErrorsAndIncompleteRotationWithoutLeakingResponse() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = GithubTokenRefreshRepository(OkHttpClient(), "test-client", "test-secret",
                server.url("/token").toString())
            for (body in listOf(
                """{"error":"bad_refresh_token","error_description":"private-value"}""",
                """{"access_token":"ghu_123456789012345678901234567890","token_type":"bearer"}"""
            )) {
                server.enqueue(MockResponse().setBody(body))
                val result = repository.refresh("ghr_" + "x".repeat(40))
                assertTrue(result.isFailure)
                assertFalse(result.exceptionOrNull().toString().contains("private-value"))
            }
        }
    }
}
