package takagi.ru.monica.steam.token.loginsecurity.data

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamMobileAuthRequestProfileTest {
    @Test
    fun appliesOneConsistentMobileIdentityWithoutBrowserHeaders() {
        val request = SteamMobileAuthRequestProfile.applyTo(
            Request.Builder().url(
                "https://api.steampowered.com/IAuthenticationService/" +
                    "BeginAuthSessionViaCredentials/v1/"
            )
        ).build()

        assertEquals("okhttp/4.9.2", request.header("User-Agent"))
        assertEquals("application/json, text/plain, */*", request.header("Accept"))
        assertTrue(request.header("Cookie").orEmpty().contains("mobileClient=android"))
        assertTrue(request.header("Cookie").orEmpty().contains("mobileClientVersion="))
        assertFalse(request.headers.names().contains("Origin"))
        assertFalse(request.headers.names().contains("Referer"))
    }

    @Test
    fun profileKeepsStableSteamMobileDeviceFields() {
        assertEquals("Mobile", SteamMobileAuthRequestProfile.websiteId)
        assertEquals(3L, SteamMobileAuthRequestProfile.platformType)
        assertEquals(-500L, SteamMobileAuthRequestProfile.osType)
        assertEquals(528L, SteamMobileAuthRequestProfile.gamingDeviceType)
    }
}
