package takagi.ru.monica.steam.data

/**
 * Capabilities are derived from the credentials that are actually available.
 * This keeps existing maFile/MDBX records compatible and avoids persisting a
 * second account-type flag that could drift away from the stored secrets.
 */
enum class SteamAccountCapability {
    AUTHENTICATOR_CODE,
    AUTHENTICATED_SESSION,
    MOBILE_CONFIRMATIONS,
    LOGIN_APPROVALS
}

val SteamAccount.hasAuthenticatorCode: Boolean
    get() = sharedSecret.isNotBlank()

val SteamAccount.hasAuthenticatedSession: Boolean
    get() = hasRealSteamId && (
        !accessToken.isNullOrBlank() ||
            !refreshToken.isNullOrBlank() ||
            !steamLoginSecure.isNullOrBlank()
        )

val SteamAccount.isLoginOnlyAccount: Boolean
    get() = hasAuthenticatedSession &&
        !hasAuthenticatorCode &&
        identitySecret.isNullOrBlank()

fun SteamAccount.supports(capability: SteamAccountCapability): Boolean = when (capability) {
    SteamAccountCapability.AUTHENTICATOR_CODE -> hasAuthenticatorCode
    SteamAccountCapability.AUTHENTICATED_SESSION -> hasAuthenticatedSession
    SteamAccountCapability.MOBILE_CONFIRMATIONS -> canUseConfirmations
    SteamAccountCapability.LOGIN_APPROVALS -> canApproveLogins
}

fun Iterable<SteamAccount>.supporting(
    capability: SteamAccountCapability
): List<SteamAccount> = filter { account -> account.supports(capability) }
