package takagi.ru.monica.steam.store.gift

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import takagi.ru.monica.steam.store.gift.data.SteamStoreGiftCheckoutProtocol
import takagi.ru.monica.steam.store.gift.domain.SteamStoreCheckoutLine

class SteamStoreGiftCheckoutProtocolTest {
    @Test
    fun personalCartLineOmitsGiftFields() {
        val form = SteamStoreGiftCheckoutProtocol.addToCartBody(
            sessionId = "session token",
            line = SteamStoreCheckoutLine(packageId = 100)
        ).formValues()

        assertEquals("add_to_cart", form["action"])
        assertEquals("session token", form["sessionid"])
        assertEquals("100", form["subid"])
        assertFalse("isgift" in form)
        assertFalse("gifteeaccountid" in form)
    }

    @Test
    fun giftCartLineIncludesTheOfficialRecipientFields() {
        val form = SteamStoreGiftCheckoutProtocol.addToCartBody(
            sessionId = "abc",
            line = SteamStoreCheckoutLine(packageId = 200, gifteeAccountId = 39734271L)
        ).formValues()

        assertEquals("1", form["isgift"])
        assertEquals("39734271", form["gifteeaccountid"])
    }

    private fun String.formValues(): Map<String, String> = split('&').associate { pair ->
        val (key, value) = pair.split('=', limit = 2)
        URLDecoder.decode(key, Charsets.UTF_8.name()) to
            URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
