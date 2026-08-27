package takagi.ru.monica.steam.store.gift.data

import java.net.URI
import takagi.ru.monica.steam.store.gift.domain.SteamStoreCheckoutLine
import takagi.ru.monica.steam.web.domain.SteamWebNavigationCommand
import takagi.ru.monica.steam.web.domain.SteamWebPageAutomation

private const val STEAM_CART_URL = "https://store.steampowered.com/cart/"
private const val STEAM_ADD_TO_CART_URL =
    "https://store.steampowered.com/cart/addtocart/"

internal fun steamStoreCheckoutAutomationFactory(
    lines: List<SteamStoreCheckoutLine>
): ((String) -> SteamWebPageAutomation)? {
    if (lines.isEmpty()) return null
    val snapshot = lines.toList()
    return { sessionId -> SteamStoreCheckoutWebAutomation(sessionId, snapshot) }
}

private class SteamStoreCheckoutWebAutomation(
    private val sessionId: String,
    lines: List<SteamStoreCheckoutLine>
) : SteamWebPageAutomation {
    private val pending = ArrayDeque(lines)
    private var cartNavigationIssued = false

    override fun onPageFinished(url: String): SteamWebNavigationCommand? {
        pending.removeFirstOrNull()?.let { line ->
            return SteamWebNavigationCommand.PostUrl(
                url = STEAM_ADD_TO_CART_URL,
                body = SteamStoreGiftCheckoutProtocol.addToCartBody(
                    sessionId = sessionId,
                    line = line
                ).toByteArray(Charsets.UTF_8)
            )
        }
        if (!cartNavigationIssued && !isSteamCartPage(url)) {
            cartNavigationIssued = true
            return SteamWebNavigationCommand.LoadUrl(STEAM_CART_URL)
        }
        return null
    }
}

internal fun isSteamCartPage(url: String?): Boolean = runCatching {
    val uri = URI(url.orEmpty())
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("store.steampowered.com", ignoreCase = true) &&
        uri.path.trimEnd('/') == "/cart"
}.getOrDefault(false)
