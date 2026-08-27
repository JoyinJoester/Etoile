package takagi.ru.monica.steam.store.freebie.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFreebieUiGuardTest {
    @Test
    fun freebiePageKeepsCommonM3eAccountRefreshAndDockComponents() {
        val screenFile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/freebie/ui/SteamFreebieScreen.kt"
        )
        val cardFile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/freebie/ui/components/SteamFreebieCard.kt"
        )
        val screen = screenFile.readText()
        val card = cardFile.readText()

        assertTrue("SteamFreebieScreen.kt is too large", screenFile.readLines().size <= 500)
        assertTrue("SteamFreebieCard.kt is too large", cardFile.readLines().size <= 400)
        assertTrue(screen.contains("ExpressiveTopBar("))
        assertTrue(screen.contains("SteamExpressivePullToRefresh("))
        assertTrue(screen.contains("SteamAccountSwitcherSheet("))
        assertTrue(screen.contains("SingleChoiceSegmentedButtonRow("))
        assertTrue(screen.contains("LocalSteamDockContentClearance.current"))
        assertTrue(card.contains("Modifier.heightIn(min = 48.dp)"))
        assertTrue(card.contains("SteamStoreImage("))
    }

    @Test
    fun storeDetailAndOfficialWebReturnToTheFreebiePage() {
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreBrowseMenu.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(menu.contains("onOpenFreebies"))
        assertTrue(menu.contains("R.string.steam_store_freebies"))
        assertTrue(store.contains("SteamStoreDestination.Freebies"))
        assertTrue(store.contains("SteamFreebieScreen("))
        assertTrue(store.indexOf("detailAppId != null ->") < store.indexOf("freebiesOpen ->"))
        assertTrue(store.contains("state.detailAppId != null -> viewModel.closeDetail()"))
        assertTrue(store.contains("freebiesOpen -> freebiesOpen = false"))
    }

    @Test
    fun freeLicenseActionsUseTheAuthenticatedOfficialStorePage() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/freebie/ui/components/SteamFreebieCard.kt"
        ).readText()
        val detailButton = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/purchase/ui/SteamStoreFreeLicenseButton.kt"
        ).readText()

        assertTrue(store.contains("SteamStoreFreeLicenseButton"))
        assertTrue(store.contains("if (hasPurchasablePackage)"))
        assertFalse(store.contains("if (freeLicenseOption == null)"))
        assertTrue(store.contains("onOpenOfficial = viewModel::openAuthenticatedStoreWeb"))
        assertTrue(store.contains("viewModel.openAuthenticatedStoreWeb(detail.storeUrl)"))
        assertTrue(store.contains("requireAuthenticatedSession = state.webRequiresAuthenticatedSession"))
        assertTrue(viewModel.contains("webRequiresAuthenticatedSession"))
        assertTrue(viewModel.contains("fun openAuthenticatedStoreWeb"))
        assertTrue(card.contains("onClick = onOpenOfficial"))
        assertTrue(detailButton.contains("onClick = onOpenOfficial"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
