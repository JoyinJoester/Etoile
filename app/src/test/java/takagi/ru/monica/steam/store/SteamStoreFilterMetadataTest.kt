package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.filters.data.SteamStoreFilterMetadataParser
import takagi.ru.monica.steam.store.filters.domain.findTagId

class SteamStoreFilterMetadataTest {
    @Test
    fun parsesLocalizedPriceLanguageAndTagMetadataFromSteamSearchPage() {
        val html = """
            <html><body>
              <div class="tab_filter_control_row" data-param="supportedlang"
                   data-value="schinese" data-loc="简体中文"></div>
              <div class="tab_filter_control_row" data-param="supportedlang"
                   data-value="english" data-loc="英语"></div>
              <div class="tab_filter_control_row" data-param="tags"
                   data-value="19" data-loc="动作"></div>
              <div class="tab_filter_control_row" data-param="tags"
                   data-value="492" data-loc="独立"></div>
              <script>
                rgPriceStopData = [
                  {"price":"free","label":"免费"},
                  {"price":50,"label":"低于 ¥ 50"},
                  {"price":null,"label":"任意价格"}
                ];
              </script>
            </body></html>
        """.trimIndent()

        val metadata = SteamStoreFilterMetadataParser.parse(html)

        assertEquals(listOf("free", "50"), metadata.priceOptions.map { it.value })
        assertEquals(listOf("schinese", "english"), metadata.languages.map { it.value })
        assertEquals(listOf(19, 492), metadata.tags.map { it.id })
        assertTrue(metadata.tags.any { it.label == "动作" })
        assertEquals(19, metadata.findTagId(" 动作 "))
        assertEquals(null, metadata.findTagId("不存在的标签"))
    }
}
