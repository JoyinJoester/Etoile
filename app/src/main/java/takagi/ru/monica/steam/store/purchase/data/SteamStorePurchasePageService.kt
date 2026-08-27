package takagi.ru.monica.steam.store.purchase.data

import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.store.bundle.data.SteamStoreBundleParser
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.SteamStoreFamilyViewException
import takagi.ru.monica.steam.store.related.data.SteamStoreRelatedContentService

internal data class SteamStorePurchasePage(
    val visiblePackageIds: Set<Int> = emptySet(),
    val tags: List<String> = emptyList(),
    val bundles: List<SteamStoreBundle> = emptyList()
)

internal object SteamStorePurchasePageParser {
    fun parse(html: String): SteamStorePurchasePage {
        val document = Jsoup.parse(html)
        val visiblePackageIds = document
            .select(
                ".game_area_purchase_game_wrapper:not([data-ds-bundleid]) " +
                    "input[name=subid]"
            )
            .mapNotNull { it.attr("value").toIntOrNull()?.takeIf { id -> id > 0 } }
            .toSet()
        return SteamStorePurchasePage(
            visiblePackageIds = visiblePackageIds,
            tags = document
                .select(".glance_tags.popular_tags a.app_tag, .popular_tags a.app_tag")
                .map { it.text().trim() }
                .filter(String::isNotBlank)
                .distinct(),
            bundles = SteamStoreBundleParser.parse(html)
        )
    }
}

internal class SteamStorePurchasePageService(
    private val client: OkHttpClient,
    private val relatedContentService: SteamStoreRelatedContentService
) {
    fun fetch(
        appId: Int,
        countryCode: String?,
        language: String,
        steamLoginSecure: String?,
        accessToken: String?
    ): SteamStorePurchasePage = runCatching {
        val request = buildSteamStoreRequest(
            path = "/app/$appId/",
            query = mapOf("l" to language),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        ).newBuilder().header("Accept", "text/html").build()
        val page = client.newCall(request).execute().use { response ->
            if (response.code == 403 && steamLoginSecure?.isNotBlank() == true) {
                throw SteamStoreFamilyViewException()
            }
            if (!response.isSuccessful) return@use SteamStorePurchasePage()
            SteamStorePurchasePageParser.parse(response.body?.string().orEmpty())
        }
        val metadata = relatedContentService.fetch(
            appIds = page.bundles.flatMap { bundle -> bundle.items.map { it.appId } },
            countryCode = countryCode.orEmpty(),
            language = language,
            accessToken = accessToken
        ).associateBy { it.appId }
        page.copy(
            bundles = page.bundles.map { bundle ->
                val items = bundle.items.map { item ->
                    val app = metadata[item.appId]
                    item.copy(
                        name = app?.name?.takeIf(String::isNotBlank) ?: item.name,
                        imageUrl = app?.headerImageUrl.orEmpty()
                    )
                }
                bundle.copy(
                    imageUrl = items.firstNotNullOfOrNull {
                        it.imageUrl.takeIf(String::isNotBlank)
                    }.orEmpty(),
                    items = items
                )
            }
        )
    }.onFailure { error ->
        SteamDiagLogger.append(
            "store_purchase_page fetch_failed app_id=$appId type=${error.javaClass.simpleName}"
        )
    }.getOrElse { error ->
        if (error is SteamStoreFamilyViewException) throw error
        SteamStorePurchasePage()
    }
}
