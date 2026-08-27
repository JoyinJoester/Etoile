package takagi.ru.monica.steam.token.loginchallenge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginCaptchaUrlTest {
    @Test
    fun buildsSteamCommunityHttpsUrl() {
        assertEquals(
            "https://steamcommunity.com/login/rendercaptcha/?gid=123456",
            SteamLoginCaptchaUrl.build("123456")
        )
    }

    @Test
    fun encodesGidAsQueryDataWithoutChangingHost() {
        val url = requireNotNull(SteamLoginCaptchaUrl.build("https://evil.example/a+b"))

        assertTrue(url.startsWith("https://steamcommunity.com/login/rendercaptcha/?gid="))
        assertTrue(url.endsWith("https%3A%2F%2Fevil.example%2Fa%2Bb"))
        assertFalse(url.startsWith("https://evil.example"))
    }

    @Test
    fun rejectsBlankOrControlCharacterGid() {
        assertNull(SteamLoginCaptchaUrl.build(""))
        assertNull(SteamLoginCaptchaUrl.build("bad\tgid"))
    }
}
