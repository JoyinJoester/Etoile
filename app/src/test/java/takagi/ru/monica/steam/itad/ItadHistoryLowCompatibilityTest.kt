package takagi.ru.monica.steam.itad

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadHistoricalLow
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowCompatibility
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.domain.resolveItadHistoryLowCompatibility

class ItadHistoryLowCompatibilityTest {
    @Test
    fun acceptsMatchingCurrencyAndLowNotAboveCurrentSteamPrice() {
        assertEquals(
            ItadHistoryLowCompatibility.COMPATIBLE,
            resolveItadHistoryLowCompatibility(
                historicalLow = historyLow("INR", 44_900L),
                expectedCurrency = "INR",
                currentSteamPriceMinor = 56_900L
            )
        )
    }

    @Test
    fun rejectsFallbackCurrencyFromAnotherRegion() {
        assertEquals(
            ItadHistoryLowCompatibility.CURRENCY_MISMATCH,
            resolveItadHistoryLowCompatibility(
                historicalLow = historyLow("USD", 1_499L),
                expectedCurrency = "PKR",
                currentSteamPriceMinor = 2_000L
            )
        )
    }

    @Test
    fun rejectsRecordedLowThatIsHigherThanCurrentSteamPrice() {
        assertEquals(
            ItadHistoryLowCompatibility.CURRENT_STEAM_PRICE_IS_LOWER,
            resolveItadHistoryLowCompatibility(
                historicalLow = historyLow("USD", 1_499L),
                expectedCurrency = "USD",
                currentSteamPriceMinor = 599L
            )
        )
    }

    private fun historyLow(currency: String, priceMinor: Long) = ItadHistoricalLow(
        gameId = "game",
        shopId = 61,
        shopName = "Steam",
        price = ItadMoney(priceMinor / 100.0, priceMinor, currency),
        regular = ItadMoney(priceMinor / 100.0, priceMinor, currency),
        discountPercent = 0,
        timestamp = "2026-01-01T00:00:00Z",
        sourceUrl = "https://isthereanydeal.com/game/test/",
        fetchedAtMillis = 0L
    )
}
