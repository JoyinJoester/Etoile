package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogParser
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.domain.steamStoreCurrencyForCountry

class SteamStoreCurrencyTest {
    @Test
    fun indiaUsesInrAndItsSymbol() {
        assertEquals("INR", steamStoreCurrencyForCountry("IN"))
        assertEquals("₹2999.00", formatSteamPrice(299_900, "INR"))
    }

    @Test
    fun catalogFallsBackToRequestedRegionCurrency() {
        val page = SteamStoreCatalogParser.parse(
            payload = """{"results_html":"<a class='search_result_row' data-ds-appid='1091500'><span class='title'>Cyberpunk 2077</span><div class='discount_final_price'>2999.00</div></a>","start":0,"total_count":1}""",
            filter = takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter.SPECIALS,
            countryCode = "IN"
        )

        assertEquals("INR", page.items.single().currency)
        assertTrue(page.items.single().formattedFinalPrice.startsWith("₹"))
    }
}
