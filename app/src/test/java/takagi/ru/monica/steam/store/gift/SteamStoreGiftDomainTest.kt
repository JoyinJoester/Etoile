package takagi.ru.monica.steam.store.gift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.domain.SteamCartItem
import takagi.ru.monica.steam.store.domain.steamCartCheckoutLines
import takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftRecipient
import takagi.ru.monica.steam.store.gift.domain.steamStoreAccountIdFromSteamId64

class SteamStoreGiftDomainTest {
    @Test
    fun steamId64ConvertsToTheAccountIdExpectedBySteamCart() {
        assertEquals(0L, steamStoreAccountIdFromSteamId64("76561197960265728"))
        assertEquals(39734271L, steamStoreAccountIdFromSteamId64("76561197999999999"))
        assertNull(steamStoreAccountIdFromSteamId64("not-a-steam-id"))
        assertNull(steamStoreAccountIdFromSteamId64("42"))
    }

    @Test
    fun checkoutLinesRetainPerItemGiftRecipients() {
        val recipient = SteamStoreGiftRecipient(
            steamId = "76561197999999999",
            accountId = 39734271L,
            displayName = "Friend"
        )
        val lines = steamCartCheckoutLines(
            listOf(
                SteamCartItem(appId = 1, packageId = 100, name = "Self"),
                SteamCartItem(
                    appId = 2,
                    packageId = 200,
                    name = "Gift",
                    giftRecipient = recipient
                ),
                SteamCartItem(appId = 3, packageId = null, name = "Unavailable")
            )
        )

        assertEquals(listOf(100, 200), lines.map { it.packageId })
        assertFalse(lines.first().isGift)
        assertTrue(lines.last().isGift)
        assertEquals(39734271L, lines.last().gifteeAccountId)
    }
}
