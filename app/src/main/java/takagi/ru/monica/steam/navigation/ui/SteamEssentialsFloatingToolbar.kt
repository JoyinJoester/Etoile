package takagi.ru.monica.steam.navigation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.dockSwipeTarget
import takagi.ru.monica.ui.LocalReduceAnimations
import kotlin.math.abs

/**
 * Safe space for the final scroll item and controls that must sit above the
 * floating Dock. Never apply this to the page root: page backgrounds and
 * scrolling content are intentionally allowed to draw behind the Dock.
 */
internal val SteamDockContentClearance = 104.dp

/** Zero outside Dock pages and while the full-screen chat thread is open. */
internal val LocalSteamDockContentClearance = staticCompositionLocalOf { 0.dp }

private const val REDUCED_MOTION_DURATION_MILLIS = 120

/** Moves only floating/fixed actions above the Dock without shrinking the page. */
@Composable
internal fun Modifier.steamDockActionClearance(extraBottomSpacing: Dp = 0.dp): Modifier =
    padding(bottom = LocalSteamDockContentClearance.current + extraBottomSpacing)

internal data class SteamToolbarItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val hasBadge: Boolean = false
)

/**
 * Handles page switching only while the pointer is inside the floating Dock.
 * Content lists never receive this modifier, so their normal vertical and
 * horizontal gestures remain independent from top-level navigation.
 */
internal fun Modifier.steamDockSwipe(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    thresholdPx: Float,
    onSelected: (SteamDockTab) -> Unit
): Modifier = pointerInput(order, selected, thresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        val touchSlop = viewConfiguration.touchSlop
        var horizontalLocked = false
        var gestureCompleted = false
        var totalDrag = 0f

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val drag = change.position - down.position
            val horizontalDistance = abs(drag.x)
            val verticalDistance = abs(drag.y)
            totalDrag = drag.x

            if (!horizontalLocked) {
                if (verticalDistance > touchSlop && verticalDistance >= horizontalDistance) {
                    return@awaitEachGesture
                }
                if (horizontalDistance > touchSlop && horizontalDistance > verticalDistance) {
                    horizontalLocked = true
                }
            }

            if (horizontalLocked) {
                // Initial pass lets the Dock win horizontal drags while the
                // child IconButtons keep ordinary taps.
                change.consume()
            }

            if (!change.pressed) {
                gestureCompleted = true
                break
            }
        }

        if (horizontalLocked && gestureCompleted) {
            dockSwipeTarget(
                order = order,
                selected = selected,
                totalDragPx = totalDrag,
                thresholdPx = thresholdPx
            )?.let(onSelected)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamEssentialsFloatingToolbar(
    modifier: Modifier = Modifier,
    items: List<SteamToolbarItem>,
    selectedIndex: Int = -1,
    floatingActionButton: (@Composable () -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    expanded: Boolean = true
) {
    val reduceAnimations = LocalReduceAnimations.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = configuration.screenWidthDp
    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && items.size > 3)

    HorizontalFloatingToolbar(
        modifier = modifier
            .steamWindowBottomPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 0.dp),
        expanded = expanded,
        floatingActionButton = floatingActionButton ?: {},
        scrollBehavior = scrollBehavior,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            val itemWidth by animateDpAsState(
                targetValue = if (expanded || isSelected) 48.dp else 0.dp,
                animationSpec = if (reduceAnimations) {
                    tween<Dp>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                },
                label = "steam_toolbar_item_width_$index"
            )
            val labelWidth by animateDpAsState(
                targetValue = if (isSelected && !shouldHideLabel) 80.dp else 0.dp,
                animationSpec = if (reduceAnimations) {
                    tween<Dp>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                },
                label = "steam_toolbar_label_width_$index"
            )
            val spacerWidth by animateDpAsState(
                targetValue = if (index < items.lastIndex) 8.dp else 0.dp,
                animationSpec = if (reduceAnimations) {
                    tween<Dp>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                },
                label = "steam_toolbar_spacer_width_$index"
            )

            if (itemWidth > 0.dp || isSelected) {
                IconButton(
                    onClick = item.onClick,
                    modifier = Modifier
                        .width(itemWidth + labelWidth)
                        .height(48.dp),
                    colors = if (isSelected) {
                        IconButtonDefaults.filledIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                .copy(alpha = 0.76f)
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = Color.Transparent
                        )
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        if (isSelected && !shouldHideLabel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }

                if (index < items.lastIndex) {
                    Spacer(modifier = Modifier.width(spacerWidth))
                }
            }
        }
    }
}
