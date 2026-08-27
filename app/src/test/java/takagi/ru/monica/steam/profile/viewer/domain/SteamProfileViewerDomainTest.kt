package takagi.ru.monica.steam.profile.viewer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements

class SteamProfileViewerDomainTest {
    @Test
    fun gameScopesSeparateCommonAndTargetOnlyGames() {
        val snapshot = SteamProfileViewerSnapshot(
            viewerAccountId = 1L,
            viewerSteamId = VIEWER,
            target = SteamProfileSummary(
                steamId = TARGET,
                personaName = "Target",
                personaState = SteamPersonaState.ONLINE
            ),
            targetGames = listOf(
                SteamGame(10, "Common", 100, 0),
                SteamGame(20, "Target only", 200, 0)
            ),
            viewerGames = listOf(
                SteamGame(10, "Common", 300, 0),
                SteamGame(30, "Viewer only", 400, 0)
            ),
            gameDataVisibility = SteamProfileGameDataVisibility.AVAILABLE,
            fetchedAt = 1L
        )

        assertEquals(listOf(10), snapshot.gamesForScope(SteamProfileGameScope.COMMON).map { it.appId })
        assertEquals(listOf(20), snapshot.gamesForScope(SteamProfileGameScope.TARGET_ONLY).map { it.appId })
    }

    @Test
    fun achievementComparisonBuildsAllFourStates() {
        val viewer = achievements(
            achievement("both", true),
            achievement("viewer", true),
            achievement("target", false),
            achievement("neither", false)
        )
        val target = achievements(
            achievement("both", true),
            achievement("viewer", false),
            achievement("target", true),
            achievement("neither", false)
        )

        val comparison = buildSteamAchievementComparison(VIEWER, TARGET, viewer, target, 1L)

        assertEquals(
            listOf(
                SteamAchievementComparisonState.BOTH,
                SteamAchievementComparisonState.VIEWER_ONLY,
                SteamAchievementComparisonState.TARGET_ONLY,
                SteamAchievementComparisonState.NEITHER
            ),
            comparison.achievements.map(SteamAchievementComparisonEntry::state)
        )
        assertEquals(1, comparison.filtered(SteamAchievementComparisonFilter.BOTH).size)
        assertEquals(2, comparison.viewerCompleted)
        assertEquals(2, comparison.targetCompleted)
    }

    private fun achievements(vararg achievements: SteamAchievement) = SteamGameAchievements(
        accountId = 1L,
        appId = 10,
        gameName = "Game",
        achievements = achievements.toList(),
        fetchedAt = 1L
    )

    private fun achievement(name: String, achieved: Boolean) = SteamAchievement(
        apiName = name,
        displayName = name,
        description = "",
        achieved = achieved,
        unlockTimeSeconds = null,
        iconUrl = null,
        lockedIconUrl = null
    )

    private companion object {
        const val VIEWER = "76561198000000001"
        const val TARGET = "76561198000000002"
    }
}
