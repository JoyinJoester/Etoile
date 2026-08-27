package takagi.ru.monica.steam.library.screenshots.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameScreenshotsIntegrationGuardTest {
    @Test
    fun libraryDetailNavigatesToNativeAuthenticatedGameScreenshotsAndReturns() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/screenshots/ui/SteamGameScreenshotsScreen.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/screenshots/data/SteamGameScreenshotsService.kt"
        ).readText()

        assertTrue(library.contains("SteamLibraryDestination.Screenshots"))
        assertTrue(library.contains("SteamGameScreenshotsEntry"))
        assertTrue(library.contains("SteamGameScreenshotsScreen"))
        assertTrue(screen.contains("LazyVerticalGrid"))
        assertTrue(screen.contains("SteamExpressivePullToRefresh"))
        assertTrue(screen.contains("SteamFullscreenImageViewer"))
        assertTrue(service.contains("communityGetText"))
        assertTrue(service.contains("steamLoginSecure"))
        assertTrue(service.contains("mobileClientVersion"))
        assertTrue(
            !projectFile(
                "app/src/main/java/takagi/ru/monica/steam/library/screenshots/ui/SteamGameScreenshotsWebScreen.kt"
            ).exists()
        )
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) {
            it.parentFile
        }.firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
