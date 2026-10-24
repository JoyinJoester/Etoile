package takagi.ru.monica.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EtoileOverscrollTest {
    @Test fun scrollingWithinContentDoesNotMoveItsContainer() {
        val effect = EtoileOverscrollEffect(16f, 24f)
        var calls = 0
        val delta = Offset(0f, -100f)
        val consumed = effect.applyToScroll(delta, NestedScrollSource.UserInput) {
            calls++
            assertEquals(delta, it)
            it
        }
        assertEquals(delta, consumed)
        assertEquals(1, calls)
        assertFalse(effect.isInProgress)
    }

    @Test fun longRepeatedDragsStayWithinBothEdgeLimits() {
        for (direction in listOf(-1f, 1f)) {
            val effect = EtoileOverscrollEffect(16f, 24f)
            repeat(1_000) {
                val delta = Offset(500f * direction, 1_000f * direction)
                assertEquals(delta, effect.applyToScroll(delta, NestedScrollSource.UserInput) { Offset.Zero })
                assertTrue(kotlin.math.abs(effect.displacement.x) <= 16f)
                assertTrue(kotlin.math.abs(effect.displacement.y) <= 24f)
            }
            assertTrue(effect.isInProgress)
        }
    }

    @Test fun resistanceIncreasesAsTheEdgeIsPulledFurther() {
        var offset = 0f
        var previousStep = Float.MAX_VALUE
        repeat(100) {
            val next = resistedOverscroll(offset, 10f, 24f)
            val step = next - offset
            assertTrue(step > 0f)
            assertTrue(step < previousStep)
            assertTrue(next < 24f)
            previousStep = step
            offset = next
        }
    }

    @Test fun reversingAfterAHugePullHasNoHiddenDragDeadZone() {
        val effect = EtoileOverscrollEffect(16f, 24f)
        effect.applyToScroll(Offset(0f, 100_000f), NestedScrollSource.UserInput) { Offset.Zero }
        val displacement = effect.displacement.y
        var forwarded = Offset.Zero
        val consumed = effect.applyToScroll(Offset(0f, -40f), NestedScrollSource.UserInput) {
            forwarded = it
            it
        }
        assertEquals(-40f + displacement, forwarded.y, 0.001f)
        assertEquals(Offset(0f, -40f), consumed)
        assertFalse(effect.isInProgress)
    }

    @Test fun partiallyConsumedScrollUsesOnlyTheRemainderForFeedback() {
        val effect = EtoileOverscrollEffect(16f, 24f)
        val consumed = effect.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput) { Offset(0f, 90f) }
        assertEquals(Offset(0f, 100f), consumed)
        assertEquals(resistedOverscroll(0f, 10f, 24f), effect.displacement.y, 0.001f)
        assertEquals(0f, effect.displacement.x, 0f)
    }

    @Test fun parentRefreshConsumptionDoesNotAlsoPullTheList() {
        val effect = EtoileOverscrollEffect(16f, 24f)
        effect.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput) { it }
        assertFalse(effect.isInProgress)
    }

    @Test fun flingAndProgrammaticDeltasDoNotAccumulateOverscroll() {
        val effect = EtoileOverscrollEffect(16f, 24f)
        var calls = 0
        val consumed = effect.applyToScroll(Offset(0f, -1_000f), NestedScrollSource.SideEffect) {
            calls++
            Offset.Zero
        }
        assertEquals(1, calls)
        assertEquals(Offset.Zero, consumed)
        assertFalse(effect.isInProgress)
    }

    @Test fun aZeroSizedAxisDoesNotSwallowItsUnconsumedDelta() {
        val effect = EtoileOverscrollEffect(0f, 24f)
        val consumed = effect.applyToScroll(Offset(80f, 80f), NestedScrollSource.UserInput) { Offset.Zero }
        assertEquals(Offset(0f, 80f), consumed)
        assertEquals(0f, effect.displacement.x, 0f)
    }

    @Test fun eachScrollableHasItsOwnIndependentEffect() {
        val factory = EtoileOverscrollFactory(16f, 24f)
        val first = factory.createOverscrollEffect()
        val second = factory.createOverscrollEffect()
        first.applyToScroll(Offset(100f, 0f), NestedScrollSource.UserInput) { Offset.Zero }
        assertTrue(first.isInProgress)
        assertFalse(second.isInProgress)
        assertNotSame(first.node, second.node)
    }

    @Test fun anOutwardFlingDoesNotKickTheEdgeFurtherAndFinishesWhenDetached() = runTest {
        val effect = EtoileOverscrollEffect(16f, 24f)
        effect.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput) { Offset.Zero }
        var calls = 0
        effect.applyToFling(Velocity(0f, 10_000f)) {
            calls++
            assertEquals(Velocity.Zero, it)
            it
        }
        assertEquals(1, calls)
        assertFalse(effect.isInProgress)
    }
}
