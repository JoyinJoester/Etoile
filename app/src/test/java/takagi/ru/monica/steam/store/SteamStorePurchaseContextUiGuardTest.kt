package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.ui.steamStoreOwnershipStatusForDisplay
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

class SteamStorePurchaseContextUiGuardTest {
    @Test
    fun purchaseContextLivesInAFocusedModuleAndIsWiredIntoDetail() {
        val root = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/purchase"
        )
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val component = root.resolve("ui/SteamStorePurchaseContextSection.kt")
        val componentSource = component.readText()
        val packageOptionsSource = componentSource
            .substringAfter("private fun PackageOptionsCard(")
            .substringBefore("private fun RelatedAppsCard(")

        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(component.readLines().size <= 400)
        assertTrue(screen.contains("SteamStorePurchaseContextSection("))
        assertTrue(screen.contains("selectedPackageId"))
        assertFalse(componentSource.contains("onOpenRelatedApp"))
        assertTrue(screen.contains("viewModel.addDetailToCart(detail, packageOption)"))
        assertTrue(viewModel.contains("purchaseContextCache?.load(account.steamId, appId)"))
        assertTrue(viewModel.contains("steamStorePurchaseContextRequestIsCurrent"))
        assertTrue(viewModel.contains("if (!refreshedDetail.isDlc)"))
        assertTrue(componentSource.contains("PackageOptionsCard("))
        assertFalse(componentSource.contains("RelatedAppsCard("))
        assertTrue(componentSource.contains("SteamStoreOwnershipStatus.FAMILY_SHARED"))
        assertTrue(componentSource.contains("detail.isDlc"))
        assertFalse(componentSource.contains("R.string.steam_store_purchase_context_summary"))
        assertTrue(packageOptionsSource.contains("verticalAlignment = Alignment.CenterVertically"))
        assertTrue(packageOptionsSource.contains("formatSteamPrice(option.priceCents, currency)"))
        assertTrue(packageOptionsSource.contains("option.imageUrl.isNotBlank()"))
        assertTrue(packageOptionsSource.contains("maxLines = 2"))
        assertTrue(packageOptionsSource.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun unknownOrMissingOwnershipIsNotPresentedAsARealAccountStatus() {
        val game = SteamStoreDetail(appId = 620, name = "Game")
        val dlc = SteamStoreDetail(appId = 621, name = "DLC", type = "dlc")
        assertNull(steamStoreOwnershipStatusForDisplay(game, null))
        assertNull(
            steamStoreOwnershipStatusForDisplay(
                game,
                purchaseContext(SteamStoreOwnershipStatus.UNKNOWN)
            )
        )
        assertEquals(
            SteamStoreOwnershipStatus.OWNED,
            steamStoreOwnershipStatusForDisplay(
                game,
                purchaseContext(SteamStoreOwnershipStatus.OWNED)
            )
        )
        assertEquals(
            SteamStoreOwnershipStatus.FAMILY_SHARED,
            steamStoreOwnershipStatusForDisplay(
                game,
                purchaseContext(SteamStoreOwnershipStatus.FAMILY_SHARED)
            )
        )
        assertNull(
            steamStoreOwnershipStatusForDisplay(
                dlc,
                purchaseContext(SteamStoreOwnershipStatus.OWNED)
            )
        )
    }

    private fun purchaseContext(status: SteamStoreOwnershipStatus) = SteamStorePurchaseContext(
        accountSteamId = "76561198000000000",
        appId = 620,
        ownership = status
    )

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
