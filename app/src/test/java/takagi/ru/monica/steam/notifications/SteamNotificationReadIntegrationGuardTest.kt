package takagi.ru.monica.steam.notifications

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNotificationReadIntegrationGuardTest {
    @Test
    fun notificationCardReadCallbackReachesSteamViewModel() {
        val notificationScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/ui/SteamNotificationsScreen.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(notificationScreen.contains("onNotificationOpened(notification.id)"))
        assertTrue(steamScreen.contains("onNotificationOpened = viewModel::markSteamNotificationRead"))
        assertTrue(viewModel.contains("notificationService.markRead(freshAccount"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, relativePath)
    }
}
