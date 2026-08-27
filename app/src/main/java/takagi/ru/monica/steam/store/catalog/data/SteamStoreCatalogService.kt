package takagi.ru.monica.steam.store.catalog.data

import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.throwSteamStoreHttpFailure
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreItem
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection

internal class SteamStoreCatalogService(private val client: OkHttpClient) {
    private val budgetPriceStopsByCountry = ConcurrentHashMap<String, List<Int>>()

    fun page(
        filter: SteamStoreBrowseFilter,
        filters: SteamStoreFilterSelection,
        start: Int,
        count: Int,
        language: String,
        countryCode: String?,
        steamLoginSecure: String?
    ): SteamStoreCatalogPage {
        require(filter != SteamStoreBrowseFilter.ALL || filters.isActive)
        val request = buildSteamStoreRequest(
            path = "/search/results/",
            query = buildSteamStoreCatalogQuery(
                filter = filter,
                filters = filters,
                start = start,
                count = count,
                language = language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throwSteamStoreHttpFailure(response.code, steamLoginSecure) {
                    "Steam 商店目录请求失败：${response.code}"
                }
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 商店目录返回空数据")
            return SteamStoreCatalogParser.parse(body, filter, countryCode)
        }
    }

    fun search(
        queryText: String,
        filters: SteamStoreFilterSelection,
        language: String,
        countryCode: String?,
        steamLoginSecure: String?
    ): List<SteamStoreItem> {
        val request = buildSteamStoreRequest(
            path = "/search/results/",
            query = buildSteamStoreCatalogQuery(
                filter = SteamStoreBrowseFilter.ALL,
                filters = filters,
                start = 0,
                count = FILTERED_SEARCH_RESULT_LIMIT,
                language = language,
                queryText = queryText
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throwSteamStoreHttpFailure(response.code, steamLoginSecure) {
                    "Steam 商店筛选搜索请求失败：${response.code}"
                }
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 商店筛选搜索返回空数据")
            return SteamStoreCatalogParser.parse(body, SteamStoreBrowseFilter.ALL, countryCode).items
        }
    }

    fun budgetSuggestions(
        targetMinor: Int,
        countryCode: String,
        steamLoginSecure: String?,
        language: String,
        wishlistAppIds: Set<Int> = emptySet(),
        limit: Int = 6
    ): List<SteamStoreItem> {
        if (targetMinor <= 0 || countryCode.isBlank() || limit <= 0) return emptyList()
        val upperMinor = maximumCommunityBudgetMinor(targetMinor)
        val maxPriceMajor = selectSteamBudgetPriceCap(
            stopsMajor = runCatching {
                budgetPriceStops(
                    countryCode = countryCode,
                    steamLoginSecure = steamLoginSecure,
                    language = language
                )
            }.getOrDefault(emptyList()),
            targetMinor = targetMinor
        )
        val pages = mutableMapOf<Int, SteamStoreCatalogPage>()
        fun loadPage(start: Int): SteamStoreCatalogPage = pages.getOrPut(start) {
            budgetPage(
                start = start,
                maxPriceMajor = maxPriceMajor,
                countryCode = countryCode,
                steamLoginSecure = steamLoginSecure,
                language = language
            )
        }
        val firstPage = loadPage(0)
        return collectCommunityBudgetSuggestions(
            firstPage = firstPage,
            loadPage = ::loadPage,
            targetMinor = targetMinor,
            upperMinor = upperMinor,
            wishlistAppIds = wishlistAppIds,
            limit = limit
        )
    }

    private fun budgetPriceStops(
        countryCode: String,
        steamLoginSecure: String?,
        language: String
    ): List<Int> {
        val cacheKey = countryCode.trim().uppercase()
        budgetPriceStopsByCountry[cacheKey]?.let { return it }
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
                    "Steam 价格档位请求失败：${response.code}"
                }
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 价格档位返回空数据")
            return parseSteamBudgetPriceStops(body).also { stops ->
                if (stops.isNotEmpty()) budgetPriceStopsByCountry[cacheKey] = stops
            }
        }
    }

