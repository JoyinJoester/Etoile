package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchasePageParser

class SteamStorePurchasePageParserTest {
    @Test
    fun officialPagePackageIdsExcludeHiddenCommercialLicense() {
        val html = """
            <div class="glance_tags popular_tags">
              <a class="app_tag">动作</a>
              <a class="app_tag">角色扮演</a>
              <a class="app_tag">动作</a>
            </div>
            <div class="game_area_purchase_game_wrapper">
              <form><input name="subid" value="1053"></form>
            </div>
            <div class="game_area_purchase_game_wrapper" data-ds-bundleid="233"
                 data-ds-bundle-data='{"m_bIsCommercial":false,"m_rgItems":[{"m_rgIncludedAppIDs":[500]}]}'>
              <h2 class="title">Left 4 Dead Bundle</h2><a href="/app/500/">Left 4 Dead</a>
            </div>
        """.trimIndent()

        val page = SteamStorePurchasePageParser.parse(html)

        assertEquals(setOf(1053), page.visiblePackageIds)
        assertEquals(listOf("动作", "角色扮演"), page.tags)
        assertEquals(listOf(233), page.bundles.map { it.bundleId })
    }
}
