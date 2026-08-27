package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockStyleTest {
    @Test
    fun storedStyleUsesM3eAsTheBackwardCompatibleDefault() {
        assertEquals(SteamDockStyle.M3E, SteamDockStyle.fromStoredValue(null))
        assertEquals(SteamDockStyle.M3E, SteamDockStyle.fromStoredValue("UNKNOWN"))
        assertEquals(
            SteamDockStyle.LIQUID_GLASS,
            SteamDockStyle.fromStoredValue(SteamDockStyle.LIQUID_GLASS.name)
        )
        assertEquals(
            SteamDockStyle.FIXED,
            SteamDockStyle.fromStoredValue(SteamDockStyle.FIXED.name)
        )
    }

    @Test
    fun liquidGlassDefaultKeepsAllFiveTopLevelDestinations() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER
        )
    }

    @Test
    fun fixedDefaultKeepsAllFiveTopLevelDestinations() {
        assertEquals(
            SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
            SteamDockTab.FIXED_DEFAULT_ORDER
        )
    }
}
