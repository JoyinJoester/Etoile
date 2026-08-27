package takagi.ru.monica.steam.alerts

import takagi.ru.monica.steam.alerts.domain.*
import takagi.ru.monica.steam.store.domain.SteamWishlistItem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAlertPolicyTest {
    @Test
    fun enabledTypesProduceOnlyRequestedAlertKinds() {
        val settings = SteamAlertSettings(
            enabled = true,
            notificationsEnabled = true,
            confirmationsEnabled = true,
            sessionEnabled = false,
            devicesEnabled = true,
            wishlistDiscountsEnabled = true,
            lastDeviceCount = 2
        )
        val decision = SteamAlertPolicy.evaluate(
            settings,
            SteamAlertObservation(
                unreadNotifications = 2,
                pendingConfirmations = 1,
                sessionIssues = 3,
                authorizedDeviceCount = 3,
                wishlistDiscountNames = listOf("Game")
            )
        )

        assertEquals(
            setOf(
                SteamAlertKind.NOTIFICATIONS,
                SteamAlertKind.CONFIRMATIONS,
                SteamAlertKind.DEVICES,
                SteamAlertKind.WISHLIST_DISCOUNTS
            ),
            decision.kinds
        )
        assertFalse(SteamAlertKind.SESSION in decision.kinds)
    }

    @Test
    fun deviceBaselineDoesNotAlertOnFirstSuccessfulCheck() {
        val decision = SteamAlertPolicy.evaluate(
            SteamAlertSettings(enabled = true, lastDeviceCount = null),
            SteamAlertObservation(authorizedDeviceCount = 4)
        )

        assertFalse(SteamAlertKind.DEVICES in decision.kinds)
        assertEquals(4, decision.deviceBaseline)
    }

    @Test
    fun identicalAlertIsSuppressedForTwentyFourHours() {
        val decision = SteamAlertDecision(setOf(SteamAlertKind.SESSION), null)
        val settings = SteamAlertSettings(
            enabled = true,
            lastAlertSignature = decision.signature,
            lastNotificationAt = 1_000L
        )

        assertFalse(SteamAlertPolicy.shouldNotify(settings, decision, 2_000L))
        assertTrue(
            SteamAlertPolicy.shouldNotify(
                settings,
                decision,
                1_000L + SteamAlertPolicy.REPEAT_SUPPRESSION_MS
            )
        )
    }

    @Test
    fun intervalIsRestrictedToBatterySafeChoices() {
        assertEquals(12, SteamAlertSettings(intervalHours = 1).normalizedIntervalHours)
        assertEquals(6, SteamAlertSettings(intervalHours = 6).normalizedIntervalHours)
        assertEquals(setOf(6, 12, 24), SteamAlertSettings.allowedIntervals)
    }

    @Test
    fun wishlistDiscountsRequireABaselineAndADeeperDiscount() {
        val current = listOf(wishlistItem(10, 20), wishlistItem(20, 50))

        assertTrue(SteamWishlistDiscountPolicy.newlyDiscounted(null, current).isEmpty())
        assertEquals(
            listOf(20),
            SteamWishlistDiscountPolicy.newlyDiscounted(
                previous = listOf(wishlistItem(10, 20), wishlistItem(20, 0)),
                current = current
            ).map { it.appId }
        )
    }

    private fun wishlistItem(appId: Int, discount: Int) = SteamWishlistItem(
        appId = appId,
        name = "Game $appId",
        discountPercent = discount
    )
}
