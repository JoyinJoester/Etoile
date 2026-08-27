package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.ui.gallery.SteamScreenshotDownloadPolicy

class SteamScreenshotDownloadPolicyTest {
    @Test
    fun acceptsOnlyHttpsSteamImageHosts() {
        assertTrue(
            SteamScreenshotDownloadPolicy.isAllowedUrl(
                "https://cdn.cloudflare.steamstatic.com/steam/apps/1/ss_test.jpg"
            )
        )
        assertFalse(
            SteamScreenshotDownloadPolicy.isAllowedUrl(
                "http://cdn.cloudflare.steamstatic.com/steam/apps/1/ss_test.jpg"
            )
        )
        assertFalse(
            SteamScreenshotDownloadPolicy.isAllowedUrl(
                "https://steamstatic.com.example.org/steam/apps/1/ss_test.jpg"
            )
        )
    }

    @Test
    fun buildsSafeStableGalleryFileNames() {
        assertEquals(
            "神之天平_ASTLIBRA_screenshot_3_1234.png",
            SteamScreenshotDownloadPolicy.buildDisplayName(
                gameName = "神之天平 / ASTLIBRA:*?",
                screenshotIndex = 2,
                mimeType = "image/png; charset=binary",
                timestampMillis = 1234L
            )
        )
        assertEquals("steam_game", SteamScreenshotDownloadPolicy.safeFileStem("..."))
        assertEquals("image/jpeg", SteamScreenshotDownloadPolicy.normalizeMimeType("image/jpg"))
    }
}
