package takagi.ru.monica.steam.itad

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.ui.formatItadMoney

class ItadStoreUiGuardTest {
    @Test
    fun regionalPriceCardsExpandValidatedHistoryLowWithOfficialAttribution() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/itad/ui/ItadHistoryLowSection.kt"
        ).readText()

        assertFalse(store.contains("item(key = \"itad_history_low_"))
        assertTrue(store.contains("AnimatedVisibility("))
        assertTrue(store.contains("countryCode = price.countryCode"))
        assertTrue(store.contains("expectedCurrency = price.currency"))
        assertTrue(store.contains("currentSteamPriceMinor = price.finalPriceMinor"))
        assertTrue(store.contains("historyCountryCode = detail.accountCountryCode"))
        assertTrue(store.contains("onOpenItadSettings = onOpenSettings"))
        assertTrue(card.contains("R.string.itad_history_low_source"))
        assertTrue(card.contains("current.historicalLow.sourceUrl"))
        assertTrue(card.contains("isthereanydeal.com"))
        assertTrue(card.contains("value = null"))
        assertTrue(card.contains("resolveItadHistoryLowCompatibility("))
        assertTrue(card.contains("R.string.itad_history_low_region_mismatch"))
        assertFalse(card.contains("ItadPriceTrendSection("))
    }

    @Test
    fun moneyFormattingKeepsItadCurrencyAndAmountWithoutConversion() {
        val formatted = formatItadMoney(
            ItadMoney(amount = 9.99, amountInt = 999, currency = "CNY"),
            Locale.US
        )

        assertEquals("CNY 9.99", formatted)
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
