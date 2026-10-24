package takagi.ru.monica.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Limits are density-scaled pixels, independent of the window or device size. */
internal data class EtoileOverscrollFactory(
    val horizontalLimit: Float,
    val verticalLimit: Float
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        EtoileOverscrollEffect(horizontalLimit, verticalLimit)
}

/** Increasing resistance with no hidden drag accumulation once the visible edge is reached. */
internal fun resistedOverscroll(offset: Float, delta: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val distance = abs(offset).coerceAtMost(limit)
    val room = limit - distance
    val pull = abs(delta) * 0.3f
    if (room == 0f || pull == 0f) return offset.coerceIn(-limit, limit)
    return (offset + delta.sign * room * (pull / (limit + pull))).coerceIn(-limit, limit)
}

/** Reverse drags release the visible displacement first, then reach the scrollable immediately. */
internal fun overscrollRelease(offset: Float, delta: Float): Float =
    if (offset * delta < 0f) delta.sign * min(abs(offset), abs(delta)) else 0f

internal class EtoileOverscrollEffect(
    private val horizontalLimit: Float,
    private val verticalLimit: Float
) : OverscrollEffect {
    private val renderer = EdgeNode()
    override val node: Modifier.Node get() = renderer
    private var settleJob: Job? = null
    private var limitX = horizontalLimit
    private var limitY = verticalLimit
    internal var displacement = Offset.Zero
        private set(value) {
            if (field == value) return
            field = value
            if (renderer.isAttached) renderer.invalidatePlacement()
        }

    override val isInProgress: Boolean
        get() = displacement != Offset.Zero

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        // Wheel, keyboard and fling deltas keep their normal bounds and never build up tension.
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
        settleJob?.cancel()
        val released = Offset(
            overscrollRelease(displacement.x, delta.x),
            overscrollRelease(displacement.y, delta.y)
        )
        displacement += released
        val available = delta - released
        // Includes nested scrolling, so a parent's pull-to-refresh can consume its gesture first.
        val consumed = performScroll(available)
        val remainder = available - consumed
        displacement = Offset(
            resistedOverscroll(displacement.x, remainder.x, limitX),
            resistedOverscroll(displacement.y, remainder.y, limitY)
        )
        return released + consumed + Offset(
            if (limitX > 0f) remainder.x else 0f,
            if (limitY > 0f) remainder.y else 0f
        )
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        settleJob?.cancel()
        // An outward release settles the small existing displacement without a velocity kick.
        performFling(Velocity(
            if (displacement.x * velocity.x > 0f) 0f else velocity.x,
            if (displacement.y * velocity.y > 0f) 0f else velocity.y
        ))
        if (!isInProgress) return
        if (!renderer.isAttached) {
            displacement = Offset.Zero
            return
        }
        val start = displacement
        val job = renderer.coroutineScope.launch {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) { fraction, _ -> displacement = start * fraction.coerceIn(0f, 1f) }
            displacement = Offset.Zero
        }
        settleJob = job
        job.join()
    }

    private inner class EdgeNode : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            // Tiny chips and short panels get a smaller allowance than full-page lists.
            limitX = min(horizontalLimit, placeable.width * 0.08f)
            limitY = min(verticalLimit, placeable.height * 0.08f)
            displacement = Offset(displacement.x.coerceIn(-limitX, limitX), displacement.y.coerceIn(-limitY, limitY))
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    translationX = displacement.x
                    translationY = displacement.y
                    clip = true
                }
            }
        }

        override fun onDetach() {
            settleJob?.cancel()
            displacement = Offset.Zero
        }
    }
}
