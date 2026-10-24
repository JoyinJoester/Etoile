package takagi.ru.monica.github.navigation

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubNavigationTransitionsTest {
    private fun motion(start: Int, end: Int) = TargetBasedAnimation(
        animationSpec = GithubNavigationTransitions.slideSpec,
        typeConverter = IntOffset.VectorConverter,
        initialValue = IntOffset(start, 0), targetValue = IntOffset(end, 0),
    )

    @Test fun pushAndPopKeepTheViewportCoveredAtEveryFrameInBothLayoutDirections() {
        for (direction in LayoutDirection.entries) {
            for (width in listOf(360, 1080, 2560)) {
                val foreground = GithubNavigationTransitions.foregroundOffset(width, direction)
                val background = GithubNavigationTransitions.backgroundOffset(width, direction)
                for ((front, back) in listOf(motion(foreground, 0) to motion(0, background), motion(0, foreground) to motion(background, 0))) {
                    assertEquals(front.durationNanos, back.durationNanos)
                    for (time in 0L..front.durationNanos step 8_000_000L) {
                        val frontX = front.getValueFromNanos(time).x
                        val backX = back.getValueFromNanos(time).x
                        val left = minOf(frontX, backX)
                        val right = maxOf(frontX, backX) + width
                        assertTrue("Uncovered viewport edge: $direction $width at $time", left <= 0 && right >= width)
                        assertTrue("Gap between page surfaces: $direction $width at $time", abs(frontX - backX) <= width)
                    }
                }
            }
        }
    }

    @Test fun backgroundKeepsPaceWithForegroundInsteadOfAcceleratingAfterIt() {
        val width = 1000
        val front = motion(GithubNavigationTransitions.foregroundOffset(width, LayoutDirection.Ltr), 0)
        val back = motion(0, GithubNavigationTransitions.backgroundOffset(width, LayoutDirection.Ltr))
        for (time in 0L..front.durationNanos step 8_000_000L) {
            val frontProgress = 1f - front.getValueFromNanos(time).x / width.toFloat()
            val backProgress = back.getValueFromNanos(time).x / -200f
            assertEquals("Mismatched page progress at $time", frontProgress, backProgress, 0.006f)
        }
        assertTrue(front.durationNanos <= 350_000_000L)
        assertEquals(IntOffset.Zero, front.getValueFromNanos(front.durationNanos))
    }

}
