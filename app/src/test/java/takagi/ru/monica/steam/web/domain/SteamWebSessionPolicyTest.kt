package takagi.ru.monica.steam.web.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWebSessionPolicyTest {
    @Test
    fun accountSessionAcceptsMatchingRawAndEncodedIdentities() {
        val raw = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000000",
            steamLoginSecure = "76561198000000000||token",
            requireAuthenticatedSession = true,
        )
        val encoded = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000000",
            steamLoginSecure = "76561198000000000%7C%7Ctoken%2Fvalue",
            requireAuthenticatedSession = true,
        )

        assertTrue(raw.canLoad)
        assertTrue(raw.installAuthenticatedCookie)
        assertEquals("76561198000000000", encoded.cookieSteamId)
        assertTrue(encoded.canLoad)
    }

    @Test
    fun accountSessionRejectsAnotherAccountsCookie() {
        val decision = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000001",
            steamLoginSecure = "76561198000000000||token",
            requireAuthenticatedSession = false,
        )

        assertFalse(decision.canLoad)
        assertFalse(decision.installAuthenticatedCookie)
        assertEquals(SteamWebSessionProblem.IDENTITY_MISMATCH, decision.problem)
    }

    @Test
    fun missingSessionCanOnlyOpenPublicNonSensitivePages() {
        val publicPage = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000000",
            steamLoginSecure = null,
            requireAuthenticatedSession = false,
        )
        val checkout = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000000",
            steamLoginSecure = null,
            requireAuthenticatedSession = true,
        )

        assertTrue(publicPage.canLoad)
        assertFalse(publicPage.installAuthenticatedCookie)
        assertFalse(checkout.canLoad)
        assertEquals(SteamWebSessionProblem.AUTHENTICATED_SESSION_REQUIRED, checkout.problem)
    }

    @Test
    fun malformedAuthenticatedSessionFailsClosed() {
        val decision = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = "76561198000000000",
            steamLoginSecure = "not-a-steam-session",
            requireAuthenticatedSession = false,
        )

        assertFalse(decision.canLoad)
        assertEquals(SteamWebSessionProblem.INVALID_SESSION, decision.problem)
    }

    @Test
    fun allowsOnlyOfficialSteamHttpsNavigation() {
        assertTrue(SteamWebNavigationPolicy.isAllowed("https://store.steampowered.com/cart/"))
        assertTrue(SteamWebNavigationPolicy.isAllowed("https://checkout.steampowered.com/"))
        assertTrue(SteamWebNavigationPolicy.isAllowed("https://steamcommunity.com/login/home/"))
        assertTrue(SteamWebNavigationPolicy.isAllowed("https://s.team/p/example"))
        assertFalse(SteamWebNavigationPolicy.isAllowed("http://store.steampowered.com/cart/"))
        assertFalse(SteamWebNavigationPolicy.isAllowed("https://store.steampowered.com.evil.example/"))
        assertFalse(SteamWebNavigationPolicy.isAllowed("javascript:alert(1)"))
        assertTrue(SteamWebNavigationPolicy.isSafeExternal("https://example.com"))
        assertTrue(SteamWebNavigationPolicy.isSafeExternal("steam://open/main"))
        assertFalse(SteamWebNavigationPolicy.isSafeExternal("file:///sdcard/secret"))
        assertFalse(SteamWebNavigationPolicy.isSafeExternal("intent://host/#Intent;end"))
    }

    @Test
    fun buildsEncodedSecureLoginCookie() {
        val cookies = SteamWebSessionCookiePolicy.cookies(
            steamLoginSecure = "76561198000000000||token/value+with spaces",
            sessionId = "abcdef0123456789abcdef01"
        )
        val secure = cookies.single { it.startsWith("steamLoginSecure=") }
        assertTrue(secure.contains("%7C%7C"))
        assertTrue(secure.contains("Secure"))
        assertTrue(secure.contains("HttpOnly"))
        assertFalse(secure.contains(" with spaces"))
        assertTrue(cookies.any { it.startsWith("sessionid=abcdef") })
    }

    @Test
    fun writesSessionCookiesToStoreAndCommunityDomains() {
        val writes = SteamWebSessionCookiePolicy.cookieWrites(
            steamLoginSecure = "76561198000000000||token",
            sessionId = "abcdef0123456789abcdef01"
        )

        assertTrue(writes.any { it.url == "https://store.steampowered.com" })
        assertTrue(writes.any { it.url == "https://steamcommunity.com" })
        assertTrue(writes.any {
            it.url == "https://steamcommunity.com" &&
                it.value.contains("Domain=.steamcommunity.com") &&
                it.value.startsWith("steamLoginSecure=")
        })
    }

    @Test
    fun restoresFamilyViewCookieToStoreAndCommunityDomains() {
        val writes = SteamWebSessionCookiePolicy.cookieWrites(
            steamLoginSecure = "76561198000000000||token",
            sessionId = "abcdef0123456789abcdef01",
            steamParentalCookie = "steamparental=family-session",
        )

        val parentalWrites = writes.filter { it.value.startsWith("steamparental=") }
        assertEquals(2, parentalWrites.size)
        assertTrue(parentalWrites.any { it.url == "https://store.steampowered.com" })
        assertTrue(parentalWrites.any { it.url == "https://steamcommunity.com" })
        assertTrue(parentalWrites.all { it.value.contains("Secure") })
    }

    @Test
    fun communityDesktopModeUsesBrowserUaWithoutLegacyMobileCookies() {
        val defaultUserAgent =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP3A; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/138.0.7204.157 Mobile Safari/537.36"

        val userAgent = SteamWebClientPolicy.userAgent(
            mode = SteamWebClientMode.COMMUNITY_DESKTOP,
            defaultUserAgent = defaultUserAgent
        )
        val writes = SteamWebSessionCookiePolicy.cookieWrites(
            steamLoginSecure = "76561198000000000||token",
            sessionId = "abcdef0123456789abcdef01",
            clientMode = SteamWebClientMode.COMMUNITY_DESKTOP
        )

        assertTrue(userAgent.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(userAgent.contains("Chrome/138.0.7204.157"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains(" Mobile "))
        val legacyMobileCookies = writes.filter {
            it.value.startsWith("mobileClient=") ||
                it.value.startsWith("mobileClientVersion=")
        }
        assertEquals(2, legacyMobileCookies.size)
        assertTrue(legacyMobileCookies.all { it.value.contains("Max-Age=0") })
        assertFalse(legacyMobileCookies.any { it.value.contains("=android") })
        assertFalse(legacyMobileCookies.any { it.value.contains("777777") })
        assertTrue(writes.any { it.value.startsWith("steamLoginSecure=") })
    }

    @Test
    fun defaultModeNormalizesAndroidWebViewUaForResponsiveStore() {
        val defaultUserAgent =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP3A; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/138.0.7204.157 Mobile Safari/537.36"

        val userAgent = SteamWebClientPolicy.userAgent(
            mode = SteamWebClientMode.DEFAULT,
            defaultUserAgent = defaultUserAgent
        )

        assertTrue(userAgent.contains("Android 15"))
        assertTrue(userAgent.contains("Mobile"))
        assertTrue(userAgent.contains("Chrome/138.0.7204.157"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains("Version/4.0"))
    }

    @Test
    fun responsiveStoreDisplayUsesViewportWithoutOverviewAndFixedTextZoom() {
        val responsive = SteamWebClientPolicy.displayPolicy(SteamWebClientMode.DEFAULT)
        assertTrue(responsive.useWideViewPort)
        assertFalse(responsive.loadWithOverviewMode)
        assertEquals(100, responsive.textZoomPercent)

        val desktop = SteamWebClientPolicy.displayPolicy(SteamWebClientMode.COMMUNITY_DESKTOP)
        assertTrue(desktop.useWideViewPort)
        assertTrue(desktop.loadWithOverviewMode)
        assertEquals(100, desktop.textZoomPercent)
    }

    @Test
    fun keepsPreviouslyEncodedSteamLoginSecureAtSingleEncodingLevel() {
        val raw = SteamWebSessionCookiePolicy.cookies(
            steamLoginSecure = "76561198000000000%7C%7Ctoken%2Fvalue",
            sessionId = "abcdef0123456789abcdef01"
        ).single { it.startsWith("steamLoginSecure=") }

        assertTrue(raw.contains("%7C%7C"))
        assertTrue(raw.contains("%2F"))
        assertFalse(raw.contains("%257C%257C"))
        assertEquals(1, "%7C%7C".toRegex().findAll(raw).count())
    }
}
