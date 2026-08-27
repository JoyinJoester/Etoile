/*
 * Motion model adapted from KernelSU FloatingBottomBar and the BiliPai
 * KernelSU-aligned Dock implementation. KernelSU is GPL-3.0; Etoile is
 * distributed under the same license.
 */
package takagi.ru.monica.steam.navigation.liquidglass.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal data class LiquidGlassDockDragSpec(
    val baseResistance: Float = 1.02f,
    val overscrollResistance: Float = 0.34f,
    val overscrollLimitItems: Float = 0.5f,
    val flingProjectionTimeSeconds: Float = 0.20f,
    val maxReleaseStepCount: Int = 1
)

internal fun resolveLiquidGlassDockVelocityItemsPerSecond(
    velocityPxPerSecond: Float,
    itemWidthPx: Float
): Float = if (itemWidthPx > 0f) velocityPxPerSecond / itemWidthPx else 0f

internal fun resolveLiquidGlassDockReleaseTargetIndex(
    currentValue: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec()
): Int {
    if (itemCount <= 0) return 0
    val projectedValue = currentValue +
        resolveLiquidGlassDockVelocityItemsPerSecond(
            velocityPxPerSecond = velocityPxPerSecond,
            itemWidthPx = itemWidthPx
        ) * dragSpec.flingProjectionTimeSeconds
    var targetIndex = projectedValue.roundToInt()
    val baseIndex = currentValue.roundToInt()
    val maxStep = dragSpec.maxReleaseStepCount.coerceAtLeast(1)
    if (abs(targetIndex - baseIndex) > maxStep) {
        targetIndex = baseIndex + (targetIndex - baseIndex).sign * maxStep
    }
    return targetIndex.coerceIn(0, itemCount - 1)
}

internal class LiquidGlassDockMotionState internal constructor(
    initialIndex: Int,
    private val itemCount: Int,
    private val scope: CoroutineScope,
    private val onIndexChanged: (Int) -> Unit,
    reduceMotion: Boolean,
    private val dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec()
) {
    private val reducedMotionAnimationSpec: AnimationSpec<Float> =
        tween<Float>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
    private val valueAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val velocityAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.5f, 300f, 0.01f)
    }
    private val pressProgressAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val scaleXAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.6f, 250f, 0.001f)
    }
    private val scaleYAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.7f, 250f, 0.001f)
    }
    private val offsetSnapAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 300f, 0.5f)
    }

    private val valueAnimation = Animatable(initialIndex.toFloat(), 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val offsetAnimation = Animatable(0f)
    private val mutatorMutex = MutatorMutex()
    private val deformationVelocityTracker = VelocityTracker()

    private var motionGeneration = 0
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var releaseJob: Job? = null
    private var offsetJob: Job? = null
    private var desiredValue = initialIndex.toFloat()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val deformationVelocityItemsPerSecond: Float get() = velocityAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val dragOffset: Float get() = offsetAnimation.value
    val isRunning: Boolean get() = valueAnimation.isRunning

    var velocityPxPerSecond by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var targetIndex by mutableIntStateOf(initialIndex)
        private set

    private fun startNewMotion(): Int {
        motionGeneration += 1
        return motionGeneration
    }

    private fun press() {
        deformationVelocityTracker.resetTracking()
        releaseJob?.cancel()
        releaseJob = scope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleYAnimationSpec) }
        }
    }

    private fun release(onSettled: (() -> Unit)? = null) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = ((itemCount - 1).toFloat() * 0.025f).coerceAtLeast(0.001f)
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            onSettled?.invoke()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    private fun updateDeformationVelocity(position: Float) {
        val valueRange = (itemCount - 1).toFloat().coerceAtLeast(1f)
        deformationVelocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(position, 0f)
        )
        val targetVelocity = deformationVelocityTracker.calculateVelocity().x / valueRange
        velocityJob = scope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }

    private fun animateToValue(value: Float, onSettled: (() -> Unit)? = null) {
        scope.launch {
            mutatorMutex.mutate {
                press()
                val nextTarget = value.fastCoerceIn(0f, (itemCount - 1).toFloat())
                targetIndex = nextTarget.roundToInt().coerceIn(0, itemCount - 1)
                valueJob?.cancel()
                valueJob = launch { valueAnimation.animateTo(nextTarget, valueAnimationSpec) }
                if (deformationVelocityItemsPerSecond != 0f) {
                    velocityJob?.cancel()
                    velocityJob = launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release(onSettled)
            }
        }
    }

    fun onDrag(
        dragAmountPx: Float,
        itemWidthPx: Float,
        gestureVelocityPxPerSecond: Float = 0f
    ) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        if (!isDragging) {
            isDragging = true
            startNewMotion()
            valueJob?.cancel()
            offsetJob?.cancel()
            desiredValue = valueAnimation.value
            velocityPxPerSecond = 0f
            velocityJob?.cancel()
            velocityJob = scope.launch { velocityAnimation.snapTo(0f) }
            press()
        }
        velocityPxPerSecond = gestureVelocityPxPerSecond

        val isOverscrolling = desiredValue < 0f || desiredValue > (itemCount - 1).toFloat()
        val resistance = if (isOverscrolling) {
            dragSpec.overscrollResistance
        } else {
            dragSpec.baseResistance
        }
        desiredValue = (desiredValue + dragAmountPx / itemWidthPx * resistance).fastCoerceIn(
            -dragSpec.overscrollLimitItems,
            (itemCount - 1).toFloat() + dragSpec.overscrollLimitItems
        )

        val clampedValue = desiredValue.fastCoerceIn(0f, (itemCount - 1).toFloat())
        valueJob?.cancel()
        valueJob = scope.launch {
            valueAnimation.snapTo(clampedValue)
            updateDeformationVelocity(clampedValue)
        }

        offsetJob?.cancel()
        offsetJob = scope.launch {
            offsetAnimation.snapTo(offsetAnimation.value + dragAmountPx)
        }
    }

    fun setPressed(pressed: Boolean) {
        if (pressed) {
            press()
        } else if (!isDragging) {
            release()
        }
    }

    fun onDragEnd(velocityX: Float, itemWidthPx: Float) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        isDragging = false
        val generation = motionGeneration
        velocityPxPerSecond = velocityX
        val releaseTargetIndex = resolveLiquidGlassDockReleaseTargetIndex(
            currentValue = desiredValue,
            velocityPxPerSecond = velocityX,
            itemWidthPx = itemWidthPx,
            itemCount = itemCount,
            dragSpec = dragSpec
        )
        targetIndex = releaseTargetIndex
        desiredValue = releaseTargetIndex.toFloat()
        animateToValue(releaseTargetIndex.toFloat()) {
            if (generation == motionGeneration) {
                velocityPxPerSecond = 0f
                onIndexChanged(releaseTargetIndex)
            }
        }
        offsetJob?.cancel()
        offsetJob = scope.launch {
            offsetAnimation.animateTo(0f, offsetSnapAnimationSpec)
        }
    }

    fun updateIndex(index: Int) {
        if (isDragging || itemCount <= 0) return
        val safeIndex = index.coerceIn(0, itemCount - 1)
        if (
            safeIndex == targetIndex &&
            (
                isRunning ||
                    abs(value - safeIndex.toFloat()) < 0.005f ||
                    abs(targetValue - safeIndex.toFloat()) < 0.005f
                )
        ) {
            return
        }
        startNewMotion()
        targetIndex = safeIndex
        desiredValue = safeIndex.toFloat()
        velocityPxPerSecond = 0f
        animateToValue(safeIndex.toFloat())
    }
}

