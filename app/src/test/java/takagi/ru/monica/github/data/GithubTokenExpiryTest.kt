package takagi.ru.monica.github.data

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.GithubDeviceAccessToken

class GithubTokenExpiryTest {
    @Test fun legacyTokensHaveNoExpiry() {
        assertNull(githubTokenExpiry(1000, null))
        assertNull(validateGithubRefreshToken(null))
    }

    @Test fun computesAbsoluteExpiryWithoutOverflow() {
        assertEquals(28_801_000L, githubTokenExpiry(1000, 28_800))
        for (seconds in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertTrue(runCatching { githubTokenExpiry(1000, seconds) }.isFailure)
        }
    }

    @Test fun refreshCredentialsAreValidatedAndRedacted() {
        val refresh = "ghr_" + "a".repeat(40)
        assertEquals(refresh, validateGithubRefreshToken(refresh))
        assertTrue(runCatching { validateGithubRefreshToken(" ".repeat(40)) }.isFailure)
        val token = GithubDeviceAccessToken("secret-access", "bearer", emptySet(), refresh)
        assertFalse(token.toString().contains(refresh))
        assertFalse(token.toString().contains("secret-access"))
    }
}
