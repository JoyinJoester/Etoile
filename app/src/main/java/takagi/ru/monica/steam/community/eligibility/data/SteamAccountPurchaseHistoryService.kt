package takagi.ru.monica.steam.community.eligibility.data

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityTransaction
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest

internal class SteamAccountPurchaseHistoryService(
    client: OkHttpClient = SteamHttpClientProvider.client
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun fetch(
        account: SteamAccount,
        fallbackCurrencyCode: String
    ): List<SteamCommunityTransaction>? {
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: account.accessToken?.takeIf(String::isNotBlank)?.let {
                "${account.steamId}||$it"
            }
            ?: return null
        val request = buildSteamStoreRequest(
            path = "/account/history/",
            query = mapOf("l" to "english"),
            steamLoginSecure = secure
        ).newBuilder()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isRedirect) return@use null
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam 交易记录请求失败：${response.code}")
            }
            SteamAccountPurchaseHistoryParser.parse(
                html = response.body?.string().orEmpty(),
                fallbackCurrencyCode = fallbackCurrencyCode
            )
        }
    }

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
