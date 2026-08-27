package takagi.ru.monica.steam.store.purchase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreParser

class SteamStorePurchaseParserTest {
    @Test
    fun appDetailsKeepsEveryPackageDemoDlcAndDiscoveryField() {
        val detail = SteamStoreParser.parseDetail(
            appId = 620,
            payload = """{
              "620":{"success":true,"data":{
                "type":"game","name":"Portal 2","steam_appid":620,
                "price_overview":{"currency":"CNY","initial":4200,"final":2100},
                "package_groups":[{"subs":[
                  {"packageid":10,"option_text":"Portal 2","option_description":"Base game","price_in_cents":2100,"percent_savings_text":"-50%"},
                  {"packageid":20,"option_text":"Portal Bundle","option_description":"Two games","price_in_cents":3000}
                ]}],
                "demos":[{"appid":621,"description":"Portal 2 Demo"}],
                "dlc":[622,623],
                "fullgame":{"appid":400,"name":"Portal"},
                "categories":[{"description":"Single-player"},{"description":"Co-op"}],
                "supported_languages":"<strong>English</strong>, Simplified Chinese",
                "controller_support":"full",
                "website":"https://www.thinkwithportals.com/",
                "recommendations":{"total":123456},
                "achievements":{"total":51}
              }}
            }"""
        )!!

        assertEquals(listOf(10, 20), detail.packageOptions.map { it.packageId })
        assertEquals(10, detail.packageId)
        assertEquals(50, detail.packageOptions.first().discountPercent)
        assertEquals(listOf(621), detail.demos.map { it.appId })
        assertEquals(listOf(622, 623), detail.dlcAppIds)
        assertEquals(400, detail.fullGame?.appId)
        assertEquals(listOf("Single-player", "Co-op"), detail.categories)
        assertTrue(detail.supportedLanguages.contains("English"))
        assertEquals("full", detail.controllerSupport)
        assertEquals(123456, detail.recommendationCount)
        assertEquals(51, detail.achievementCount)
    }
}
