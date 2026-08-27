package takagi.ru.monica.steam.navigation.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWindowChromeInsetsTest {
    @Test
    fun multiWindowUsesFallbackWhenWindowInsetsAreMissing() {
        val result = reduceSteamWindowChromeInsets(
            previous = SteamWindowChromeInsetsPx(),
            observedTopPx = 0,
            observedBottomPx = 0,
            fallbackTopPx = 24,
            fallbackBottomPx = 12,
            isInMultiWindowMode = true
        )

        assertEquals(SteamWindowChromeInsetsPx(topPx = 24, bottomPx = 12), result)
    }

    @Test
    fun multiWindowKeepsInsetsStableDuringTransientZeroFrames() {
        val result = reduceSteamWindowChromeInsets(
            previous = SteamWindowChromeInsetsPx(topPx = 31, bottomPx = 18),
            observedTopPx = 0,
            observedBottomPx = 0,
            fallbackTopPx = 24,
            fallbackBottomPx = 12,
            isInMultiWindowMode = true
        )

        assertEquals(SteamWindowChromeInsetsPx(topPx = 31, bottomPx = 18), result)
    }

    @Test
    fun normalWindowFollowsCurrentInsetsWithoutRetainingOldValues() {
        val result = reduceSteamWindowChromeInsets(
            previous = SteamWindowChromeInsetsPx(topPx = 31, bottomPx = 18),
            observedTopPx = 0,
            observedBottomPx = 0,
            fallbackTopPx = 24,
            fallbackBottomPx = 12,
            isInMultiWindowMode = false
        )

        assertEquals(SteamWindowChromeInsetsPx(), result)
    }

    @Test
    fun imeSuppressesExtraBottomPaddingWithoutChangingStableInset() {
        val insets = SteamWindowChromeInsetsPx(topPx = 31, bottomPx = 18)

        assertEquals(0, resolveSteamWindowBottomPaddingPx(insets, imeVisible = true))
        assertEquals(18, resolveSteamWindowBottomPaddingPx(insets, imeVisible = false))
    }

    @Test
    fun topLevelPagesUseTheSharedWindowChromeInset() {
        listOf(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatRootContent.kt",
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).forEach { path ->
            assertTrue(
                "$path must avoid the small-window caption area",
                projectFile(path).readText().contains("steamWindowTopPadding()")
            )
        }
    }

    @Test
    fun sharedInsetReadsSafeDrawingAndMultiWindowState() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamWindowChromeInsets.kt"
        ).readText()

        assertTrue(source.contains("WindowInsets.safeDrawing"))
        assertTrue(source.contains("isInMultiWindowMode"))
        assertTrue(source.contains("SteamWindowChromeInsetMemory"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
