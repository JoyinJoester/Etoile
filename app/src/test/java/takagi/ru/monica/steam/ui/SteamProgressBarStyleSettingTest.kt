package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamProgressBarStyleSettingTest {
    @Test
    fun appearanceSettingsReuseMonicaProgressStylePicker() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()

        assertTrue(host.contains("settings.validatorProgressBarStyle == ProgressBarStyle.WAVE"))
        assertTrue(host.contains("title = context.getString(R.string.validator_progress_bar_style)"))
        assertTrue(host.contains("ProgressBarStyleDialog("))
        assertTrue(host.contains("settingsViewModel.updateValidatorProgressBarStyle(style)"))
        assertTrue(host.contains("R.string.progress_bar_style_linear"))
        assertTrue(host.contains("R.string.progress_bar_style_wave"))
    }

    @Test
    fun pickerUsesWholeRowAsTheTouchTarget() {
        val settingsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val dialog = settingsScreen
            .substringAfter("fun ProgressBarStyleDialog(")
            .substringBefore("fun NotificationValidatorCard(")

        assertTrue(dialog.contains("ListItem("))
        assertTrue(dialog.contains("onClick = null"))
        assertTrue(dialog.contains("Modifier.clickable { onStyleSelected(style) }"))
    }

    @Test
    fun tokenPageConsumesThePersistedStyle() {
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val settingsManager = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt"
        ).readText()

        assertTrue(tokenScreen.contains("style = appSettings.validatorProgressBarStyle"))
        assertTrue(settingsManager.contains("updateValidatorProgressBarStyle"))
        assertTrue(settingsManager.contains("VALIDATOR_PROGRESS_BAR_STYLE_KEY"))
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
