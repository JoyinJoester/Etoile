package takagi.ru.monica.steam.navigation.liquidglass.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassDockMotionTest {
    @Test
    fun velocityIsNormalizedByTheVisibleItemWidth() {
        assertEquals(
            2.5f,
            resolveLiquidGlassDockVelocityItemsPerSecond(
                velocityPxPerSecond = 250f,
                itemWidthPx = 100f
            ),
            0.0001f
        )
        assertEquals(
            0f,
            resolveLiquidGlassDockVelocityItemsPerSecond(
                velocityPxPerSecond = 250f,
                itemWidthPx = 0f
            ),
            0.0001f
        )
    }

    @Test
    fun releaseProjectionMovesAtMostOneDestination() {
        assertEquals(
            2,
            resolveLiquidGlassDockReleaseTargetIndex(
                currentValue = 1.2f,
                velocityPxPerSecond = 2_000f,
                itemWidthPx = 100f,
                itemCount = 5
            )
        )
        assertEquals(
            0,
            resolveLiquidGlassDockReleaseTargetIndex(
                currentValue = 0.2f,
                velocityPxPerSecond = -2_000f,
                itemWidthPx = 100f,
                itemCount = 5
            )
        )
    }
}
