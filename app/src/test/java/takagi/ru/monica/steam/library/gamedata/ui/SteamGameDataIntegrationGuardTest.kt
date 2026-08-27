package takagi.ru.monica.steam.library.gamedata.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameDataIntegrationGuardTest {
    @Test
    fun libraryDetailNavigatesToAuthenticatedGameDataAndDelegatesDownloads() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserScreen.kt"
        ).readText()
        val gameDataWeb = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/gamedata/ui/SteamGameDataWebScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()

        assertTrue(library.contains("SteamLibraryDestination.GameData"))
        assertTrue(library.contains("SteamGameDataEntry"))
        assertTrue(library.contains("SteamGameDataWebScreen"))
        assertTrue(web.contains("setDownloadListener"))
        assertTrue(web.contains("onDownloadRequested"))
        assertTrue(gameDataWeb.contains("Intent(Intent.ACTION_VIEW, uri)"))
        assertTrue(gameDataWeb.contains("selector = Intent(Intent.ACTION_MAIN)"))
        assertTrue(gameDataWeb.contains("Intent.CATEGORY_APP_BROWSER"))
        assertTrue(activity.contains("onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged"))
    }

    @Test
    fun optionalGameDataAndScreenshotActionsStayCompactAndSideBySide() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val gameDataEntry = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/gamedata/ui/SteamGameDataEntry.kt"
        ).readText()
        val screenshotEntry = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/screenshots/ui/SteamGameScreenshotsEntry.kt"
        ).readText()

        assertTrue(library.contains("if (gameDataPage != null || screenshotsPage != null)"))
        assertTrue(library.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(library.contains("modifier = Modifier.weight(1f)"))
        assertTrue(gameDataEntry.contains("defaultMinSize(minHeight = 56.dp)"))
        assertTrue(screenshotEntry.contains("defaultMinSize(minHeight = 56.dp)"))
        assertFalse(gameDataEntry.contains("steam_library_game_data_description"))
        assertFalse(screenshotEntry.contains("steam_library_screenshots_description"))
        assertFalse(gameDataEntry.contains("KeyboardArrowRight"))
        assertFalse(screenshotEntry.contains("KeyboardArrowRight"))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) {
            it.parentFile
        }.firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
