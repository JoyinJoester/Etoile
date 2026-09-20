package takagi.ru.monica.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorSchemeGeneratorTest {
    @Test
    fun customSeedsKeepReadableTextInBothThemes() {
        val seeds = listOf(0xFF000000L, 0xFFFFFFFFL, 0xFFFF0000L, 0xFF00FF00L, 0xFF0000FFL, 0xFF6750A4L)
        for (dark in listOf(false, true)) {
            for (seed in seeds) {
                val scheme = generateCustomMaterialColorScheme(dark, seed, 0xFF009688L, 0xFFFF9800L)
                val pairs = listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.tertiary to scheme.onTertiary,
                    scheme.surface to scheme.onSurface,
                    scheme.error to scheme.onError
                )
                pairs.forEach { (background, foreground) ->
                    assertTrue("Insufficient contrast for seed=$seed dark=$dark", contrast(background, foreground) >= 4.5)
                }
                assertTrue(if (dark) scheme.surface.luminance() < 0.1f else scheme.surface.luminance() > 0.8f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
