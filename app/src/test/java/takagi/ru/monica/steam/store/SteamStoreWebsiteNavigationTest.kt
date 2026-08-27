package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.store.domain.normalizeSteamStoreWebsiteUrl

class SteamStoreWebsiteNavigationTest {
    @Test
    fun keepsCompleteHttpAndHttpsUrls() {
        assertEquals(
            "https://www.thinkwithportals.com/",
            normalizeSteamStoreWebsiteUrl("https://www.thinkwithportals.com/")
        )
        assertEquals(
            "http://example.com/game",
            normalizeSteamStoreWebsiteUrl("http://example.com/game")
        )
    }

    @Test
    fun addsHttpsToHostOnlyWebsiteValues() {
        assertEquals(
            "https://example.com/game",
            normalizeSteamStoreWebsiteUrl("example.com/game")
        )
        assertEquals(
            "https://example.com/game",
            normalizeSteamStoreWebsiteUrl("//example.com/game")
        )
    }

    @Test
    fun rejectsBlankUnsafeAndHostlessValues() {
        assertNull(normalizeSteamStoreWebsiteUrl(""))
        assertNull(normalizeSteamStoreWebsiteUrl("javascript:alert(1)"))
        assertNull(normalizeSteamStoreWebsiteUrl("https:///missing-host"))
    }
}
