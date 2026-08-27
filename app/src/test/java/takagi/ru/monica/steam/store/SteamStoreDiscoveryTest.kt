package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreParser
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogParser
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem
import takagi.ru.monica.steam.store.domain.visibleStoreCollections

class SteamStoreDiscoveryTest {
    @Test
    fun parsesCurrentSaleEventsFromOfficialHomepageMarkup() {
        val html = """
            <div class="home_page_takeunder" style="background: url('desktop.jpg')">
              <a href="https://store.steampowered.com/sale/SimFest2026?snr=1_4"
                 aria-label="模拟游戏节，由独立工作室主办"></a>
            </div>
            <div class="home_area_spotlight" data-ds-appid="2922620">
              <a href="https://store.steampowered.com/sale/SimFest2026?snr=1_4">
                <img data-image-url="https://cdn.example/event.jpg" alt="模拟游戏节">
                <div class="home_capsule_banner">周末特惠</div>
              </a>
            </div>
        """.trimIndent()

        val event = SteamStoreParser.parseDiscoveryEvents(html).single()

        assertEquals("模拟游戏节，由独立工作室主办", event.title)
        assertEquals("周末特惠", event.badge)
        assertEquals("https://cdn.example/event.jpg", event.imageUrl)
        assertEquals("https://store.steampowered.com/sale/SimFest2026", event.url)
    }

    @Test
    fun browseFiltersExposeOnlyRelevantCollections() {
        val game = SteamStoreItem(10, "Game", finalPriceCents = 0)
        val home = SteamStoreHome(
            specials = listOf(game.copy(finalPriceCents = 990)),
            topSellers = listOf(game),
            newReleases = listOf(game),
            comingSoon = listOf(game)
        )

        assertEquals(4, visibleStoreCollections(home, SteamStoreBrowseFilter.ALL).size)
        assertEquals(listOf("specials"), visibleStoreCollections(home, SteamStoreBrowseFilter.SPECIALS).map { it.id })
        assertTrue(visibleStoreCollections(home, SteamStoreBrowseFilter.FREE).single().items.all { it.isFree })
    }

    @Test
    fun discoveryParserKeepsEachEventContainerAtomic() {
        val html = """
            <div class="home_area_spotlight">
              <a href="https://store.steampowered.com/sale/First?snr=home" aria-label="第一个活动">
                <img data-image-url="https://cdn.example/first.jpg" alt="错误备用标题">
                <div class="home_capsule_banner">特卖一</div>
              </a>
            </div>
            <div class="home_area_spotlight">
              <a href="https://store.steampowered.com/sale/Second?snr=home" aria-label="第二个活动">
                <img src="https://cdn.example/second.jpg">
                <div class="home_capsule_banner">特卖二</div>
              </a>
            </div>
        """.trimIndent()

        val events = SteamStoreParser.parseDiscoveryEvents(html)

        assertEquals(listOf("第一个活动", "第二个活动"), events.map { it.title })
        assertEquals(listOf("特卖一", "特卖二"), events.map { it.badge })
        assertEquals("https://cdn.example/first.jpg", events[0].imageUrl)
        assertEquals("https://cdn.example/second.jpg", events[1].imageUrl)
    }

    @Test
    fun parsesPagedCatalogRowsFromOfficialSearchFragment() {
        val payload = """
            {
              "success": 1,
              "start": 25,
              "total_count": 51,
              "results_html": "<a class='search_result_row' data-ds-appid='730'><div class='search_capsule'><img src='https://cdn.example/730.jpg'></div><span class='title'>Counter-Strike 2</span><span class='platform_img win'></span><div class='discount_block'><div class='discount_pct'>-50%</div><div class='discount_prices'><div class='discount_original_price'>¥ 40.00</div><div class='discount_final_price'>¥ 20.00</div></div></div></a><a class='search_result_row' data-ds-appid='1172470'><div class='search_capsule'><img src='https://cdn.example/1172470.jpg'></div><span class='title'>Apex Legends</span><span class='platform_img win'></span><div class='discount_final_price free'>免费</div></a>"
            }
        """.trimIndent()

        val page = SteamStoreCatalogParser.parse(payload, SteamStoreBrowseFilter.TOP_SELLERS)

        assertEquals(25, page.start)
        assertEquals(51, page.totalCount)
        assertTrue(page.hasMore)
        assertEquals(listOf(730, 1172470), page.items.map { it.appId })
        assertEquals(4000, page.items[0].initialPriceCents)
        assertEquals(2000, page.items[0].finalPriceCents)
        assertEquals(50, page.items[0].discountPercent)
        assertEquals(0, page.items[1].finalPriceCents)
    }

    @Test
    fun eventAndPointsShopLinksStayInsideTrustedSteamWebSurface() {
        assertTrue(
            SteamWebNavigationPolicy.isAllowed(
                "https://store.steampowered.com/sale/SimFest2026"
            )
        )
        assertTrue(
            SteamWebNavigationPolicy.isAllowed(
                "https://store.steampowered.com/points/shop/"
            )
        )
    }
}
