package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFixedBottomBarGuardTest {
    @Test
    fun fixedBarUsesMaterialNavigationBarWithLabelsAndInsets() {
        val component = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamFixedBottomBar.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()

        assertTrue(component.contains("NavigationBar("))
        assertTrue(component.contains("NavigationBarItem("))
        assertTrue(component.contains("alwaysShowLabel = true"))
        assertTrue(component.contains("SteamDockTab.completeFixedOrder(order)"))
        assertTrue(component.contains("TextOverflow.Ellipsis"))
        assertTrue(component.contains("rememberSteamWindowBottomInsets()"))
        assertTrue(component.contains("windowInsets = windowInsets"))
        assertTrue(settings.contains("FIXED_ORDER_KEY"))
        assertTrue(settings.contains("updateFixedOrder"))
        assertTrue(activity.contains("SteamFixedBottomBar("))
        assertTrue(activity.contains("dockStyle == SteamDockStyle.FIXED"))
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
