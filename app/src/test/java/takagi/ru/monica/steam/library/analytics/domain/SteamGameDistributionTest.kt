package takagi.ru.monica.steam.library.analytics.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGamePrice
import takagi.ru.monica.steam.library.withCnyConversion

class SteamGameDistributionTest {
    @Test
    fun playtimeDistributionUsesStableBoundaries() {
        val games = listOf(0, 59, 60, 179, 180, 599, 600, 1_799, 1_800, 5_999, 6_000)
            .mapIndexed { index, minutes -> game(index, minutes) }

        val counts = steamGameDistribution(games, SteamGameDistributionMode.PLAYTIME)
            .associate { it.range to it.gameCount }

        assertEquals(1, counts[SteamGameDistributionRange.UNPLAYED])
        assertEquals(2, counts[SteamGameDistributionRange.ONE_TO_THREE_HOURS])
        assertEquals(1, counts[SteamGameDistributionRange.OVER_HUNDRED_HOURS])
    }

    @Test
    fun priceDistributionKeepsUnavailableGamesVisible() {
        val games = listOf(
            game(1, priceMinor = 0),
            game(2, priceMinor = 2_499),
            game(3, priceMinor = 2_500),
            game(4)
        )

        val counts = steamGameDistribution(games, SteamGameDistributionMode.PRICE)
            .associate { it.range to it.gameCount }

        assertEquals(1, counts[SteamGameDistributionRange.FREE])
        assertEquals(1, counts[SteamGameDistributionRange.PRICE_UNDER_25])
        assertEquals(1, counts[SteamGameDistributionRange.PRICE_25_TO_50])
        assertEquals(1, counts[SteamGameDistributionRange.PRICE_UNKNOWN])
    }

    @Test
    fun bucketsRetainTheirMatchingGamesForDrillDown() {
        val games = listOf(
            game(1, minutes = 0),
            game(2, minutes = 60),
            game(3, minutes = 179),
            game(4, minutes = 600)
        )

        val buckets = steamGameDistribution(games, SteamGameDistributionMode.PLAYTIME)

        assertEquals(
            listOf(2, 3),
            buckets.single { it.range == SteamGameDistributionRange.ONE_TO_THREE_HOURS }
                .games
                .map(SteamGame::appId)
        )
        assertEquals(
            listOf(4),
            buckets.single { it.range == SteamGameDistributionRange.TEN_TO_THIRTY_HOURS }
                .games
                .map(SteamGame::appId)
        )
    }

    @Test
    fun inrPriceIsConvertedToCnyBeforeBucketAssignment() {
        val inrPrice = SteamGamePrice("INR", 299_900, 299_900, true)
            .withCnyConversion(mapOf("INR" to 12.0), 123L)
        val game = game(9).copy(price = inrPrice)

        val bucket = steamGameDistribution(listOf(game), SteamGameDistributionMode.PRICE)
            .single { it.gameCount == 1 }

        assertEquals(SteamGameDistributionRange.PRICE_200_TO_400, bucket.range)
    }

    @Test
    fun unknownCurrencyStaysInUnknownBucket() {
        val game = game(10).copy(price = SteamGamePrice("ZZZ", 2_000, 2_000, true))

        val bucket = steamGameDistribution(listOf(game), SteamGameDistributionMode.PRICE)
            .single { it.gameCount == 1 }

        assertEquals(SteamGameDistributionRange.PRICE_UNKNOWN, bucket.range)
    }

    private fun game(appId: Int, minutes: Int = 0, priceMinor: Long? = null) = SteamGame(
        appId = appId,
        name = "Game $appId",
        playtimeForeverMinutes = minutes,
        playtimeRecentMinutes = 0,
        price = priceMinor?.let { SteamGamePrice("CNY", it, it, true) }
    )
}
