package takagi.ru.monica.steam.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDiagLoggerTest {
    @Test
    fun appendBeforeInitializationIsSafeInJvmTests() {
        SteamDiagLogger.append("voice test before Android context")
    }

    @Test
    fun sanitizerRemovesJsonHeadersUrlsSteamIdsAndInjectedLines() {
        val sanitized = sanitizeSteamDiagnosticLine(
            """Authorization: Bearer abc.def.ghi
            {"access_token":"token-value","sessionid":"session-value"}
            url=https://steamcommunity.com/chat?access_token=query-secret&sessionid=cookie-secret
            partner=76561198012345678""".trimIndent()
        )

        listOf(
            "abc.def.ghi",
            "token-value",
            "session-value",
            "query-secret",
            "cookie-secret",
            "76561198012345678"
        ).forEach { secret -> assertFalse(secret, sanitized.contains(secret)) }
        assertFalse(sanitized.contains('\n'))
        assertTrue(sanitized.contains("<redacted>"))
    }
}
