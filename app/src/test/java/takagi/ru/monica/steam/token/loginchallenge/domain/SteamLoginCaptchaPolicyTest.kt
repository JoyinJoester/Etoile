package takagi.ru.monica.steam.token.loginchallenge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SteamLoginCaptchaPolicyTest {
    @Test
    fun ignoresCaptchaFieldsWhenChallengeIsNotRequired() {
        assertSame(
            SteamLoginCaptchaResolution.NotRequired,
            SteamLoginCaptchaPolicy.resolve(
                required = false,
                captchaGid = "123",
                legacyCaptchaGid = "456"
            )
        )
    }

    @Test
    fun prefersCaptchaGidField() {
        val resolution = SteamLoginCaptchaPolicy.resolve(
            required = true,
            captchaGid = " 123456 ",
            legacyCaptchaGid = "654321"
        )

        assertEquals("123456", (resolution as SteamLoginCaptchaResolution.Required).gid)
    }

    @Test
    fun acceptsLegacyCaptchaGidField() {
        val resolution = SteamLoginCaptchaPolicy.resolve(
            required = true,
            captchaGid = null,
            legacyCaptchaGid = "legacy-gid"
        )

        assertEquals("legacy-gid", (resolution as SteamLoginCaptchaResolution.Required).gid)
    }

    @Test
    fun reportsMissingOrInvalidGid() {
        assertSame(
            SteamLoginCaptchaResolution.MissingGid,
            SteamLoginCaptchaPolicy.resolve(true, " ", null)
        )
        assertSame(
            SteamLoginCaptchaResolution.MissingGid,
            SteamLoginCaptchaPolicy.resolve(true, "bad\ngid", null)
        )
        assertEquals(1003, SteamLoginCaptchaPolicy.CONFIRMATION_TYPE)
    }
}
