package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLoginOnlyMarketCapabilityTest {
    @Test
    fun inventoryAndMarketRemainSessionFeaturesWhileAutoConfirmationRequiresSecrets() {
        val inventoryUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()
        val batchUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/market/ui/SteamBatchSellSheet.kt"
        ).readText()
        val rootUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(inventoryUi.contains("!account.hasAuthenticatedSession"))
        assertTrue(inventoryUi.contains("if (canAutoConfirm)"))
        assertTrue(batchUi.contains("if (canAutoConfirm)"))
        assertTrue(
            rootUi.contains("canAutoConfirm = selectedAccount?.canUseConfirmations == true")
        )
        assertTrue(
            viewModel.contains("val shouldAutoConfirm = autoConfirm && account.canUseConfirmations")
        )
        assertTrue(viewModel.contains("val preExistingMarketIds = if (shouldAutoConfirm)"))
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
