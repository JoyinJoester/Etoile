package takagi.ru.monica.github.data

import takagi.ru.monica.github.domain.GithubDeviceFlowProtocolException

/** Missing expiry denotes a non-expiring OAuth token, never an already expired token. */
internal fun githubTokenExpiry(now: Long, seconds: Long?): Long? {
    if (seconds == null) return null
    if (now < 0 || seconds <= 0 || seconds > (Long.MAX_VALUE - now) / 1000) {
        throw GithubDeviceFlowProtocolException("invalid_token_expiry")
    }
    return now + seconds * 1000
}

internal fun validateGithubRefreshToken(token: String?): String? {
    if (token != null && (token.length !in 20..255 || token.any { it.isWhitespace() || it.isISOControl() })) {
        throw GithubDeviceFlowProtocolException("invalid_refresh_token")
    }
    return token
}
