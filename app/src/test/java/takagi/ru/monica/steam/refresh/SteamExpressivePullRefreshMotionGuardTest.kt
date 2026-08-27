package takagi.ru.monica.steam.refresh

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.foundation.ui.calculatePullRefreshContentOffsetPx
import takagi.ru.monica.steam.foundation.ui.calculatePullRefreshVisualFraction

class SteamExpressivePullRefreshMotionGuardTest {
    @Test
    fun sharedRefreshControlUsesContainedIndicatorAndMovesItsBoxScopedContent() {
        val controls = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamPageRefreshControls.kt"
        ).readText()

        assertTrue(controls.contains("PullToRefreshDefaults.LoadingIndicator("))
        assertTrue(controls.contains("calculatePullRefreshContentOffsetPx("))
        assertTrue(controls.contains("calculatePullRefreshVisualFraction("))
        assertTrue(controls.contains("VisualPullToRefreshState("))
        assertTrue(controls.contains("state = visualIndicatorState"))
        assertTrue(controls.contains(".offset { IntOffset(0,"))
        assertTrue(controls.contains("state.isAnimating"))
        assertTrue(controls.contains("keepContentAtRestUntilHidden"))
        assertTrue(controls.contains("enabled: Boolean = true"))
        assertTrue(controls.contains("content()"))
        assertFalse(controls.contains("content = content"))
        assertFalse(controls.contains("import androidx.compose.material3.LoadingIndicator"))
    }

    @Test
    fun contentOffsetTracksTheGestureButStaysAtRestDuringRefreshAndHide() {
        assertEquals(
            0f,
            calculatePullRefreshContentOffsetPx(0f, 80f, trackPull = true),
            0.001f
        )
        assertEquals(
            40f,
            calculatePullRefreshContentOffsetPx(0.5f, 80f, trackPull = true),
            0.001f
        )
        assertEquals(
            87.2f,
            calculatePullRefreshContentOffsetPx(1.5f, 80f, trackPull = true),
            0.001f
        )
        assertEquals(
            92f,
            calculatePullRefreshContentOffsetPx(20f, 80f, trackPull = true),
            0.001f
        )
        assertEquals(1.09f, calculatePullRefreshVisualFraction(1.5f), 0.001f)
        assertEquals(1.15f, calculatePullRefreshVisualFraction(20f), 0.001f)
        assertEquals(0f, calculatePullRefreshVisualFraction(-2f), 0.001f)
        assertEquals(
            0f,
            calculatePullRefreshContentOffsetPx(1f, 80f, trackPull = false),
            0.001f
        )
        assertEquals(
            0f,
            calculatePullRefreshContentOffsetPx(Float.NaN, 80f, trackPull = true),
            0.001f
        )
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
