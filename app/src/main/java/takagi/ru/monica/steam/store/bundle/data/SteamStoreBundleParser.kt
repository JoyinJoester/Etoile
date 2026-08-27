package takagi.ru.monica.steam.store.bundle.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundleItem

internal object SteamStoreBundleParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): List<SteamStoreBundle> = Jsoup.parse(html, STEAM_STORE_BASE)
        .select(".game_area_purchase_game_wrapper[data-ds-bundleid], " +
            ".game_area_purchase_game.bundle[data-ds-bundleid]")
        .mapNotNull(::parseBundle)
        .distinctBy(SteamStoreBundle::bundleId)

    private fun parseBundle(element: Element): SteamStoreBundle? {
        val bundleId = element.attr("data-ds-bundleid").toIntOrNull()?.takeIf { it > 0 }
            ?: return null
        val data = runCatching {
            json.parseToJsonElement(element.attr("data-ds-bundle-data")) as? JsonObject
        }.getOrNull() ?: return null
        if (data.bool("m_bIsCommercial") == true) return null

        val namesById = element.select("a[href*=/app/]").mapNotNull { link ->
            val appId = APP_ID.find(link.absUrl("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null
            appId to link.text().trim()
        }.toMap()
        val appIds = data.array("m_rgItems")
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .flatMap { item -> item.array("m_rgIncludedAppIDs").asSequence() }
            .mapNotNull { it.jsonPrimitive.intOrNull?.takeIf { id -> id > 0 } }
            .distinct()
            .toList()
        if (appIds.isEmpty()) return null

        return SteamStoreBundle(
            bundleId = bundleId,
            title = bundleTitle(element),
            storeUrl = element.selectFirst("a[href*=/bundle/$bundleId]")
                ?.absUrl("href")?.substringBefore('?')
                .orEmpty()
                .ifBlank { "$STEAM_STORE_BASE/bundle/$bundleId/" },
            finalPriceCents = element.selectFirst(".discount_block[data-price-final]")
                ?.attr("data-price-final")?.toIntOrNull(),
            discountPercent = data.int("m_nDiscountPct")?.coerceIn(0, 100) ?: 0,
            items = appIds.map { appId ->
                SteamStoreBundleItem(appId = appId, name = namesById[appId].orEmpty())
            }
        )
    }

    private fun bundleTitle(element: Element): String {
        val title = element.selectFirst("h2.title")?.clone()?.also {
            it.select(".bundle_label").remove()
        }?.text().orEmpty().trim()
        return title
            .removePrefix("Buy ")
            .removePrefix("购买 ")
            .removePrefix("購買 ")
            .ifBlank { "Steam Bundle" }
    }

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private val APP_ID = Regex("/app/(\\d+)")
    private const val STEAM_STORE_BASE = "https://store.steampowered.com"
}
