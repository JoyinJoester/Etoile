package takagi.ru.monica.steam.community.eligibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.eligibility.data.SteamCommunityAccountInfoParser
import takagi.ru.monica.steam.community.eligibility.data.SteamLimitedAccountSupportParser
import takagi.ru.monica.steam.community.eligibility.data.resolveCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockCalculator
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockSource
import takagi.ru.monica.steam.community.eligibility.domain.withSteamLevelEvidence
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamCommunityEligibilityTest {
    @Test
    fun parsesLimitedAccountFlagAndCountryFromClientAccountInfo() {
        val body = SteamProtoWriter().apply {
            writeString(1, "Alyx")
            writeString(2, "CN")
            writeVarint(7, 4096L)
        }.toByteArray()

        val info = SteamCommunityAccountInfoParser.parse(body)

        assertEquals("CN", info.countryCode)
        assertEquals(true, info.limited)
        assertEquals(4096L, info.accountFlags)
    }

    @Test
    fun forcedLimitedFlagIsAlsoTreatedAsLimited() {
        val body = SteamProtoWriter().apply {
            writeString(2, "US")
            writeVarint(7, 8192L)
        }.toByteArray()

        assertEquals(true, SteamCommunityAccountInfoParser.parse(body).limited)
    }

    @Test
    fun missingAccountFlagsDoesNotInventAnUnrestrictedAccount() {
        val body = SteamProtoWriter().apply {
            writeString(1, "New account")
            writeString(2, "CN")
        }.toByteArray()

        val info = SteamCommunityAccountInfoParser.parse(body)

        assertNull(info.limited)
    }

    @Test
    fun accountFlagsWithoutLimitedBitsRemainAvailableForCorroboration() {
        val body = SteamProtoWriter().apply {
            writeString(2, "CN")
            writeVarint(7, 0L)
        }.toByteArray()

        assertEquals(false, SteamCommunityAccountInfoParser.parse(body).limited)
    }

    @Test
    fun parsesExactSpentAndThresholdFromSupportPage() {
        val result = SteamLimitedAccountSupportParser.parse(
            """
            <html><body>
              <h1>Limited User Accounts</h1>
              <p>Your account has spent ${'$'}2.35 USD out of the ${'$'}5.00 USD required.</p>
            </body></html>
            """.trimIndent()
        )

        assertEquals(true, result?.limited)
        assertEquals(235, result?.spentUsdCents)
        assertEquals(500, result?.thresholdUsdCents)
        assertEquals(265, result?.remainingUsdCents)
    }

    @Test
    fun parsesAlternateOfficialSupportProgressWording() {
        val result = SteamLimitedAccountSupportParser.parse(
            """
            <html><body>
              <p>Qualifying spend: US${'$'}1.25.</p>
              <p>You must spend another US${'$'}3.75 to remove this restriction.</p>
            </body></html>
            """.trimIndent()
        )

        assertEquals(true, result?.limited)
        assertEquals(125, result?.spentUsdCents)
        assertEquals(375, result?.remainingUsdCents)
    }

    @Test
    fun parsesOfficialSpendRatioFromLocalizedSupportHomePage() {
        val result = SteamLimitedAccountSupportParser.parse(
            """
            <html><body>
              <section>
                <p>Steam 上的花费额度： ${'$'}0.00 / ${'$'}5.00 USD</p>
              </section>
            </body></html>
            """.trimIndent()
        )

        assertEquals(true, result?.limited)
        assertEquals(0, result?.spentUsdCents)
        assertEquals(500, result?.thresholdUsdCents)
        assertEquals(500, result?.remainingUsdCents)
    }

    @Test
    fun detectsUnrestrictedAccountWithoutInventingSpendValues() {
        val result = SteamLimitedAccountSupportParser.parse(
            "<html><body>Your account is not limited.</body></html>"
        )

        assertEquals(false, result?.limited)
        assertNull(result?.spentUsdCents)
        assertEquals(0, result?.remainingUsdCents)
    }

    @Test
    fun exactZeroSpendWinsOverGenericUnrestrictedHelpCopy() {
        val result = SteamLimitedAccountSupportParser.parse(
            """
            <html><body>
              <p>Your account has spent ${'$'}0.00 USD out of the ${'$'}5.00 USD required.</p>
              <p>If your account is not limited, these restrictions do not apply.</p>
            </body></html>
            """.trimIndent()
        )

        assertEquals(true, result?.limited)
        assertEquals(0, result?.spentUsdCents)
        assertEquals(500, result?.remainingUsdCents)
    }

    @Test
    fun loginPageIsNotTreatedAsEligibilityData() {
        val result = SteamLimitedAccountSupportParser.parse(
            "<html><body><form action='/login'>Sign in to Steam Support</form></body></html>"
        )

        assertNull(result)
    }

    @Test
    fun conditionalUnrestrictedHelpCopyDoesNotUnlockTheAccount() {
        val result = SteamLimitedAccountSupportParser.parse(
            """
            <html><body>
              <p>If your account is not limited, these restrictions do not apply.</p>
              <p>Accounts must spend at least ${'$'}5.00 USD.</p>
            </body></html>
            """.trimIndent()
        )

        assertNull(result)
    }

    @Test
    fun onlyPositiveAccountFlagsCanConfirmARestriction() {
        assertEquals(
            SteamCommunityRestrictionStatus.LIMITED,
            resolveCommunityRestrictionStatus(supportLimited = false, accountFlagsLimited = true)
        )
        assertEquals(
            SteamCommunityRestrictionStatus.LIMITED,
            resolveCommunityRestrictionStatus(supportLimited = null, accountFlagsLimited = true)
        )
        assertEquals(
            SteamCommunityRestrictionStatus.UNKNOWN,
            resolveCommunityRestrictionStatus(supportLimited = null, accountFlagsLimited = false)
        )
        assertEquals(
            SteamCommunityRestrictionStatus.UNKNOWN,
            resolveCommunityRestrictionStatus(supportLimited = false, accountFlagsLimited = null)
        )
        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            resolveCommunityRestrictionStatus(supportLimited = false, accountFlagsLimited = false)
        )
        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            resolveCommunityRestrictionStatus(
                supportLimited = false,
                accountFlagsLimited = null,
                supportProvesThresholdReached = true
            )
        )
    }

    @Test
    fun positiveSteamLevelOnlyUpgradesUnknownEligibility() {
        val unknown = SteamCommunityUnlockProgress(
            status = SteamCommunityRestrictionStatus.UNKNOWN,
            source = SteamCommunityUnlockSource.ESTIMATE
        )
        val limited = SteamCommunityUnlockProgress(
            status = SteamCommunityRestrictionStatus.LIMITED,
            source = SteamCommunityUnlockSource.STEAM_ACCOUNT_FLAGS
        )

        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            unknown.withSteamLevelEvidence(12)?.status
        )
        assertEquals(
            SteamCommunityUnlockSource.STEAM_LEVEL,
            unknown.withSteamLevelEvidence(12)?.source
        )
        assertEquals(
            SteamCommunityRestrictionStatus.LIMITED,
            limited.withSteamLevelEvidence(12)?.status
        )
    }

    @Test
    fun convertsUsdThresholdIntoAccountCurrencyUsingSharedRates() {
        val rates = mapOf(
            "CNY" to 1.0,
            "USD" to 0.14,
            "EUR" to 0.13
        )

        assertEquals(
            3_571L,
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                usdCents = 500,
                currencyCode = "CNY",
                unitsPerCny = rates
            )
        )
        assertEquals(
            464L,
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                usdCents = 500,
                currencyCode = "EUR",
                unitsPerCny = rates
            )
        )
    }

    @Test
    fun unknownRateReturnsNoFakeLocalAmount() {
        assertNull(
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                usdCents = 500,
                currencyCode = "XYZ",
                unitsPerCny = mapOf("CNY" to 1.0, "USD" to 0.14)
            )
        )
    }

    @Test
    fun supportParserDoesNotMarkGenericFaqAsExactProgress() {
        val result = SteamLimitedAccountSupportParser.parse(
            "<html><body>Limited accounts must spend at least ${'$'}5.00 USD.</body></html>"
        )

        assertFalse(result?.hasExactProgress == true)
    }
}
