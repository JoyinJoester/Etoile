package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreRelatedContentUiGuardTest {
    @Test
    fun storeOwnsResponsiveDlcCardsAndNativeNavigation() {
        val component = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/related/ui/SteamStoreRelatedContentSection.kt"
        )
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val purchase = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/purchase/ui/SteamStorePurchaseContextSection.kt"
        ).readText()

        assertTrue(component.exists())
        val source = component.readText()
        assertTrue(source.contains("LazyRow("))
        assertTrue(source.contains("Card("))
        assertTrue(source.contains("SteamStoreImage("))
        assertTrue(source.contains("item.name"))
        assertTrue(source.contains("R.string.steam_store_related_game"))
        assertTrue(source.contains("R.string.steam_store_related_dlc"))
        assertTrue(source.contains("R.string.steam_store_related_demo"))
        assertTrue(source.contains("heightIn(min = 48.dp)"))
        assertFalse(source.contains("DLC #"))
        assertTrue(screen.contains("SteamStoreRelatedContentSection("))
        assertTrue(screen.contains("relatedDlc = detail.relatedDlc"))
        assertTrue(screen.contains("onOpenRelatedApp = viewModel::openRelatedDetail"))
        assertFalse(purchase.contains("RelatedAppsCard("))
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
