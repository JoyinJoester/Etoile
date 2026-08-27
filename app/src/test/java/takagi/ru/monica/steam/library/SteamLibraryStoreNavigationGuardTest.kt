package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryStoreNavigationGuardTest {
    @Test
    fun libraryGameDetailsExposeStoreNavigation() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(screen.contains("onOpenStoreApp: (Int) -> Unit"))
        assertTrue(screen.contains("onClick = { onOpenStoreApp(game.appId) }"))
        assertFalse(screen.contains("SteamLibraryGameContextSection("))
        assertTrue(screen.contains("Icons.Default.Storefront"))
        assertTrue(screen.contains("R.string.steam_library_open_store"))
        assertTrue(activity.contains("pendingStoreAppId = appId"))
        assertTrue(activity.contains("navigateTo(EtoilePage.STORE)"))
        assertTrue(store.contains("initialAppId: Int? = null"))
        assertTrue(store.contains("viewModel.openDetail(appId)"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
