package takagi.ru.monica.steam.network.optimization.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkOptimizationDataExchangeGuardTest {
    @Test
    fun v2DataExchangeSupportsFilesClipboardAndClearConfirmation() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt"
        ).readText()
        val transfer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamHostsDataExchange.kt"
        )
        val actionsMenu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsActionsMenu.kt"
        )

        assertTrue(transfer.exists())
        assertTrue(actionsMenu.exists())
        assertTrue(screen.contains("rememberSteamHostsDataExchange("))
        assertTrue(screen.contains("SteamHostsActionsMenu("))
        assertTrue(screen.contains("AlertDialog("))
        assertTrue(transfer.readText().contains("ActivityResultContracts.OpenDocument"))
        assertTrue(transfer.readText().contains("ActivityResultContracts.CreateDocument"))
        assertTrue(transfer.readText().contains("ClipboardManager"))
        assertTrue(actionsMenu.readText().contains("MonicaTopActionsDropdownMenu("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
