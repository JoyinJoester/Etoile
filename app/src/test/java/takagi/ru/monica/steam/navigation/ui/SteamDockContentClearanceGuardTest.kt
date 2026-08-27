package takagi.ru.monica.steam.navigation.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guard for Steam settings pages that remain underneath the
 * liquid-glass or fixed bottom navigation. The shared screens are also used
 * by Monica, so the optional padding must be supplied by the Steam host.
 */
class SteamDockContentClearanceGuardTest {
    @Test
    fun steamSettingsHostPassesClearanceToEveryScrollableChild() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt"
        ).readText()

        assertTrue(host.contains("val dockContentClearance = LocalSteamDockContentClearance.current"))
        listOf(
            "ColorSchemeSelectionScreen(",
            "CustomColorSettingsScreen(",
            "MonicaPlusScreen(",
            "PaymentScreen(",
            "DeveloperSettingsScreen(",
            "ExtensionsScreen(",
            "MasterPasswordLockingSettingsScreen(",
            "ResetPasswordScreen(",
            "SecurityQuestionsSetupScreen("
        ).forEach { child ->
            assertTrue("$child must be hosted by the Steam clearance-aware settings surface", host.contains(child))
        }
        assertEquals(
            "Every scrollable child must receive the dock-aware padding",
            9,
            Regex("contentBottomPadding = dockContentClearance \\+ 16\\.dp")
                .findAll(host)
                .count()
        )
    }

    @Test
    fun scrollableSettingsChildrenExposeContentBottomPadding() {
        val screens = mapOf(
            "ColorSchemeSelectionScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))",
            "CustomColorSettingsScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))",
            "MonicaPlusScreen.kt" to "bottom = contentBottomPadding",
            "PaymentScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))",
            "DeveloperSettingsScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))",
            "ExtensionsScreen.kt" to "Modifier.height(32.dp + contentBottomPadding)",
            "MasterPasswordLockingSettingsScreen.kt" to
                "Modifier.height(20.dp + contentBottomPadding)",
            "ResetPasswordScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))",
            "SecurityQuestionsSetupScreen.kt" to
                "Spacer(modifier = Modifier.height(contentBottomPadding))"
        )

        screens.forEach { (name, usage) ->
            val source = projectFile(
                "app/src/main/java/takagi/ru/monica/ui/screens/$name"
            ).readText()
            assertTrue("$name must expose optional contentBottomPadding", source.contains("contentBottomPadding"))
            assertTrue("$name must place the padding inside scroll content", source.contains(usage))
        }
    }

    @Test
    fun existingSteamSettingsListsAlsoKeepTheirFinalItemsAboveTheDock() {
        val expectedUsage = mapOf(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt" to
                "contentBottomPadding = dockContentClearance + 16.dp",
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt" to
                "bottom = dockContentClearance + 16.dp",
            "app/src/main/java/takagi/ru/monica/steam/notifications/settings/ui/SteamNotificationSettingsScreen.kt" to
                "contentPadding = PaddingValues(bottom = dockClearance + 24.dp)",
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt" to
                "bottom = dockClearance + 24.dp",
            "app/src/main/java/takagi/ru/monica/steam/store/hints/ui/SteamStoreHintSettingsScreen.kt" to
                "contentPadding = PaddingValues(bottom = dockClearance + 24.dp)"
        )

        expectedUsage.forEach { (path, usage) ->
            val source = projectFile(path).readText()
            assertTrue("$path must retain dock-aware list padding", source.contains(usage))
        }
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
