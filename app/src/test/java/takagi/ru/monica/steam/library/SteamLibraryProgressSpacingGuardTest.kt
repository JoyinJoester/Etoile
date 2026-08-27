package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryProgressSpacingGuardTest {
    @Test
    fun overviewUsesExpressivePullIndicatorOutsideScrollableContent() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val overview = screen
            .substringAfter("SteamLibraryDestination.Overview ->")
            .substringBefore("if (showAccountSheet")

        assertTrue(overview.contains("SteamExpressivePullToRefresh("))
        assertTrue(overview.contains("modifier = Modifier.fillMaxSize().padding(padding)"))
        assertTrue(overview.contains("SteamLibraryOverview("))
        assertFalse(overview.contains("LinearProgressIndicator("))
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