private const val KERNEL_SU_PRESSED_SCALE = 78f / 56f
private const val REDUCED_MOTION_DURATION_MILLIS = 120

@Composable
internal fun rememberLiquidGlassDockMotionState(
    initialIndex: Int,
    itemCount: Int,
    reduceMotion: Boolean,
    onIndexChanged: (Int) -> Unit
): LiquidGlassDockMotionState {
    val scope = rememberCoroutineScope()
    val currentOnIndexChanged by rememberUpdatedState(onIndexChanged)
    return remember(itemCount, reduceMotion) {
        LiquidGlassDockMotionState(
            initialIndex = initialIndex,
            itemCount = itemCount,
            scope = scope,
            onIndexChanged = { currentOnIndexChanged(it) },
            reduceMotion = reduceMotion
        )
    }
}

internal fun Modifier.liquidGlassDockHorizontalDrag(
    motionState: LiquidGlassDockMotionState,
    itemWidthPx: Float
): Modifier = pointerInput(motionState, itemWidthPx) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            motionState.onDrag(over, itemWidthPx)
        }

        if (dragStart != null) {
            velocityTracker.addPosition(dragStart.uptimeMillis, dragStart.position)
            var cancelled = false
            try {
                horizontalDrag(dragStart.id) { change ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val dragAmount = change.position.x - change.previousPosition.x
                    motionState.onDrag(
                        dragAmountPx = dragAmount,
                        itemWidthPx = itemWidthPx,
                        gestureVelocityPxPerSecond = velocityTracker.calculateVelocity().x
                    )
                }
            } catch (_: Exception) {
                cancelled = true
            }
            motionState.onDragEnd(
                velocityX = if (cancelled) 0f else velocityTracker.calculateVelocity().x,
                itemWidthPx = itemWidthPx
            )
        }
    }
}
