package takagi.ru.monica.steam.network.optimization.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.ui.components.calculateSteamNetworkOptimizationPullProgress

class SteamNetworkOptimizationPullCardTest {
    @Test
    fun pullProgressClampsAtBothEnds() {
        assertEquals(
            0f,
            calculateSteamNetworkOptimizationPullProgress(-10f, 100f),
            0.0001f
        )
        assertEquals(
            0.5f,
            calculateSteamNetworkOptimizationPullProgress(50f, 100f),
            0.0001f
        )
        assertEquals(
            1f,
            calculateSteamNetworkOptimizationPullProgress(160f, 100f),
            0.0001f
        )
    }

    @Test
    fun pullStateMatchesMonicaPassReleaseNavigationBehavior() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/common/pull/PullToActionState.kt"
        ).readText()

        assertTrue(source.contains("canStartPullState()"))
        assertTrue(source.contains("source == NestedScrollSource.UserInput"))
        assertTrue(source.contains("performPullThreshold()"))
        assertTrue(source.contains("currentOffset >= triggerDistance"))
        assertTrue(source.contains("override suspend fun onPreFling"))
        assertTrue(source.contains("onTriggeredState()"))
        assertTrue(source.contains("Spring.DampingRatioNoBouncy"))
    }

    @Test
    fun settingsPlacesPullCardBeforeSearchAndOpensIndependentScreen() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val navigation = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt"
        ).readText()
        val headerIndex = settings.indexOf("homeHeaderContent?.invoke(")
        val searchIndex = settings.indexOf("SettingsSearchField(")

        assertTrue(headerIndex >= 0)
        assertTrue(searchIndex > headerIndex)
        assertTrue(settings.contains(".nestedScroll(homeHeaderPullState.nestedScrollConnection)"))
        assertTrue(settings.contains("canStartPull = { scrollState.value == 0 }"))
        assertTrue(settings.contains("settingsSearchQuery.isBlank()"))
        assertTrue(host.contains("onHomeHeaderPullTriggered = onOpenNetworkOptimization"))
        assertTrue(host.contains("onOpen = onOpenNetworkOptimization"))
        assertTrue(navigation.contains("SteamNetworkOptimizationAutoScreen("))
        assertFalse(host.contains("SteamNetworkOptimizationHeroCard("))
    }

    @Test
    fun cardUsesTheSameCompactExpandedMotionAsMonicaPass() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkOptimizationPullCard.kt"
        ).readText()

        assertTrue(source.contains("compactHeight = 88.dp"))
        assertTrue(source.contains("expandedHeight = 236.dp"))
        assertTrue(source.contains("cornerRadius = 28.dp + 16.dp * progress"))
        assertTrue(source.contains("surfaceContainerHigh"))
        assertTrue(source.contains("primaryContainer"))
        assertTrue(source.contains("rotationZ = progress * 180f"))
        assertTrue(source.contains("clearAndSetSemantics"))
        assertTrue(source.contains("steam_network_auto_pull_release"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
