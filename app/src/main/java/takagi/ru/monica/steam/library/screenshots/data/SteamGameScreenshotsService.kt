package takagi.ru.monica.steam.library.screenshots.data

import java.security.SecureRandom
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsBatch
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsPage
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException

internal class SteamGameScreenshotsService(
    private val api: SteamApiClient = SteamApiClient()
) {
    private val secureRandom = SecureRandom()

    fun fetch(
        account: SteamAccount,
        target: SteamGameScreenshotsPage,
        pageNumber: Int
    ): SteamGameScreenshotsBatch {
        require(account.hasRealSteamId && account.steamId == target.steamId) {
            "matching real Steam account required"
        }
        require(
            !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
        ) { "Steam community session required" }
        require(pageNumber > 0) { "positive screenshot page required" }

        val html = api.communityGetText(
            path = "/profiles/${target.steamId}/screenshots/",
            query = linkedMapOf(
                "appid" to target.appId.toString(),
                "p" to pageNumber.toString(),
                "browsefilter" to "myfiles",
                "sort" to "newestfirst",
                "view" to "imagewall",
                "content" to "1",
                "privacy" to "30",
                "l" to "english"
            ),
            cookies = communityCookies(account),
            referer = target.url
        )
        if (SteamGameScreenshotsParser.isAuthenticationPage(html)) {
            throw SteamApiException(
                message = "Steam community session expired",
                httpStatusCode = 401
            )
        }
        return SteamGameScreenshotsParser.parse(html, target.appId)
    }

    private fun communityCookies(account: SteamAccount): Map<String, String> {
        val loginSecure = account.steamLoginSecure
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "${account.steamId}||${account.accessToken.orEmpty()}"
        val bytes = ByteArray(12).also(secureRandom::nextBytes)
        val sessionId = bytes.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return mapOf(
            "steamLoginSecure" to loginSecure,
            "sessionid" to sessionId,
            "mobileClient" to "android",
            "mobileClientVersion" to "777777 3.6.4"
        )
    }
}
