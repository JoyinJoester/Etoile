package takagi.ru.monica.steam.token.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountCapability
import takagi.ru.monica.steam.data.supporting

class SteamAccountCapabilityUiPolicyTest {
    @Test
    fun accountCollectionsCanBeSeparatedByCapability() {
        val loginOnly = account(
            accessToken = "access",
            refreshToken = "refresh",
            steamLoginSecure = "76561198000000000||access"
        )
        val full = account(
            sharedSecret = "shared",
            identitySecret = "identity",
            accessToken = "access",
            refreshToken = "refresh"
        )

        assertEquals(listOf(full), listOf(loginOnly, full).supporting(SteamAccountCapability.AUTHENTICATOR_CODE))
        assertEquals(
            listOf(loginOnly, full),
            listOf(loginOnly, full).supporting(SteamAccountCapability.AUTHENTICATED_SESSION)
        )
        assertEquals(listOf(full), listOf(loginOnly, full).supporting(SteamAccountCapability.MOBILE_CONFIRMATIONS))
        assertEquals(listOf(full), listOf(loginOnly, full).supporting(SteamAccountCapability.LOGIN_APPROVALS))
    }

    @Test
    fun tokenScreenUsesCapabilitySeparatedAccountCollections() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(source.contains("val tokenAccounts = remember(uiState.accounts)"))
        assertTrue(source.contains("uiState.accounts.filter { it.hasAuthenticatorCode }"))
        assertTrue(source.contains("val sessionAccounts = remember(uiState.accounts)"))
        assertTrue(source.contains("uiState.accounts.filter { it.hasAuthenticatedSession }"))
        assertTrue(source.contains("accounts = confirmationAccounts"))
        assertTrue(source.contains("accounts = sessionAccounts"))
        assertTrue(source.contains("steam_token_page_login_only_hidden"))
        assertTrue(viewModelSource.contains("if (!account.canUseConfirmations)"))
        assertTrue(viewModelSource.contains("if (!account.canApproveLogins)"))
    }

    private fun account(
        sharedSecret: String = "",
        identitySecret: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        steamLoginSecure: String? = null
    ) = SteamAccount(
        id = if (sharedSecret.isBlank()) 1L else 2L,
        steamId = "76561198000000000",
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

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
