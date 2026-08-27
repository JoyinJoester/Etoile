package takagi.ru.monica.steam.library.screenshots.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamGameScreenshotsPageTest {
    @Test
    fun buildsAccountScreenshotPageFilteredToSelectedGame() {
        assertEquals(
            "https://steamcommunity.com/profiles/76561199437517476/screenshots/?appid=730",
            steamGameScreenshotsPage("76561199437517476", 730)?.url
        )
    }

    @Test
    fun rejectsInvalidSteamIdentityOrAppId() {
        assertNull(steamGameScreenshotsPage(null, 730))
        assertNull(steamGameScreenshotsPage("0", 730))
        assertNull(steamGameScreenshotsPage("76561199437517476", 0))
        assertNull(steamGameScreenshotsPage("76561199437517476", -1))
    }
}
