package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogParser
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter

class SteamStoreCatalogParserTest {
    @Test
    fun parsesOfficialSearchResultsFragmentAsPage() {
        val payload = """
            {
              "success": 1,
              "start": 24,
              "total_count": 80,
              "results_html": "<a href='https://store.steampowered.com/app/730/' data-ds-appid='730' class='search_result_row'><div class='search_capsule'><img src='https://cdn.example/730.jpg'></div><span class='title'>Counter-Strike 2</span><span class='platform_img win'></span><div class='discount_pct'>-20%</div><div class='discount_original_price'>¥ 50.00</div><div class='discount_final_price'>¥ 40.00</div></a>"
            }
        """.trimIndent()

        val page = SteamStoreCatalogParser.parse(payload, SteamStoreBrowseFilter.SPECIALS)

        assertEquals(24, page.start)
        assertEquals(80, page.totalCount)
        assertTrue(page.hasMore)
        assertEquals(730, page.items.single().appId)
        assertEquals(5_000, page.items.single().initialPriceCents)
        assertEquals(4_000, page.items.single().finalPriceCents)
        assertEquals("CNY", page.items.single().currency)
        assertTrue(page.items.single().windows)
    }

    @Test
    fun parsesFreeCatalogItemWithoutInventingPrice() {
        val payload = """{"start":0,"total_count":1,"results_html":"<a data-ds-appid='10' class='search_result_row'><span class='title'>Free Game</span><div class='discount_final_price free'>免费</div></a>"}"""
        val item = SteamStoreCatalogParser.parse(payload, SteamStoreBrowseFilter.FREE).items.single()
        assertEquals(0, item.finalPriceCents)
        assertTrue(item.isFree)
    }
}
