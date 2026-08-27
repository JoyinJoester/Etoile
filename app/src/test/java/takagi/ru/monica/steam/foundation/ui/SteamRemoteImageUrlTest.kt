package takagi.ru.monica.steam.foundation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamRemoteImageUrlTest {
    @Test
    fun economyAssetsUseTheOriginalSteamCdnWithoutChangingPublicModelUrls() {
        assertEquals(
            "https://community.cloudflare.steamstatic.com/economy/emoticonlarge/steamthumbsup",
            normalizeSteamImageUrl(
                "https://steamcommunity.com/economy/emoticonlarge/steamthumbsup"
            )
        )
        assertEquals(
            "https://community.cloudflare.steamstatic.com/economy/sticker/steamhappy",
            normalizeSteamImageUrl("/economy/sticker/steamhappy")
        )
    }

    @Test
    fun unrelatedSteamImagesKeepTheirHost() {
        val url = "https://steamcommunity.com/profiles/1/avatar.jpg"
        assertEquals(url, normalizeSteamImageUrl(url))
    }
}
