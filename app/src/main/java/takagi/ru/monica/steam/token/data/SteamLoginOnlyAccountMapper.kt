package takagi.ru.monica.steam.token.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFilePayload

internal fun SteamLoginImportService.LoginResult.ReadyForImport.toLoginOnlyAccountPayload(
    displayNameOverride: String? = null,
    existingAccount: SteamAccount? = null
): SteamMaFilePayload {
    require(payload.sessionOnly) { "Steam login result is not session-only" }
    require(existingAccount == null || existingAccount.steamId == steamId) {
        "Existing Steam account does not match the login result"
    }

    val loginAccountName = payload.accountName
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != steamId }
    val accountName = loginAccountName
        ?: existingAccount?.accountName?.takeIf { it.isNotBlank() }
        ?: steamId
    val displayName = displayNameOverride
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: existingAccount?.displayName?.takeIf { it.isNotBlank() }
        ?: accountName
    val keepAuthenticatorPayload = existingAccount?.sharedSecret?.isNotBlank() == true

    return SteamMaFilePayload(
        steamId = steamId,
        accountName = accountName,
        displayName = displayName,
        deviceId = existingAccount?.deviceId?.takeIf { it.isNotBlank() }
            ?: payload.deviceId,
        sharedSecret = existingAccount?.sharedSecret.orEmpty(),
        identitySecret = existingAccount?.identitySecret,
        revocationCode = existingAccount?.revocationCode,
        tokenGid = existingAccount?.tokenGid,
        accessToken = accessToken,
        refreshToken = refreshToken,
        steamLoginSecure = "$steamId||$accessToken",
        rawJson = if (keepAuthenticatorPayload) {
            existingAccount.rawSteamGuardJson
        } else {
            payload.steamGuardJson
        }
    )
}

internal fun SteamAccount.withLoginOnlyAccountPayload(
    payload: SteamMaFilePayload,
    updatedAt: Long = System.currentTimeMillis()
): SteamAccount {
    require(steamId == payload.steamId) { "Steam account does not match the login payload" }
    return copy(
        accountName = payload.accountName,
        displayName = payload.displayName,
        deviceId = payload.deviceId,
        sharedSecret = payload.sharedSecret,
        identitySecret = payload.identitySecret,
        revocationCode = payload.revocationCode,
        tokenGid = payload.tokenGid,
        accessToken = payload.accessToken,
        refreshToken = payload.refreshToken,
        steamLoginSecure = payload.steamLoginSecure,
        rawSteamGuardJson = payload.rawJson,
        updatedAt = updatedAt
    )
}
