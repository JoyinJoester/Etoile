package takagi.ru.monica.steam.store.filters.data

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.throwSteamStoreHttpFailure
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata

internal class SteamStoreFilterMetadataService(private val client: OkHttpClient) {
    fun fetch(
        countryCode: String?,
        steamLoginSecure: String?,
        language: String
    ): SteamStoreFilterMetadata {
        val request = buildSteamStoreRequest(
            path = "/search/",
            query = mapOf(
                "l" to language,
                "category1" to "998"
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throwSteamStoreHttpFailure(response.code, steamLoginSecure) {
                    "Steam 商店筛选信息请求失败：${response.code}"
                }
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 商店筛选信息返回空数据")
            return SteamStoreFilterMetadataParser.parse(body)
        }
    }
}
