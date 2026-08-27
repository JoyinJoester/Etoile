package takagi.ru.monica.steam.library.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership

class SteamLibraryMultiFilterTest {
    private val games = listOf(
        SteamGame(
            appId = 1,
            name = "Alpha",
            playtimeForeverMinutes = 60,
            playtimeRecentMinutes = 30,
            achievementUnlockedCount = 4,
            achievementTotalCount = 10,
            supportsSteamCloud = true
        ),
        SteamGame(
            appId = 2,
            name = "Bravo",
            playtimeForeverMinutes = 1_500,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 10,
            achievementTotalCount = 10,
            allAchievementsUnlocked = true
        ),
        SteamGame(
            appId = 3,
            name = "Charlie",
            playtimeForeverMinutes = 0,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 0,
            achievementTotalCount = 0,
            ownership = SteamGameOwnership.FAMILY_SHARED,
            supportsSteamCloud = true
        ),
        SteamGame(
            appId = 4,
            name = "Delta",
            playtimeForeverMinutes = 300,
            playtimeRecentMinutes = 10,
            achievementTotalCount = null
        )
    )

    @Test
    fun ownershipAchievementPlaytimeAndCloudCanBeCombined() {
        val result = filterSteamLibraryGames(
            games = games,
            query = "",
            selection = SteamLibraryFilterSelection(
                ownership = SteamLibraryOwnershipFilter.OWNED,
                achievementStatus = SteamLibraryAchievementStatusFilter.INCOMPLETE,
                playtime = SteamLibraryPlaytimeFilter.UNDER_TWO_HOURS,
                requiresSteamCloud = true
            )
        )

        assertEquals(listOf("Alpha"), result.map(SteamGame::name))
    }

    @Test
    fun familySharedScopeCanBeSelectedWithoutChangingOtherDimensions() {
        val result = filterSteamLibraryGames(
            games = games,
            query = "",
            selection = SteamLibraryFilterSelection(
                ownership = SteamLibraryOwnershipFilter.FAMILY_SHARED
            )
        )

        assertEquals(listOf("Charlie"), result.map(SteamGame::name))
    }

    @Test
    fun noAchievementsDoesNotTreatUnknownProgressAsNoAchievements() {
        val result = filterSteamLibraryGames(
            games = games,
            query = "",
            selection = SteamLibraryFilterSelection(
                achievementStatus = SteamLibraryAchievementStatusFilter.NO_ACHIEVEMENTS
            )
        )

        assertEquals(listOf("Charlie"), result.map(SteamGame::name))
    }

    @Test
    fun totalPlaytimeAndNameSortsAreStable() {
        val byTime = filterSteamLibraryGames(
            games = games,
            query = "",
            selection = SteamLibraryFilterSelection(
                sortOrder = SteamLibrarySortOrder.TOTAL_PLAYTIME
            )
        )
        val byNameDescending = filterSteamLibraryGames(
            games = games,
            query = "",
            selection = SteamLibraryFilterSelection(
                sortOrder = SteamLibrarySortOrder.NAME_DESCENDING
            )
        )

        assertEquals(listOf("Bravo", "Delta", "Alpha", "Charlie"), byTime.map(SteamGame::name))
        assertEquals(listOf("Delta", "Charlie", "Bravo", "Alpha"), byNameDescending.map(SteamGame::name))
    }

    @Test
    fun previewCountMatchesFilteredResultsWithoutDependingOnSortOrder() {
        val selection = SteamLibraryFilterSelection(
            ownership = SteamLibraryOwnershipFilter.OWNED,
            playStatus = SteamLibraryPlayStatusFilter.PLAYED,
            sortOrder = SteamLibrarySortOrder.NAME_DESCENDING
        )

        assertEquals(
            filterSteamLibraryGames(games, "", selection).size,
            countSteamLibraryGames(games, "", selection)
        )
    }

    @Test
    fun activeChoiceCountIncludesSortAndFeatureToggle() {
        val selection = SteamLibraryFilterSelection(
            ownership = SteamLibraryOwnershipFilter.OWNED,
            playStatus = SteamLibraryPlayStatusFilter.RECENT,
            sortOrder = SteamLibrarySortOrder.NAME_ASCENDING,
            requiresSteamCloud = true
        )

        assertTrue(selection.hasActiveFilters)
        assertEquals(4, selection.activeChoiceCount)
        assertFalse(SteamLibraryFilterSelection().hasActiveFilters)
    }
}
