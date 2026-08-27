package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamReducedMotionIntegrationGuardTest {
    @Test
    fun steamSettingsExposeAndProvideTheExistingReducedMotionPreference() {
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        val settingsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSettingsScreen.kt"
        ).readText()

        assertTrue(settingsHost.contains("showReduceAnimations = false"))
        assertTrue(settingsHost.contains("SettingsItemWithSwitch("))
        assertTrue(settingsHost.contains("settingsViewModel::updateReduceAnimations"))
        assertTrue(settingsHost.contains("context.getString(R.string.reduce_animations)"))
        assertTrue(
            settingsHost.contains(
                "context.getString(R.string.reduce_animations_description)"
            )
        )
        assertTrue(settingsHost.contains("showPreviewFeatures = false"))
        assertTrue(
            activity.contains("LocalReduceAnimations provides settings.reduceAnimations")
        )
        assertTrue(
            activity.contains("easyNotesScreenEnter(settings.reduceAnimations)")
        )
        assertTrue(
            settingsScreen.contains("easyNotesScreenEnter(settings.reduceAnimations)")
        )
    }

    @Test
    fun steamPageTransitionsUseTheSharedReducedMotionPolicy() {
        val transitions = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/navigation/NavTransitions.kt"
        ).readText()
        assertTrue(transitions.contains("fun easyNotesScreenEnter(reduceAnimations: Boolean)"))
        assertTrue(transitions.contains("fun easyNotesScreenExit(reduceAnimations: Boolean)"))
        assertTrue(transitions.contains("REDUCED_MOTION_FADE_IN_DURATION"))
        assertTrue(transitions.contains("REDUCED_MOTION_FADE_OUT_DURATION"))

        listOf(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatRootContent.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/security/SteamAppLockGate.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).forEach { path ->
            val source = projectFile(path).readText()
            assertTrue(path, source.contains("LocalReduceAnimations.current"))
            assertTrue(path, source.contains("easyNotesScreenEnter(reduceAnimations)"))
            assertTrue(path, source.contains("easyNotesScreenExit(reduceAnimations)"))
        }
    }

    @Test
    fun bothDockStylesKeepFeedbackWithoutBouncyReducedMotion() {
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamEssentialsFloatingToolbar.kt"
        ).readText()
        val liquidMotion = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/motion/LiquidGlassDockMotion.kt"
        ).readText()
        val liquidDock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/ui/SteamLiquidGlassDock.kt"
        ).readText()

        assertTrue(toolbar.contains("LocalReduceAnimations.current"))
        assertTrue(
            toolbar.contains("tween<Dp>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)")
        )
        assertTrue(liquidMotion.contains("reduceMotion: Boolean"))
        assertTrue(
            liquidMotion.contains("tween<Float>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)")
        )
        assertTrue(liquidDock.contains("LocalReduceAnimations.current"))
        assertTrue(liquidDock.contains("reduceMotion = reduceAnimations"))
        assertTrue(liquidDock.contains("if (reduceAnimations) fadeIn("))
        assertTrue(liquidDock.contains("if (reduceAnimations) fadeOut("))
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
