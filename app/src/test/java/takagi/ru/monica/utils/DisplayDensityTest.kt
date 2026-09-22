package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayDensityTest {
    @Test
    fun `scale is clamped to supported range`() {
        assertEquals(DisplayDensity.MIN_SCALE, DisplayDensity.clampScale(0))
        assertEquals(DisplayDensity.MAX_SCALE, DisplayDensity.clampScale(999))
        assertEquals(110, DisplayDensity.clampScale(110))
    }

    @Test
    fun `density is calculated from base density and scale`() {
        assertEquals(320, DisplayDensity.densityDpiFor(320, 100))
        assertEquals(240, DisplayDensity.densityDpiFor(320, 75))
        assertEquals(480, DisplayDensity.densityDpiFor(320, 150))
    }
}
