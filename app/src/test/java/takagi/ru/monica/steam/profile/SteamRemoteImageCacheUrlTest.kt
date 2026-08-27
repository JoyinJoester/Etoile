package takagi.ru.monica.steam.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamRemoteImageCacheUrlTest {
    @Test
    fun acceptsSteamChatUgcAvatarHosts() {
        assertTrue(
            SteamRemoteImageCache.isAllowedSteamImageUrl(
                "https://steamusercontent-a.akamaihd.net/ugc/123/avatar.png"
            )
        )
        assertTrue(
            SteamRemoteImageCache.isAllowedSteamImageUrl(
                "https://steamcommunity-a.akamaihd.net/ugc/123/avatar.png"
            )
        )
    }

    @Test
    fun rejectsUntrustedAvatarHosts() {
        assertFalse(
            SteamRemoteImageCache.isAllowedSteamImageUrl(
                "https://example.invalid/avatar.png"
            )
        )
    }
}
