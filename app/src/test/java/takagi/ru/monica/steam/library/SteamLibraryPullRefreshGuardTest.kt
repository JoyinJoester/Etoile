package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryPullRefreshGuardTest {
    @Test
    fun overviewUsesRealRefreshStateSharedMenuAndExpressiveIndicator() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val overview = library
            .substringAfter("SteamLibraryDestination.Overview ->")
            .substringBefore("}", missingDelimiterValue = library)

        assertTrue(library.contains("SteamPageOverflowMenu("))
        assertTrue(library.contains("onOpenSettings = onOpenSettings"))
        assertTrue(library.contains("SteamExpressivePullToRefresh("))
        assertTrue(library.contains("refreshing = state.loadingLibrary"))
        assertTrue(library.contains("onRefresh = viewModel::refreshLibrary"))
        assertFalse(overview.contains("LinearProgressIndicator("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
