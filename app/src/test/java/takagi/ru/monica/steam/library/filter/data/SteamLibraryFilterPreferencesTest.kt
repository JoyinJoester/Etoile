package takagi.ru.monica.steam.library.filter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryAchievementStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryOwnershipFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlayStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlaytimeFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder

class SteamLibraryFilterPreferencesTest {
    @Test
    fun storedSelectionRoundTripsAllDimensions() {
        val selection = SteamLibraryFilterSelection(
            ownership = SteamLibraryOwnershipFilter.OWNED,
            playStatus = SteamLibraryPlayStatusFilter.RECENT,
            achievementStatus = SteamLibraryAchievementStatusFilter.INCOMPLETE,
            playtime = SteamLibraryPlaytimeFilter.TWO_TO_TWENTY_HOURS,
            sortOrder = SteamLibrarySortOrder.NAME_DESCENDING,
            requiresSteamCloud = true
        )

        assertEquals(selection, decodeSteamLibraryFilterSelection(encodeSteamLibraryFilterSelection(selection)))
    }

    @Test
    fun legacyFamilyAndCloudFiltersAreMigrated() {
        val family = decodeSteamLibraryFilterSelection(
            values = SteamLibraryFilterStoredValues(),
            legacyFilterName = "FAMILY_SHARED"
        )
        val cloud = decodeSteamLibraryFilterSelection(
            values = SteamLibraryFilterStoredValues(),
            legacyFilterName = "STEAM_CLOUD"
        )

        assertEquals(SteamLibraryOwnershipFilter.FAMILY_SHARED, family.ownership)
        assertTrue(cloud.requiresSteamCloud)
    }

    @Test
    fun invalidStoredValuesFallBackToDefaults() {
        val decoded = decodeSteamLibraryFilterSelection(
            SteamLibraryFilterStoredValues(
                ownership = "REMOVED_VALUE",
                playStatus = "REMOVED_VALUE",
                requiresSteamCloud = "invalid"
            )
        )

        assertEquals(SteamLibraryFilterSelection(), decoded)
    }
}
