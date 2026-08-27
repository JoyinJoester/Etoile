package takagi.ru.monica.steam.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAccountCapabilitiesTest {
    @Test
    fun fullAuthenticatorAccountSupportsEveryAccountCapability() {
        val account = account(
            sharedSecret = "shared",
            identitySecret = "identity",
            accessToken = "access",
            refreshToken = "refresh"
        )

        assertTrue(account.hasAuthenticatorCode)
        assertTrue(account.hasAuthenticatedSession)
        assertFalse(account.isLoginOnlyAccount)
        assertTrue(account.supports(SteamAccountCapability.AUTHENTICATOR_CODE))
        assertTrue(account.supports(SteamAccountCapability.AUTHENTICATED_SESSION))
        assertTrue(account.supports(SteamAccountCapability.MOBILE_CONFIRMATIONS))
        assertTrue(account.supports(SteamAccountCapability.LOGIN_APPROVALS))
    }

    @Test
    fun codeOnlyAccountIsVisibleOnlyToAuthenticatorFeatures() {
        val account = account(
            steamId = "monica-local-code-account",
            sharedSecret = "shared"
        )

        assertTrue(account.hasAuthenticatorCode)
        assertFalse(account.hasAuthenticatedSession)
        assertFalse(account.isLoginOnlyAccount)
        assertTrue(account.supports(SteamAccountCapability.AUTHENTICATOR_CODE))
        assertFalse(account.supports(SteamAccountCapability.AUTHENTICATED_SESSION))
        assertFalse(account.supports(SteamAccountCapability.MOBILE_CONFIRMATIONS))
        assertFalse(account.supports(SteamAccountCapability.LOGIN_APPROVALS))
    }

    @Test
    fun loginOnlyAccountSupportsSessionsButNotAuthenticatorOperations() {
        val account = account(
            accessToken = "access",
            refreshToken = "refresh",
            steamLoginSecure = "76561198000000000||access"
        )

        assertFalse(account.hasAuthenticatorCode)
        assertTrue(account.hasAuthenticatedSession)
        assertTrue(account.isLoginOnlyAccount)
        assertFalse(account.supports(SteamAccountCapability.AUTHENTICATOR_CODE))
        assertTrue(account.supports(SteamAccountCapability.AUTHENTICATED_SESSION))
        assertFalse(account.supports(SteamAccountCapability.MOBILE_CONFIRMATIONS))
        assertFalse(account.supports(SteamAccountCapability.LOGIN_APPROVALS))
    }

    @Test
    fun legacyCookieSessionStillCountsAsAuthenticatedSession() {
        val account = account(
            steamLoginSecure = "76561198000000000||legacy-cookie"
        )

        assertTrue(account.hasAuthenticatedSession)
        assertTrue(account.isLoginOnlyAccount)
    }

    private fun account(
        steamId: String = "76561198000000000",
        sharedSecret: String = "",
        identitySecret: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        steamLoginSecure: String? = null
    ) = SteamAccount(
        id = 1L,
        steamId = steamId,
        accountName = "account",
        displayName = "Account",
        deviceId = "",
        sharedSecret = sharedSecret,
        identitySecret = identitySecret,
        revocationCode = null,
        tokenGid = null,
        accessToken = accessToken,
        refreshToken = refreshToken,
        steamLoginSecure = steamLoginSecure,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
