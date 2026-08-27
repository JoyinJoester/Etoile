package takagi.ru.monica.steam.web.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFamilyViewSessionTest {
    @Test
    fun capturesOnlySteamParentalCookieFromWebViewHeader() {
        val session = SteamFamilyViewSession(ttlMillis = 60_000L)

        val captured = session.capture(
            accountSteamId = "76561198000000000",
            cookieHeader = "sessionid=secret; steamparental=family-session; steamLoginSecure=login-secret",
            nowMillis = 1_000L,
        )

        assertTrue(captured)
        assertEquals(
            "steamparental=family-session",
            session.cookieFor("76561198000000000", nowMillis = 1_001L),
        )
    }

    @Test
    fun ignoresHeadersWithoutSteamParentalCookie() {
        val session = SteamFamilyViewSession(ttlMillis = 60_000L)

        assertFalse(
            session.capture(
                accountSteamId = "76561198000000000",
                cookieHeader = "sessionid=secret; steamLoginSecure=login-secret",
                nowMillis = 1_000L,
            ),
        )
        assertNull(session.cookieFor("76561198000000000", nowMillis = 1_001L))
    }

    @Test
    fun keepsFamilyViewCookiesIsolatedBySteamAccount() {
        val session = SteamFamilyViewSession(ttlMillis = 60_000L)
        session.capture("76561198000000000", "steamparental=first", nowMillis = 1_000L)
        session.capture("76561198000000001", "steamparental=second", nowMillis = 1_000L)

        assertEquals(
            "steamparental=first",
            session.cookieFor("76561198000000000", nowMillis = 1_001L),
        )
        assertEquals(
            "steamparental=second",
            session.cookieFor("76561198000000001", nowMillis = 1_001L),
        )
    }

    @Test
    fun expiresAndRemovesFamilyViewCookie() {
        val session = SteamFamilyViewSession(ttlMillis = 1_000L)
        session.capture("76561198000000000", "steamparental=temporary", nowMillis = 5_000L)

        assertEquals(
            "steamparental=temporary",
            session.cookieFor("76561198000000000", nowMillis = 5_999L),
        )
        assertNull(session.cookieFor("76561198000000000", nowMillis = 6_000L))
        assertNull(session.cookieFor("76561198000000000", nowMillis = 6_001L))
    }

    @Test
    fun rejectsEmptyControlCharacterAndOversizedCookieValues() {
        val session = SteamFamilyViewSession(ttlMillis = 60_000L)

        assertFalse(session.capture("76561198000000000", "steamparental=", 1_000L))
        assertFalse(session.capture("76561198000000000", "steamparental=bad\nvalue", 1_000L))
        assertFalse(
            session.capture(
                "76561198000000000",
                "steamparental=${"x".repeat(4_097)}",
                1_000L,
            ),
        )
        assertNull(session.cookieFor("76561198000000000", nowMillis = 1_001L))
    }

    @Test
    fun updatesAndClearsCookieForOneAccount() {
        val session = SteamFamilyViewSession(ttlMillis = 60_000L)
        session.capture("76561198000000000", "steamparental=old", nowMillis = 1_000L)
        session.capture("76561198000000000", "STEAMPARENTAL=new", nowMillis = 2_000L)

        assertEquals(
            "steamparental=new",
            session.cookieFor("76561198000000000", nowMillis = 2_001L),
        )
        session.clear("76561198000000000")
        assertNull(session.cookieFor("76561198000000000", nowMillis = 2_002L))
    }

    @Test
    fun capturesCookiesOnlyFromOfficialSteamHttpsOrigins() {
        assertTrue(
            SteamFamilyViewCookieSourcePolicy.isAllowed(
                "https://store.steampowered.com/parental/",
            ),
        )
        assertTrue(
            SteamFamilyViewCookieSourcePolicy.isAllowed(
                "https://steamcommunity.com/parental/",
            ),
        )
        assertFalse(
            SteamFamilyViewCookieSourcePolicy.isAllowed(
                "http://store.steampowered.com/parental/",
            ),
        )
        assertFalse(
            SteamFamilyViewCookieSourcePolicy.isAllowed(
                "https://store.steampowered.com.evil.example/parental/",
            ),
        )
    }
}
