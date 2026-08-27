package takagi.ru.monica.steam.token.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamLoginOnlyAccountMapperTest {
    @Test
    fun newLoginOnlyAccountKeepsSessionWithoutAuthenticatorSecrets() {
        val result = loginResult(accountName = "joyins")

        val payload = result.toLoginOnlyAccountPayload(displayNameOverride = "Main")

        assertEquals("76561198000000000", payload.steamId)
        assertEquals("joyins", payload.accountName)
        assertEquals("Main", payload.displayName)
        assertEquals("", payload.sharedSecret)
        assertNull(payload.identitySecret)
        assertNull(payload.revocationCode)
        assertNull(payload.tokenGid)
        assertEquals("access", payload.accessToken)
        assertEquals("refresh", payload.refreshToken)
        assertEquals("76561198000000000||access", payload.steamLoginSecure)
    }

    @Test
    fun reloginPreservesExistingAuthenticatorAndMetadata() {
        val existing = account(
            sharedSecret = "shared",
            identitySecret = "identity",
            rawSteamGuardJson = "{\"shared_secret\":\"shared\"}",
            displayName = "Existing remark"
        )

        val payload = loginResult(accountName = "joyins")
            .toLoginOnlyAccountPayload(existingAccount = existing)
        val updated = existing.withLoginOnlyAccountPayload(payload, updatedAt = 20L)

        assertEquals("shared", payload.sharedSecret)
        assertEquals("identity", payload.identitySecret)
        assertEquals(existing.rawSteamGuardJson, payload.rawJson)
        assertEquals("Existing remark", payload.displayName)
        assertEquals("access", updated.accessToken)
        assertEquals("group", updated.groupName)
        assertEquals(listOf("tag"), updated.tags)
        assertTrue(updated.pinned)
        assertEquals(20L, updated.updatedAt)
    }

    private fun loginResult(accountName: String) =
        SteamLoginImportService.LoginResult.ReadyForImport(
            steamId = "76561198000000000",
            payload = SteamLoginImportService.SteamGuardPayload(
                deviceId = "",
                steamGuardJson = "{\"monica_session_only_login\":true}",
                sessionOnly = true,
                accountName = accountName
            ),
            accessToken = "access",
            refreshToken = "refresh"
        )

    private fun account(
        sharedSecret: String = "",
        identitySecret: String? = null,
        rawSteamGuardJson: String = "{}",
        displayName: String = "Account"
    ) = SteamAccount(
        id = 1L,
        steamId = "76561198000000000",
        accountName = "old-name",
        displayName = displayName,
        deviceId = "android:device",
        sharedSecret = sharedSecret,
        identitySecret = identitySecret,
        revocationCode = "R123",
        tokenGid = "gid",
        accessToken = "old-access",
        refreshToken = "old-refresh",
        steamLoginSecure = "76561198000000000||old-access",
        rawSteamGuardJson = rawSteamGuardJson,
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 2L,
        groupName = "group",
        tags = listOf("tag"),
        pinned = true
    )
}
