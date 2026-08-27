package takagi.ru.monica.steam.community.eligibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.eligibility.data.SteamAccountPurchaseHistoryParser
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityProgressSource
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityTransactionEstimate
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityTransactionKind
import takagi.ru.monica.steam.community.eligibility.domain.SteamLimitedAccountSupportProgress
import takagi.ru.monica.steam.community.eligibility.domain.estimateCommunitySpend
import takagi.ru.monica.steam.community.eligibility.domain.resolveCommunitySpendProgress

class SteamCommunityPurchaseHistoryTest {
    @Test
    fun parsesSteamPurchaseHistoryAndBuildsConservativeSpendRange() {
        val transactions = SteamAccountPurchaseHistoryParser.parse(
            html = """
                <html><body>
                <table class="wallet_history_table">
                  <tr class="wallet_table_row">
                    <td class="wht_items">Game A<div class="wth_payment">Visa</div></td>
                    <td class="wht_type">Purchase</td>
                    <td class="wht_total">¥ 30.00</td>
                  </tr>
                  <tr class="wallet_table_row">
                    <td class="wht_items">Game B<div class="wth_payment">Steam Wallet</div></td>
                    <td class="wht_type">Purchase</td>
                    <td class="wht_total">¥ 20.00</td>
                  </tr>
                  <tr class="wallet_table_row">
                    <td class="wht_items">Game with unknown payment source</td>
                    <td class="wht_type">Purchase</td>
                    <td class="wht_total">¥ 10.00</td>
                  </tr>
                  <tr class="wallet_table_row">
                    <td class="wht_items">Steam Wallet</td>
                    <td class="wht_type">Add Funds to your Steam Wallet</td>
                    <td class="wht_wallet_change">+¥ 10.00</td>
                  </tr>
                  <tr class="wallet_table_row">
                    <td class="wht_items">Game A</td>
                    <td class="wht_type">Refund</td>
                    <td class="wht_total">¥ 5.00</td>
                  </tr>
                  <tr class="wallet_table_row">
                    <td class="wht_items">Community Market item</td>
                    <td class="wht_type">Market Transaction</td>
                    <td class="wht_total">¥ 100.00</td>
                  </tr>
                </table>
                </body></html>
            """.trimIndent(),
            fallbackCurrencyCode = "CNY"
        )

        assertEquals(
            listOf(
                SteamCommunityTransactionKind.STORE_PURCHASE,
                SteamCommunityTransactionKind.STORE_PURCHASE,
                SteamCommunityTransactionKind.STORE_PURCHASE,
                SteamCommunityTransactionKind.WALLET_CREDIT,
                SteamCommunityTransactionKind.REFUND,
                SteamCommunityTransactionKind.MARKET
            ),
            transactions?.map { it.kind }
        )
        val estimate = estimateCommunitySpend(
            transactions = transactions.orEmpty(),
            unitsPerCny = mapOf("CNY" to 1.0, "USD" to 0.14)
        )

        assertEquals(490, estimate?.lowerBoundUsdCents)
        assertEquals(910, estimate?.upperBoundUsdCents)
        assertTrue(estimate?.mayHaveReached(500) == true)
    }

    @Test
    fun loginPageDoesNotProducePurchaseHistoryEstimate() {
        assertNull(
            SteamAccountPurchaseHistoryParser.parse(
                "<html><body><form action='/login'>Sign in to Steam</form></body></html>",
                fallbackCurrencyCode = "USD"
            )
        )
    }

    @Test
    fun officialSupportProgressAlwaysWinsOverTransactionEstimate() {
        val resolution = resolveCommunitySpendProgress(
            status = SteamCommunityRestrictionStatus.LIMITED,
            thresholdUsdCents = 500,
            support = SteamLimitedAccountSupportProgress(
                limited = true,
                spentUsdCents = 235,
                thresholdUsdCents = 500,
                remainingUsdCents = 265
            ),
            transactionEstimate = SteamCommunityTransactionEstimate(
                lowerBoundUsdCents = 400,
                upperBoundUsdCents = 600
            )
        )

        assertEquals(SteamCommunityProgressSource.STEAM_SUPPORT, resolution.progressSource)
        assertEquals(235, resolution.spentUsdCents)
        assertEquals(235, resolution.estimatedSpentUpperUsdCents)
        assertEquals(265, resolution.remainingUsdCents)
        assertTrue(resolution.exactProgress)
    }

    @Test
    fun transactionHistoryCreatesClearlyEstimatedMeasuredProgress() {
        val resolution = resolveCommunitySpendProgress(
            status = SteamCommunityRestrictionStatus.LIMITED,
            thresholdUsdCents = 500,
            support = SteamLimitedAccountSupportProgress(
                limited = true,
                spentUsdCents = null,
                thresholdUsdCents = null,
                remainingUsdCents = null
            ),
            transactionEstimate = SteamCommunityTransactionEstimate(
                lowerBoundUsdCents = 300,
                upperBoundUsdCents = 550
            )
        )

        assertEquals(
            SteamCommunityProgressSource.TRANSACTION_HISTORY,
            resolution.progressSource
        )
        assertEquals(300, resolution.spentUsdCents)
        assertEquals(550, resolution.estimatedSpentUpperUsdCents)
        assertEquals(200, resolution.remainingUsdCents)
        assertFalse(resolution.exactProgress)
    }

    @Test
    fun missingOfficialAndTransactionAmountsRemainUnknownInsteadOfFakeZeroSpend() {
        val resolution = resolveCommunitySpendProgress(
            status = SteamCommunityRestrictionStatus.LIMITED,
            thresholdUsdCents = 500,
            support = null,
            transactionEstimate = null
        )

        assertEquals(SteamCommunityProgressSource.NONE, resolution.progressSource)
        assertNull(resolution.spentUsdCents)
        assertNull(resolution.estimatedSpentUpperUsdCents)
        assertEquals(500, resolution.remainingUsdCents)
        assertFalse(resolution.exactProgress)
    }
}
