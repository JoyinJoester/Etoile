package takagi.ru.monica.steam.community.eligibility.data

import java.security.SecureRandom
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.community.eligibility.domain.SteamLimitedAccountSupportProgress
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.store.data.encodeSteamCookieValue

internal class SteamLimitedAccountSupportService(
    client: OkHttpClient = SteamHttpClientProvider.client
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun fetch(account: SteamAccount): SteamLimitedAccountSupportProgress? {
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: account.accessToken?.takeIf(String::isNotBlank)?.let {
                "${account.steamId}||$it"
            }
            ?: return null
        var lastFailure: Throwable? = null
        for (url in SUPPORT_URLS) {
            val progress = runCatching {
                fetchFromUrl(url = url, secure = secure)
            }.onFailure { error ->
                lastFailure = error
            }.getOrNull()
            if (progress != null) return progress
        }
        lastFailure?.let { throw it }
        return null
    }

    private fun fetchFromUrl(
        url: String,
        secure: String
    ): SteamLimitedAccountSupportProgress? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header(
                "Cookie",
                "steamLoginSecure=${encodeSteamCookieValue(secure)}; sessionid=${newSessionId()}; " +
                    "mobileClient=android; mobileClientVersion=777777%203.6.4"
            )
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.isRedirect) return@use null
            SteamLimitedAccountSupportParser.parse(response.body?.string().orEmpty())
        }
    }

    private fun newSessionId(): String = ByteArray(12)
        .also(random::nextBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val SUPPORT_URLS = listOf(
            "https://help.steampowered.com/zh-cn/",
            "https://help.steampowered.com/en/wizard/HelpWithLimitedAccount"
        )
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        val random = SecureRandom()
    }
}
