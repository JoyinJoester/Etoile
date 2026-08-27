package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreBrowseMenuUiGuardTest {
    @Test
    fun browseCategoriesReuseTheCommonTopActionsMenuInsteadOfADrawer() {
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreBrowseMenu.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(menu.contains("MonicaTopActionsDropdownMenu("))
        assertTrue(menu.contains("DropdownMenuItem("))
        assertTrue(menu.contains("Icons.Default.Check"))
        assertTrue(menu.contains("onOpenPointsShop"))
        assertFalse(menu.contains("ModalNavigationDrawer("))
        assertFalse(menu.contains("ModalDrawerSheet("))
        assertTrue(screen.contains("SteamStoreBrowseMenu("))
        assertFalse(screen.contains("SteamStoreBrowseDrawer("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
