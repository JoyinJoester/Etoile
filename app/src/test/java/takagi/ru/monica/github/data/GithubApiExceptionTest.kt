package takagi.ru.monica.github.data

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubApiExceptionTest {
    @Test
    fun forbiddenWithSpentQuotaIsNotAPermissionFailure() {
        val error = GithubApiException.of(response(403, "X-RateLimit-Remaining" to "0"))

        assertEquals(403, error.statusCode)
        assertTrue(error.rateLimited)
    }

    @Test
    fun forbiddenWithoutQuotaHeadersStaysAPermissionFailure() {
        assertFalse(GithubApiException.of(response(403)).rateLimited)
    }

    @Test
    fun forbiddenWithRetryAfterIsSecondaryLimit() {
        assertTrue(GithubApiException.of(response(403, "Retry-After" to "37")).rateLimited)
    }

    @Test
    fun tooManyRequestsIsThrottlingEvenWithoutHeaders() {
        assertTrue(GithubApiException.of(response(429)).rateLimited)
    }

    @Test
    fun everyResponseCarriesQuotaHeadersSoOtherStatusesAreNotThrottling() {
        assertFalse(
            GithubApiException.of(response(404, "X-RateLimit-Remaining" to "0", "Retry-After" to "12")).rateLimited
        )
    }

    private fun response(code: Int, vararg headers: Pair<String, String>): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url("https://api.github.com/repos/openai/codex/issues").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body("".toResponseBody(null))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }
}
