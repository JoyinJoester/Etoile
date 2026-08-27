package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.web.domain.SteamFamilyViewSessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreAccountCurrencyRequestTest {
    @Test
    fun authenticatedRequestLetsSteamResolveAccountRegion() {
        val request = buildSteamStoreRequest(
            path = "/api/featuredcategories",
            query = mapOf("l" to "schinese"),
            steamLoginSecure = "76561198000000000||token",
            countryCode = "TW"
        )

        assertEquals("store.steampowered.com", request.url.host)
        assertEquals("TW", request.url.queryParameter("cc"))
        assertTrue(request.header("Cookie").orEmpty().contains("steamLoginSecure="))
        assertTrue(request.header("Cookie").orEmpty().contains("%7C%7C"))
        assertFalse(request.header("Cookie").orEmpty().contains("||"))
        assertFalse(request.header("Cookie").orEmpty().contains("Domain="))
    }

    @Test
    fun guestRequestDoesNotInventLoginCookieOrCountry() {
        val request = buildSteamStoreRequest(
            path = "/api/appdetails",
            query = mapOf("appids" to "620", "l" to "schinese"),
            steamLoginSecure = null
        )

        assertNull(request.header("Cookie"))
        assertNull(request.url.queryParameter("cc"))
    }

    @Test
    fun matureStorePageRequestIncludesAgeGateCookies() {
        val request = buildSteamStoreRequest(
            path = "/app/1091500/",
            query = mapOf("l" to "schinese"),
            steamLoginSecure = "76561198000000000||token",
            countryCode = "CN"
        )

        val cookie = request.header("Cookie").orEmpty()
        assertTrue(cookie.contains("birthtime=0"))
        assertTrue(cookie.contains("lastagecheckage=1-January-1980"))
        assertTrue(cookie.contains("steamLoginSecure="))
    }

    @Test
    fun authenticatedRequestsHaveAnExplicitCountryWhenResolved() {
        val request = buildSteamStoreRequest(
            path = "/api/appdetails",
            query = mapOf("appids" to "620", "l" to "schinese"),
            steamLoginSecure = "session",
            countryCode = "CN"
        )
        assertEquals("CN", request.url.queryParameter("cc"))
    }

    @Test
    fun familyViewCookieIsSentOnlyForItsSteamAccount() {
        val firstSteamId = "76561198000000000"
        val secondSteamId = "76561198000000001"
        SteamFamilyViewSessions.capture(firstSteamId, "steamparental=family-session")
        try {
            val matchingRequest = buildSteamStoreRequest(
                path = "/api/featuredcategories",
                query = mapOf("l" to "schinese"),
                steamLoginSecure = "$firstSteamId||first-token",
            )
            val otherAccountRequest = buildSteamStoreRequest(
                path = "/api/featuredcategories",
                query = mapOf("l" to "schinese"),
                steamLoginSecure = "$secondSteamId||second-token",
            )

            assertTrue(
                matchingRequest.header("Cookie").orEmpty()
                    .contains("steamparental=family-session"),
            )
            assertFalse(
                otherAccountRequest.header("Cookie").orEmpty()
                    .contains("steamparental="),
            )
        } finally {
            SteamFamilyViewSessions.clear(firstSteamId)
        }
    }
}
