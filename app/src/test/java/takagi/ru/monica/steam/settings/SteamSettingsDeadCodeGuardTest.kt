package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSettingsDeadCodeGuardTest {
    @Test
    fun sharedSettingsHostIsTheOnlySteamSettingsRoot() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt"
        ).readText()

        assertTrue(screen.contains("EtoileSharedSettingsHost("))
        assertFalse(screen.contains("EtoileLegacySettingsScreen"))
        assertFalse(screen.contains("private fun SteamSettingsSection("))
        assertFalse(screen.contains("private fun SteamSettingsItem("))
        assertFalse(screen.contains("private fun SteamSettingsSwitchItem("))
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
