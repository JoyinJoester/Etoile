package takagi.ru.monica.steam.token.loginchallenge.domain

sealed interface SteamLoginCaptchaResolution {
    object NotRequired : SteamLoginCaptchaResolution
    object MissingGid : SteamLoginCaptchaResolution
    data class Required(val gid: String) : SteamLoginCaptchaResolution
}

object SteamLoginCaptchaPolicy {
    const val CONFIRMATION_TYPE: Int = 1003
    private const val MAX_GID_LENGTH = 256

    fun resolve(
        required: Boolean,
        captchaGid: String?,
        legacyCaptchaGid: String?
    ): SteamLoginCaptchaResolution {
        if (!required) return SteamLoginCaptchaResolution.NotRequired
        val gid = normalizeGid(captchaGid) ?: normalizeGid(legacyCaptchaGid)
        return gid?.let(SteamLoginCaptchaResolution::Required)
            ?: SteamLoginCaptchaResolution.MissingGid
    }

    fun normalizeGid(rawGid: String?): String? {
        val gid = rawGid?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (gid.length > MAX_GID_LENGTH) return null
        if (gid.any { character -> character.isISOControl() || character.isWhitespace() }) return null
        return gid
    }
}
