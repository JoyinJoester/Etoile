package takagi.ru.monica.steam.store.gift.data

import java.net.URLEncoder
import takagi.ru.monica.steam.store.gift.domain.SteamStoreCheckoutLine

internal object SteamStoreGiftCheckoutProtocol {
    fun addToCartBody(sessionId: String, line: SteamStoreCheckoutLine): String = buildList {
        add("action" to "add_to_cart")
        add("sessionid" to sessionId)
        add("subid" to line.packageId.toString())
        line.gifteeAccountId?.let { accountId ->
            add("isgift" to "1")
            add("gifteeaccountid" to accountId.toString())
        }
    }.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