    private fun budgetPage(
        start: Int,
        maxPriceMajor: Int?,
        countryCode: String,
        steamLoginSecure: String?,
        language: String
    ): SteamStoreCatalogPage {
        val request = buildSteamStoreRequest(
            path = "/search/results/",
            query = buildBudgetQuery(
                start = start,
                count = BUDGET_PAGE_SIZE,
                maxPriceMajor = maxPriceMajor,
                language = language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throwSteamStoreHttpFailure(response.code, steamLoginSecure) {
                    "Steam 预算目录请求失败：${response.code}"
                }
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 预算目录返回空数据")
            return SteamStoreCatalogParser.parse(body, SteamStoreBrowseFilter.TOP_SELLERS)
        }
    }

    private fun buildBudgetQuery(
        start: Int,
        count: Int,
        maxPriceMajor: Int?,
        language: String
    ): Map<String, String> = buildMap {
        put("query", "")
        put("start", start.coerceAtLeast(0).toString())
        put("count", count.coerceIn(1, BUDGET_PAGE_SIZE).toString())
        put("dynamic_data", "")
        put("force_infinite", "1")
        put("infinite", "1")
        put("l", language)
        put("category1", "998")
        maxPriceMajor?.takeIf { it > 0 }?.let { put("maxprice", it.toString()) }
        put("sort_by", "Price_DESC")
    }

    private fun collectCommunityBudgetSuggestions(
        firstPage: SteamStoreCatalogPage,
        loadPage: (Int) -> SteamStoreCatalogPage,
        targetMinor: Int,
        upperMinor: Int,
        wishlistAppIds: Set<Int>,
        limit: Int
    ): List<SteamStoreItem> {
        val totalCount = firstPage.totalCount.coerceAtLeast(firstPage.items.size)
        val lastPageIndex = if (totalCount <= 0) 0 else (totalCount - 1) / BUDGET_PAGE_SIZE
        val crossingPageIndex = locateFirstBudgetPageAtOrBelow(
            firstPage = firstPage,
            lastPageIndex = lastPageIndex,
            upperMinor = upperMinor,
            loadPage = loadPage
        )
        val items = linkedMapOf<Int, SteamStoreItem>()
        var pageIndex = (crossingPageIndex - 1).coerceAtLeast(0)
        var scannedPages = 0
        while (pageIndex <= lastPageIndex && scannedPages < MAX_BUDGET_SCAN_PAGES) {
            val page = loadPage(pageIndex * BUDGET_PAGE_SIZE)
            page.items.forEach { item -> items.putIfAbsent(item.appId, item) }
            val selected = selectCommunityBudgetSuggestions(
                items = items.values.toList(),
                targetMinor = targetMinor,
                wishlistAppIds = wishlistAppIds,
                limit = limit
            )
            if (selected.size >= limit) return selected
            val pagePrices = page.items.mapNotNull(SteamStoreItem::finalPriceCents)
            if (pagePrices.isNotEmpty() && pagePrices.maxOrNull()!! < targetMinor) break
            if (page.items.isEmpty()) break
            pageIndex += 1
            scannedPages += 1
        }
        return selectCommunityBudgetSuggestions(
            items = items.values.toList(),
            targetMinor = targetMinor,
            wishlistAppIds = wishlistAppIds,
            limit = limit
        )
    }

    private fun locateFirstBudgetPageAtOrBelow(
        firstPage: SteamStoreCatalogPage,
        lastPageIndex: Int,
        upperMinor: Int,
        loadPage: (Int) -> SteamStoreCatalogPage
    ): Int {
        val firstMinimum = firstPage.items.mapNotNull(SteamStoreItem::finalPriceCents).minOrNull()
        if (lastPageIndex <= 0 || firstMinimum == null || firstMinimum <= upperMinor) return 0
        var low = 1
        var high = lastPageIndex
        var result = lastPageIndex
        var probes = 0
        while (low <= high && probes < MAX_BUDGET_BINARY_PROBES) {
            val middle = low + (high - low) / 2
            val page = loadPage(middle * BUDGET_PAGE_SIZE)
            val minimum = page.items.mapNotNull(SteamStoreItem::finalPriceCents).minOrNull()
            if (minimum == null || minimum <= upperMinor) {
                result = middle
                high = middle - 1
            } else {
                low = middle + 1
            }
            probes += 1
        }
        return result
    }

    private companion object {
        const val BUDGET_PAGE_SIZE = 50
        const val FILTERED_SEARCH_RESULT_LIMIT = 50
        const val MAX_BUDGET_BINARY_PROBES = 14
        const val MAX_BUDGET_SCAN_PAGES = 8
    }
}

internal fun buildSteamStoreCatalogQuery(
    filter: SteamStoreBrowseFilter,
    filters: SteamStoreFilterSelection,
    start: Int,
    count: Int,
    language: String,
    queryText: String = ""
): Map<String, String> = buildMap {
    val normalizedQuery = queryText.trim()
    put("query", normalizedQuery)
    if (normalizedQuery.isNotBlank()) put("term", normalizedQuery)
    put("start", start.coerceAtLeast(0).toString())
    put("count", count.coerceIn(1, 50).toString())
    put("dynamic_data", "")
    put("force_infinite", "1")
    put("infinite", "1")
    put("l", language)
    put("category1", "998")
    when (filter) {
        SteamStoreBrowseFilter.SPECIALS -> put("specials", "1")
        SteamStoreBrowseFilter.TOP_SELLERS -> put("filter", "topsellers")
        SteamStoreBrowseFilter.NEW_RELEASES -> put("sort_by", "Released_DESC")
        SteamStoreBrowseFilter.COMING_SOON -> put("filter", "comingsoon")
        SteamStoreBrowseFilter.FREE -> put("maxprice", "free")
        SteamStoreBrowseFilter.ALL -> Unit
    }
    filters.toQueryParameters().forEach { (key, value) ->
        if (filter != SteamStoreBrowseFilter.FREE || key != "maxprice") {
            put(key, value)
        }
    }
}

internal fun parseSteamBudgetPriceStops(html: String): List<Int> {
    val block = STEAM_PRICE_STOP_BLOCK.find(html)?.groupValues?.getOrNull(1) ?: return emptyList()
    return STEAM_PRICE_STOP_VALUE.findAll(block)
        .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()
        .toList()
}

internal fun selectSteamBudgetPriceCap(stopsMajor: List<Int>, targetMinor: Int): Int? {
    if (targetMinor <= 0) return null
    return stopsMajor.asSequence()
        .filter { it > 0 }
        .distinct()
        .sorted()
        .firstOrNull { stop -> stop.toLong() * 100L >= targetMinor.toLong() }
}

internal fun maximumCommunityBudgetMinor(targetMinor: Int): Int =
    ((targetMinor.toLong() * 110L) / 100L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

internal fun selectCommunityBudgetSuggestions(
    items: List<SteamStoreItem>,
    targetMinor: Int,
    wishlistAppIds: Set<Int>,
    limit: Int
): List<SteamStoreItem> {
    if (targetMinor <= 0 || limit <= 0) return emptyList()
    val upperMinor = maximumCommunityBudgetMinor(targetMinor)
    return items.asSequence()
        .filter { item ->
            val price = item.finalPriceCents ?: return@filter false
            price in targetMinor..upperMinor
        }
        .sortedWith(
            compareByDescending<SteamStoreItem> { it.appId in wishlistAppIds }
                .thenBy { (it.finalPriceCents ?: Int.MAX_VALUE) - targetMinor }
                .thenByDescending(SteamStoreItem::discountPercent)
                .thenBy(SteamStoreItem::name)
        )
        .take(limit)
        .toList()
}

private val STEAM_PRICE_STOP_BLOCK = Regex(
    "rgPriceStopData\\s*=\\s*(\\[[\\s\\S]*?])\\s*;",
    RegexOption.IGNORE_CASE
)
private val STEAM_PRICE_STOP_VALUE = Regex(
    "\"price\"\\s*:\\s*(\\d+)",
    RegexOption.IGNORE_CASE
)
