package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSettingsScrollStateGuardTest {
    @Test
    fun settingsRootOwnsScrollStateAcrossChildPageNavigation() {
        val steamSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt"
        ).readText()
        val sharedHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val settingsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()

        assertTrue(steamSettings.contains("val settingsScrollState = rememberScrollState()"))
        assertTrue(steamSettings.contains("val navigationScrollState = rememberScrollState()"))
        assertFalse(steamSettings.contains("val connectivityScrollState = rememberScrollState()"))
        assertTrue(steamSettings.contains("val appSupportScrollState = rememberScrollState()"))
        assertTrue(steamSettings.contains("scrollState = settingsScrollState"))
        assertTrue(sharedHost.contains("scrollState: ScrollState"))
        assertTrue(sharedHost.contains("scrollState = scrollState"))
        assertTrue(settingsScreen.contains("scrollState: ScrollState = rememberScrollState()"))
        assertFalse(settingsScreen.contains("val scrollState = rememberScrollState()"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
