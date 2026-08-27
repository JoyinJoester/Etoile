package takagi.ru.monica.steam.links.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLinkHandlingSettingsGuardTest {
    @Test
    fun steamSettingsProvidesAndroidDomainAssociationEntryWithFallback() {
        val entry = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/links/ui/SteamLinkHandlingSettingsEntry.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()

        assertTrue(entry.contains("Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS"))
        assertTrue(entry.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(host.contains("SteamLinkHandlingSettingsEntry"))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) {
            it.parentFile
        }.firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
