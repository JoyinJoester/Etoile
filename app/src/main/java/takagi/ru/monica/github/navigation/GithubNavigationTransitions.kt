package takagi.ru.monica.github.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection

/**
 * Forward and back transitions for the GitHub navigation graph.
 *
 * Opaque surfaces share one timing curve. Keeping text at its final scale and avoiding whole-page
 * alpha animation removes the disappearing-content gap and extra offscreen blending during motion.
 */
object GithubNavigationTransitions {
    internal val slideSpec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)

    internal fun foregroundOffset(width: Int, direction: LayoutDirection): Int =
        if (direction == LayoutDirection.Ltr) width else -width

    internal fun backgroundOffset(width: Int, direction: LayoutDirection): Int =
        -foregroundOffset(width, direction) / 5

    fun enter(direction: LayoutDirection = LayoutDirection.Ltr): EnterTransition = slideInHorizontally(
        animationSpec = slideSpec,
        initialOffsetX = { foregroundOffset(it, direction) }
    )

    fun exit(direction: LayoutDirection = LayoutDirection.Ltr): ExitTransition = slideOutHorizontally(
        animationSpec = slideSpec,
        targetOffsetX = { backgroundOffset(it, direction) }
    )

    fun popEnter(direction: LayoutDirection = LayoutDirection.Ltr): EnterTransition = slideInHorizontally(
        animationSpec = slideSpec,
        initialOffsetX = { backgroundOffset(it, direction) }
    )

    fun popExit(direction: LayoutDirection = LayoutDirection.Ltr): ExitTransition = slideOutHorizontally(
        animationSpec = slideSpec,
        targetOffsetX = { foregroundOffset(it, direction) }
    )

    // Tabs share a fixed viewport, so they crossfade without an additional layout/slide animation.
    fun tabEnter(): EnterTransition = fadeIn(tween(180, easing = LinearEasing))
    fun tabExit(): ExitTransition = fadeOut(tween(180, easing = LinearEasing))
}
