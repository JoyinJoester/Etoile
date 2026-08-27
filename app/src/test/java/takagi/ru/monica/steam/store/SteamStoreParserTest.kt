package takagi.ru.monica.steam.store

import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreParserTest {
    @Test
    fun parsesFeaturedSearchAndDetailPayloads() {
        val featured = SteamStoreParser.parseFeatured(
            """{"specials":{"name":"优惠","items":[{"id":620,"name":"Portal 2","discount_percent":50,"original_price":4200,"final_price":2100,"currency":"CNY","large_capsule_image":"hero.jpg","header_image":"header.jpg"}]},"top_sellers":{"name":"热销商品","items":[]},"new_releases":{"name":"新品","items":[]}}"""
        )
        assertEquals(1, featured.specials.size)
        assertEquals(2100, featured.specials.single().finalPriceCents)
        assertEquals("¥21.00", featured.specials.single().formattedFinalPrice)

        val search = SteamStoreParser.parseSearch(
            """{"total":1,"items":[{"type":"app","name":"Portal 2","id":620,"price":{"currency":"CNY","initial":4200,"final":4200},"tiny_image":"tiny.jpg","platforms":{"windows":true,"mac":false,"linux":true}}]}"""
        )
        assertEquals(620, search.single().appId)
        assertTrue(search.single().linux)

        val detail = SteamStoreParser.parseDetail(
            appId = 620,
            payload = """{"620":{"success":true,"data":{"type":"game","name":"Portal 2","steam_appid":620,"short_description":"Puzzle","header_image":"header.jpg","price_overview":{"currency":"CNY","initial":4200,"final":2100,"discount_percent":50},"package_groups":[{"subs":[{"packageid":1234}]}],"developers":["Valve"],"publishers":["Valve"],"genres":[{"description":"冒险"}],"screenshots":[{"path_full":"shot.jpg"}],"release_date":{"date":"2011 年 4 月 19 日"}}}}"""
        )
        assertEquals("Portal 2", detail?.name)
        assertEquals(listOf("Valve"), detail?.developers)
        assertEquals("shot.jpg", detail?.screenshots?.single())
        assertEquals(1234, detail?.packageId)
    }

    @Test
    fun formatsCommonAccountCurrencies() {
        assertEquals("NT$75.60", formatSteamPrice(7560, "TWD"))
        assertEquals("£12.34", formatSteamPrice(1234, "GBP"))
        assertEquals("₩1234", formatSteamPrice(123400, "KRW"))
        assertEquals("HK$45.67", formatSteamPrice(4567, "HKD"))
    }

    @Test
    fun parsesPcRequirementsAndKeepsLegacyDetailCacheCompatible() {
        val detail = SteamStoreParser.parseDetail(
            appId = 620,
            payload = """{"620":{"success":true,"data":{"type":"game","name":"Portal 2","steam_appid":620,"pc_requirements":{"minimum":"<strong>最低配置:</strong><br><ul><li><strong>操作系统:</strong> Windows 10</li><li><strong>内存:</strong> 8 GB RAM</li></ul>","recommended":"<strong>推荐配置:</strong><br><ul><li><strong>处理器:</strong> Intel Core i7</li><li><strong>内存:</strong> 16 GB RAM</li></ul>"}}}}"""
        )

        assertEquals(
            "最低配置:\n操作系统: Windows 10\n内存: 8 GB RAM",
            detail?.systemRequirements?.minimum
        )
        assertEquals(
            "推荐配置:\n处理器: Intel Core i7\n内存: 16 GB RAM",
            detail?.systemRequirements?.recommended
        )
        assertTrue(detail?.systemRequirements?.hasContent == true)

        val legacy = Json { ignoreUnknownKeys = true }.decodeFromString<SteamStoreDetail>(
            """{"appId":620,"name":"Portal 2"}"""
        )
        assertTrue(!legacy.systemRequirements.hasContent)
        assertTrue(legacy.tags.isEmpty())
    }

    @Test
    fun excludesCommercialLicenseAndUsesDiscountedPackagePrice() {
        val detail = SteamStoreParser.parseDetail(
            appId = 500,
            payload = """{"500":{"success":true,"data":{"type":"game","name":"Left 4 Dead","steam_appid":500,"package_groups":[{"subs":[{"packageid":204526,"option_text":"Left 4 Dead - Commercial License - ₹ 349","price_in_cents_with_discount":34900},{"packageid":1053,"option_text":"Left 4 Dead - ₹ 480","price_in_cents_with_discount":48000}]}]}}}"""
        )

        assertEquals(listOf(1053), detail?.packageOptions?.map { it.packageId })
        assertEquals("Left 4 Dead", detail?.packageOptions?.single()?.title)
        assertEquals(48_000, detail?.packageOptions?.single()?.priceCents)
    }

    @Test
    fun parsesSteamLimitedFreePackageAsDirectlyClaimable() {
        val detail = SteamStoreParser.parseDetail(
            appId = 738520,
            payload = """{"738520":{"success":true,"data":{"type":"game","name":"呼吸边缘","steam_appid":738520,"package_groups":[{"subs":[{"packageid":1759598,"option_text":"Breathedge Limited Free Promotional Package - Aug 2026 - 免费","can_get_free_license":"0","is_free_license":true,"price_in_cents_with_discount":0},{"packageid":216012,"option_text":"Breathedge - ¥ 92.00","is_free_license":false,"price_in_cents_with_discount":9200}]}]}}}"""
        )

        assertEquals(1759598, detail?.freeLicenseOption?.packageId)
        assertTrue(detail?.freeLicenseOption?.isFreeLicense == true)
        assertEquals(listOf(1759598, 216012), detail?.packageOptions?.map { it.packageId })
    }
}
