package takagi.ru.monica.steam.store.catalog.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreItem

internal object SteamStoreCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String, filter: SteamStoreBrowseFilter, countryCode: String? = null): SteamStoreCatalogPage {
        val root = json.parseToJsonElement(payload).jsonObject
        val html = root["results_html"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val items = Jsoup.parseBodyFragment(html, STEAM_STORE_BASE)
            .select("a.search_result_row[data-ds-appid]")
            .mapNotNull { parseItem(it, countryCode) }
            .distinctBy(SteamStoreItem::appId)
        return SteamStoreCatalogPage(
            filter = filter,
            items = items,
            start = root["start"]?.jsonPrimitive?.intOrNull ?: 0,
            totalCount = root["total_count"]?.jsonPrimitive?.intOrNull ?: items.size
        )
    }

    private fun parseItem(row: Element, countryCode: String?): SteamStoreItem? {
        val appId = row.attr("data-ds-appid").substringBefore(',').toIntOrNull() ?: return null
        val imageUrl = row.selectFirst(".search_capsule img")?.attr("abs:src").orEmpty()
        val initialText = row.selectFirst(".discount_original_price")?.text().orEmpty()
        val finalElement = row.selectFirst(".discount_final_price")
        val finalText = finalElement?.text().orEmpty()
        return SteamStoreItem(
            appId = appId,
            name = row.selectFirst(".title")?.text().orEmpty(),
            imageUrl = imageUrl,
            headerImageUrl = imageUrl,
            currency = steamCurrency(initialText.ifBlank { finalText }, countryCode),
            initialPriceCents = steamPriceMinor(initialText),
            finalPriceCents = when {
                finalElement?.hasClass("free") == true || finalText.contains("免费", true) -> 0
                else -> steamPriceMinor(finalText)
            },
            discountPercent = row.selectFirst(".discount_pct")?.text()
                ?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            windows = row.selectFirst(".platform_img.win") != null,
            mac = row.selectFirst(".platform_img.mac") != null,
            linux = row.selectFirst(".platform_img.linux") != null,
            tagIds = TAG_ID.findAll(row.attr("data-ds-tagids"))
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it > 0 }
                .distinct()
                .toList()
        )
    }

    private fun steamCurrency(text: String, countryCode: String?): String = when {
        text.contains("₹") -> "INR"
        text.contains("NT$") -> "TWD"
        text.contains("HK$") -> "HKD"
        text.contains("¥") || text.contains("元") -> "CNY"
        text.contains("€") -> "EUR"
        text.contains("£") -> "GBP"
        text.contains("₩") -> "KRW"
        text.contains("₽") -> "RUB"
        else -> takagi.ru.monica.steam.store.domain.steamStoreCurrencyForCountry(countryCode)
    }

    private fun steamPriceMinor(text: String): Int? {
        val match = Regex("[0-9][0-9,.]*").find(text)?.value ?: return null
        val decimal = when {
            match.contains('.') -> match.replace(",", "")
            match.count { it == ',' } == 1 && match.substringAfter(',').length == 2 ->
                match.replace(',', '.')
            else -> match.replace(",", "")
        }
        return decimal.toBigDecimalOrNull()?.movePointRight(2)?.toInt()
    }

    private const val STEAM_STORE_BASE = "https://store.steampowered.com"
    private val TAG_ID = Regex("\\d+")
}
