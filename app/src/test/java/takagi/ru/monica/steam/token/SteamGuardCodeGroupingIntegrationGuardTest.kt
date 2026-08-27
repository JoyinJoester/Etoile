package takagi.ru.monica.steam.token

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGuardCodeGroupingIntegrationGuardTest {
    @Test
    fun settingPersistsAndFormatsCurrentAndNextCodes() {
        val appSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/data/AppSettings.kt"
        ).readText()
        val manager = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt"
        ).readText()
        val sharedSettingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt"
        ).readText()

        assertTrue(appSettings.contains("steamGuardCodeGroupingEnabled: Boolean = true"))
        assertTrue(manager.contains("STEAM_GUARD_CODE_GROUPING_ENABLED_KEY"))
        assertTrue(manager.contains("updateSteamGuardCodeGroupingEnabled"))
        assertTrue(sharedSettingsHost.contains("settings.steamGuardCodeGroupingEnabled"))
        assertTrue(sharedSettingsHost.contains("updateSteamGuardCodeGroupingEnabled"))
        assertTrue(
            card.windowed("settings.steamGuardCodeGroupingEnabled".length, 1)
                .count { it == "settings.steamGuardCodeGroupingEnabled" } >= 2
        )
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
