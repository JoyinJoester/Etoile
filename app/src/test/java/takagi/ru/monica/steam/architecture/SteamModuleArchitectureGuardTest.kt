package takagi.ru.monica.steam.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamModuleArchitectureGuardTest {
    @Test
    fun steamRootContainsOnlyModuleDirectories() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam")

        assertTrue(root.isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })
    }

    @Test
    fun documentedFeatureAndSharedModulesExist() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam")
        val expectedExistingModules = setOf(
            "alerts",
            "analytics",
            "backup",
            "community",
            "confirmations",
            "core",
            "data",
            "diagnostics",
            "foundation",
            "friends",
            "gifts",
            "health",
            "inventory",
            "importer",
            "io",
            "library",
            "market",
            "navigation",
            "network",
            "notifications",
            "organization",
            "outbox",
            "ownership",
            "profile",
            "quickaccess",
            "scanner",
            "security",
            "session",
            "store",
            "token",
            "trade"
        )
        val actual = root.listFiles().orEmpty()
            .filter { directory ->
                directory.isDirectory && directory.walkTopDown().any { file ->
                    file.isFile && file.extension == "kt"
                }
            }
            .map(File::getName)
            .toSet()

        assertEquals(expectedExistingModules, actual)
    }

    @Test
    fun legacySteamUiCannotReceiveAdditionalFiles() {
        val legacyUi = projectFile("app/src/main/java/takagi/ru/monica/steam/ui")
        val actual = legacyUi.listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .map(File::getName)
            .toSet()

        assertEquals(emptySet<String>(), actual)
    }

    @Test
    fun applicationHostDoesNotDependOnFeatureInternals() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileApplication.kt"
        ).readText()

        assertFalse(activity.contains(".friends.data."))
        assertFalse(activity.contains(".friends.domain."))
        assertFalse(activity.contains(".friends.presentation."))
        assertFalse(activity.contains(".friends.chat.background.data."))
        assertFalse(activity.contains(".friends.chat.background.domain."))
        assertFalse(activity.contains(".alerts.data."))
        assertFalse(application.contains(".friends.chat.background.data."))
        assertTrue(activity.contains(".friends.chat.background.SteamChatBackground"))
        assertTrue(application.contains(".friends.chat.background.SteamChatBackground"))
        assertTrue(activity.contains(".alerts.SteamAlerts"))
        assertTrue(activity.contains(".friends.chat.ui.SteamChatScreen"))
        assertTrue(activity.contains(".scanner.ui.SteamQrScannerScreen"))
        assertTrue(activity.contains(".token.ui.SteamScreen"))
        assertFalse(activity.contains(".token.presentation."))
    }

    @Test
    fun migratedFeaturesOwnTheirImplementations() {
        val featureFiles = listOf(
            "foundation/ui/SteamAvatarImage.kt",
            "profile/ui/SteamMiniProfileBackgroundLayer.kt",
            "profile/ui/SteamMiniProfileCrop.kt",
            "scanner/data/SteamQrAccountPreference.kt",
            "scanner/ui/SteamQrScannerScreen.kt"
        )

        featureFiles.forEach { relativePath ->
            val source = projectFile(
                "app/src/main/java/takagi/ru/monica/steam/$relativePath"
            )
            assertTrue("Missing Steam feature file: $relativePath", source.isFile)
            assertFalse(source.readText().contains("takagi.ru.monica.steam.ui."))
        }
    }

    @Test
    fun inventoryAndMarketOwnTheirUiEntries() {
        assertTrue(projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).isFile)
        assertTrue(projectFile(
            "app/src/main/java/takagi/ru/monica/steam/market/ui/SteamBatchSellSheet.kt"
        ).isFile)
    }

    @Test
    fun foundationUiDoesNotDependOnFeatureUi() {
        val foundationUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui"
        )
        foundationUi.listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { source ->
                val text = source.readText()
                assertFalse(text.contains(".steam.profile.ui."))
                assertFalse(text.contains(".steam.inventory.ui."))
                assertFalse(text.contains(".steam.market.ui."))
                assertFalse(text.contains(".steam.token.ui."))
        }
    }

    @Test
    fun storeUsesLayeredPackagesAndNamedSlicesWithoutRootImplementations() {
        val store = projectFile("app/src/main/java/takagi/ru/monica/steam/store")
        val layers = store.listFiles().orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .toSet()

        assertEquals(
            setOf(
                "activation",
                "bundle",
                "catalog",
                "data",
                "domain",
                "filters",
                "freebie",
                "gift",
                "hints",
                "navigation",
                "points",
                "presentation",
                "purchase",
                "related",
                "requirements",
                "ui"
            ),
            layers
        )
        assertTrue(store.listFiles().orEmpty().none { it.extension == "kt" })

        val domainSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/domain/SteamStoreModels.kt"
        ).readText()
        assertFalse(domainSource.contains(".store.data."))
        assertFalse(domainSource.contains(".store.presentation."))
        assertFalse(domainSource.contains(".store.ui."))

        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        assertTrue(activity.contains(".store.ui.SteamStoreScreen"))
        assertFalse(activity.contains(".store.data."))
        assertFalse(activity.contains(".store.presentation."))
    }

    @Test
    fun notificationsGiftsAndAlertsKeepSeparateLayeredRoots() {
        val expectedLayers = mapOf(
            "notifications" to setOf("data", "domain", "settings", "ui"),
            "gifts" to setOf("data", "domain"),
            "alerts" to setOf("data", "domain")
        )

        expectedLayers.forEach { (feature, layers) ->
            val root = projectFile("app/src/main/java/takagi/ru/monica/steam/$feature")
            val rootFiles = root.listFiles().orEmpty()
                .filter { it.extension == "kt" }
                .map(File::getName)
                .toSet()
            if (feature == "alerts") {
                assertEquals(setOf("SteamAlerts.kt"), rootFiles)
            } else {
                assertEquals(emptySet<String>(), rootFiles)
            }
            assertEquals(
                layers,
                root.listFiles().orEmpty()
                    .filter(File::isDirectory)
                    .map(File::getName)
                    .toSet()
            )
        }

        val giftService = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/gifts/data/SteamGiftService.kt"
        ).readText()
        assertFalse(giftService.contains(".steam.notifications."))

        val notificationModels = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/domain/SteamNotificationModels.kt"
        ).readText()
        assertFalse(notificationModels.contains("enum class SteamGiftAction"))

        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".steam.alerts.data.SteamAlertReceiver"))
    }

    @Test
    fun architectureDocumentRecordsCompletedLegacyMigration() {
        val document = projectFile("docs/architecture/STEAM_MODULES.md").readText()

        assertTrue(document.contains("## Feature Modules"))
        assertTrue(document.contains("## Shared Modules"))
        assertTrue(document.contains("## Dependency Rules"))
        assertTrue(document.contains("## Legacy UI Migration Status"))
        assertTrue(document.contains("migration allowlist is empty"))
        assertFalse(document.contains("SteamScreen.kt"))
        assertFalse(document.contains("SteamViewModel.kt"))
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
