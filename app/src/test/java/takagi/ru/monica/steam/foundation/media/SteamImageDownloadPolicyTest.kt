package takagi.ru.monica.steam.foundation.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamImageDownloadPolicyTest {
    @Test
    fun acceptsOnlySecureOfficialSteamImageHosts() {
        assertTrue(
            SteamImageDownloadPolicy.isAllowedUrl(
                "https://steamusercontent.com/ugc/chat-image.png?token=1"
            )
        )
        assertTrue(
            SteamImageDownloadPolicy.isAllowedUrl(
                "https://cdn.cloudflare.steamstatic.com/steam/apps/1/header.jpg"
            )
        )
        assertTrue(
            SteamImageDownloadPolicy.isAllowedUrl(
                "https://steamuserimages-a.akamaihd.net/ugc/image.jpg"
            )
        )
        assertFalse(
            SteamImageDownloadPolicy.isAllowedUrl(
                "http://steamusercontent.com/ugc/chat-image.png"
            )
        )
        assertFalse(
            SteamImageDownloadPolicy.isAllowedUrl(
                "https://steamusercontent.com.example.org/ugc/chat-image.png"
            )
        )
        assertFalse(
            SteamImageDownloadPolicy.isAllowedUrl(
                "https://example.org/chat-image.png"
            )
        )
    }

    @Test
    fun buildsSanitizedNamesAndNormalizesSupportedImageTypes() {
        assertEquals(
            "steam_chat_photo_1234.png",
            SteamImageDownloadPolicy.buildDisplayName(
                fileStem = "steam chat / photo:*?",
                mimeType = "image/png; charset=binary",
                timestampMillis = 1234L
            )
        )
        assertEquals("image/jpeg", SteamImageDownloadPolicy.normalizeMimeType("image/pjpeg"))
        assertEquals(
            "steam_image",
            SteamImageDownloadPolicy.safeFileStem("...", fallbackStem = "steam_image")
        )
    }
}
