package takagi.ru.monica.steam.community.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityProgressSource
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress

class SteamCommunityUnlockContentTest {
    @Test
    fun limitedAndUnknownAccountsKeepTheSpendEstimateVisible() {
        assertTrue(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.LIMITED)
        )
        assertTrue(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.UNKNOWN)
        )
        assertFalse(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.UNRESTRICTED)
        )
    }

    @Test
    fun spendDisplayModeSeparatesOfficialEstimateAndUnknownAmounts() {
        assertEquals(
            CommunitySpendDisplayMode.OFFICIAL,
            communitySpendDisplayMode(progress(SteamCommunityProgressSource.STEAM_SUPPORT, 235))
        )
        assertEquals(
            CommunitySpendDisplayMode.TRANSACTION_ESTIMATE,
            communitySpendDisplayMode(
                progress(SteamCommunityProgressSource.TRANSACTION_HISTORY, 235)
            )
        )
        assertEquals(
            CommunitySpendDisplayMode.UNKNOWN,
            communitySpendDisplayMode(progress(SteamCommunityProgressSource.NONE, null))
        )
        val legacyOfficial = progress(SteamCommunityProgressSource.NONE, null).copy(
            exactProgress = true,
            remainingUsdCents = 265
        )
        assertEquals(
            CommunitySpendDisplayMode.OFFICIAL,
            communitySpendDisplayMode(legacyOfficial)
        )
    }

    @Test
    fun progressIndicatorIsHiddenWhenSteamAndHistoryReturnedNoMeasuredAmount() {
        assertTrue(
            shouldShowCommunityProgressIndicator(
                progress(SteamCommunityProgressSource.STEAM_SUPPORT, 235)
            )
        )
        assertTrue(
            shouldShowCommunityProgressIndicator(
                progress(SteamCommunityProgressSource.TRANSACTION_HISTORY, 235)
            )
        )
        assertFalse(
            shouldShowCommunityProgressIndicator(
                progress(SteamCommunityProgressSource.NONE, null)
            )
        )
    }

    @Test
    fun officialZeroSpendRemainsARealMeasuredProgressValue() {
        val officialZero = progress(
            source = SteamCommunityProgressSource.STEAM_SUPPORT,
            spentUsdCents = 0
        )

        assertEquals(CommunitySpendDisplayMode.OFFICIAL, communitySpendDisplayMode(officialZero))
        assertTrue(shouldShowCommunityProgressIndicator(officialZero))
        assertEquals(0f, officialZero.progressFraction)
    }

    private fun progress(
        source: SteamCommunityProgressSource,
        spentUsdCents: Int?
    ): SteamCommunityUnlockProgress = SteamCommunityUnlockProgress(
        status = SteamCommunityRestrictionStatus.LIMITED,
        spentUsdCents = spentUsdCents,
        progressSource = source
    )
}
