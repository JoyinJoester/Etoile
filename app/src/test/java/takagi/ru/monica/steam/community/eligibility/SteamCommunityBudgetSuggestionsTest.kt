package takagi.ru.monica.steam.community.eligibility

import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogService

class SteamCommunityBudgetSuggestionsTest {
    @Test
    fun budgetCatalogUsesAccountRegionAndReturnsGamesThatCoverRemainingSpend() {
        val requestedUrl = AtomicReference<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrl.set(chain.request().url.toString())
                val responseBody = if (chain.request().url.encodedPath == "/search/") {
                    priceStopsPage(20, 40, 60)
                } else {
                    payload()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val games = SteamStoreCatalogService(client).budgetSuggestions(
            targetMinor = 3_600,
            countryCode = "CN",
            steamLoginSecure = "secure",
            language = "schinese",
            wishlistAppIds = setOf(3),
            limit = 6
        )

        assertEquals(listOf(3, 2, 4), games.map { it.appId })
        assertTrue(games.all { (it.finalPriceCents ?: 0) >= 3_600 })
        assertTrue(games.all { (it.finalPriceCents ?: Int.MAX_VALUE) <= 3_960 })
        assertTrue(requestedUrl.get().contains("cc=CN"))
        assertTrue(requestedUrl.get().contains("maxprice=40"))
        assertTrue(requestedUrl.get().contains("sort_by=Price_DESC"))
    }

    @Test
    fun budgetCatalogUsesSteamSupportedPriceStopInsteadOfInvalidCeiling() {
        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrls += request.url.toString()
                val body = when (request.url.encodedPath) {
                    "/search/" -> priceStopsPage(5, 10, 15)
                    else -> if (request.url.queryParameter("maxprice") == "5") {
                        payload(
                            start = 0,
                            totalCount = 1,
                            rows = row(11, "Exact five dollars", "$5.00")
                        )
                    } else {
                        payload(
                            start = 0,
                            totalCount = 1,
                            rows = row(12, "Unfiltered expensive result", "$199.99")
                        )
                    }
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val games = SteamStoreCatalogService(client).budgetSuggestions(
            targetMinor = 500,
            countryCode = "US",
            steamLoginSecure = "secure",
            language = "english"
        )

        assertEquals(listOf(11), games.map { it.appId })
        assertTrue(requestedUrls.any { it.contains("/search/?") })
        assertTrue(requestedUrls.any { it.contains("maxprice=5") })
        assertTrue(requestedUrls.none { it.contains("maxprice=6") })
    }

    @Test
    fun budgetCatalogContinuesUntilPriceSortedResultsReachTargetWindow() {
        val requestedStarts = mutableListOf<Int>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val body = when (request.url.encodedPath) {
                    "/search/" -> priceStopsPage(20, 40, 60)
                    else -> when (val start = request.url.queryParameter("start")?.toIntOrNull() ?: 0) {
                        0 -> {
                            requestedStarts += start
                            payload(
                                start = 0,
                                totalCount = 100,
                                rows = row(21, "Above window", "¥ 40.00")
                            )
                        }
                        else -> {
                            requestedStarts += start
                            payload(
                                start = start,
                                totalCount = 100,
                                rows = row(22, "Inside window", "¥ 39.00")
                            )
                        }
                    }
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val games = SteamStoreCatalogService(client).budgetSuggestions(
            targetMinor = 3_600,
            countryCode = "CN",
            steamLoginSecure = "secure",
            language = "schinese"
        )

        assertEquals(listOf(22), games.map { it.appId })
        assertEquals(listOf(0, 50), requestedStarts.distinct())
    }

    private fun payload(): String {
        val rows = listOf(
            row(1, "Too cheap", "¥ 35.99"),
            row(5, "Over ten percent", "¥ 39.61"),
            row(4, "Ten percent", "¥ 39.60"),
            row(2, "Exact", "¥ 36.00"),
            row(3, "Wishlist", "¥ 39.00")
        ).joinToString("")
        return """{"success":1,"start":0,"total_count":5,"results_html":${json(rows)}}"""
    }

    private fun payload(start: Int, totalCount: Int, rows: String): String =
        """{"success":1,"start":$start,"total_count":$totalCount,"results_html":${json(rows)}}"""

    private fun priceStopsPage(vararg stops: Int): String = buildString {
        append("<script>var rgPriceStopData = [")
        append("{\"price\":\"free\",\"label\":\"Free\"},")
        stops.forEachIndexed { index, stop ->
            if (index > 0) append(',')
            append("{\"price\":$stop,\"label\":\"Under $stop\"}")
        }
        append(",{\"price\":null,\"label\":\"Any Price\"}];</script>")
    }

    private fun row(appId: Int, name: String, price: String): String =
        "<a data-ds-appid='$appId' class='search_result_row'>" +
            "<span class='title'>$name</span>" +
            "<div class='discount_final_price'>$price</div></a>"

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
        append('"')
    }
}
