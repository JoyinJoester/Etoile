package takagi.ru.monica.steam.store.filters.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterOption
import takagi.ru.monica.steam.store.filters.domain.SteamStoreTagOption

internal object SteamStoreFilterMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): SteamStoreFilterMetadata {
        val document = Jsoup.parse(html)
        return SteamStoreFilterMetadata(
            priceOptions = parsePrices(html),
            languages = document
                .select(".tab_filter_control_row[data-param=supportedlang][data-value]")
                .mapNotNull { row ->
                    val value = row.attr("data-value").trim().lowercase()
                    val label = localizedLabel(row.attr("data-loc"), row.text())
                    if (value.isBlank() || label.isBlank()) null
                    else SteamStoreFilterOption(value, label)
                }
                .distinctBy(SteamStoreFilterOption::value),
            tags = document
                .select(".tab_filter_control_row[data-param=tags][data-value]")
                .mapNotNull { row ->
                    val id = row.attr("data-value").toIntOrNull()?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val label = localizedLabel(row.attr("data-loc"), row.text())
                    label.takeIf(String::isNotBlank)?.let { SteamStoreTagOption(id, it) }
                }
                .distinctBy(SteamStoreTagOption::id)
        )
    }

    private fun parsePrices(html: String): List<SteamStoreFilterOption> {
        val payload = PRICE_STOP_BLOCK.find(html)?.groupValues?.getOrNull(1)
            ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(payload).jsonArray.mapNotNull { element ->
                val item = element.jsonObject
                val value = item["price"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it == "free" || it.toIntOrNull()?.let { price -> price > 0 } == true }
                    ?: return@mapNotNull null
                val label = item["label"]?.jsonPrimitive?.contentOrNull
                    ?.let(::localizedLabel)
                    .orEmpty()
                if (label.isBlank()) null else SteamStoreFilterOption(value, label)
            }.distinctBy(SteamStoreFilterOption::value)
        }.getOrDefault(emptyList())
    }

    private fun localizedLabel(primary: String, fallback: String = ""): String = Jsoup.parse(
        primary.ifBlank { fallback }
    ).text().trim()

    private val PRICE_STOP_BLOCK = Regex(
        "rgPriceStopData\\s*=\\s*(\\[[\\s\\S]*?])\\s*;",
        RegexOption.IGNORE_CASE
    )
}
