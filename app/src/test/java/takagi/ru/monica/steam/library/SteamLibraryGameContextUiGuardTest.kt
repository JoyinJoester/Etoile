package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryGameContextUiGuardTest {
    @Test
    fun libraryDetailOmitsRedundantContextCardsAndNetworkRequest() {
        val root = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/context"
        )
        val service = root.resolve("data/SteamLibraryGameContextService.kt").readText()
        val component = root.resolve("ui/SteamLibraryGameContextSection.kt")
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()
        val filters = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryFilters.kt"
        ).readText()
        val libraryService = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamGameLibraryService.kt"
        ).readText()

        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertFalse(component.exists())
        assertTrue(service.contains("method = \"GetAppFileChangelist\""))
        assertTrue(service.contains("iface = \"ICloudService\""))
        assertTrue(!service.contains("CommitFileUpload"))
        assertTrue(!service.contains("ClientDeleteFile"))
        assertFalse(screen.contains("SteamLibraryGameContextSection("))
        val openGame = viewModel.substringAfter("fun openGame(game: SteamGame)")
            .substringBefore("fun closeGame()")
        assertFalse(openGame.contains("loadGameContext("))
        assertFalse(openGame.contains("loadingGameContext = true"))
        assertTrue(viewModel.contains("gameContextCache?.load(account.steamId, game.appId)"))
        assertTrue(viewModel.contains("steamLibraryGameContextRequestIsCurrent"))
        assertTrue(filters.contains("STEAM_CLOUD"))
        assertTrue(filters.contains("supportsSteamCloud == true"))
        assertTrue(libraryService.contains("appIds = games.map(SteamGame::appId)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
