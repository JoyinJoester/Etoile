package takagi.ru.monica.steam.token.loginchallenge.data

import java.net.URLEncoder
import takagi.ru.monica.steam.token.loginchallenge.domain.SteamLoginCaptchaPolicy

object SteamLoginCaptchaUrl {
    private const val BASE_URL = "https://steamcommunity.com/login/rendercaptcha/"

    fun build(gid: String?): String? {
        val normalizedGid = SteamLoginCaptchaPolicy.normalizeGid(gid) ?: return null
        val encodedGid = URLEncoder.encode(normalizedGid, Charsets.UTF_8.name())
        return "$BASE_URL?gid=$encodedGid"
    }
}
