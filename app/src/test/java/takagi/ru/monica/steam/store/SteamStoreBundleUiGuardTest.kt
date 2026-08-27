package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreBundleUiGuardTest {
    @Test
    fun purchaseOptionsAndBundlesUseNativeResponsiveComponents() {
        val bundle = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/bundle/ui/SteamStoreBundleSection.kt"
        ).readText()
        val purchase = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/purchase/ui/SteamStorePurchaseContextSection.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(bundle.contains("ModalBottomSheet("))
        assertTrue(bundle.contains("SteamStoreImage("))
        assertTrue(bundle.contains("onOpenApp(item.appId)"))
        assertTrue(bundle.contains("steam_store_bundle_buy"))
        assertTrue(bundle.contains("heightIn(min = 72.dp)"))
        assertTrue(purchase.contains("option.imageUrl.isNotBlank()"))
        assertTrue(purchase.contains("SteamStoreImage("))
        assertFalse(purchase.contains("RadioButton("))
        assertTrue(screen.contains("SteamStoreBundleSection("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
