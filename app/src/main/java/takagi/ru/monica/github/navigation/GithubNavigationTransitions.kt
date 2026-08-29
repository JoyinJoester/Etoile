package takagi.ru.monica.github.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Forward and back transitions for the GitHub navigation graph.
 *
 * The incoming screen travels the full half-width while the outgoing one recedes a quarter, so the
 * pair reads as one surface sliding over another rather than two independent screens. Fades run at
 * half duration on the departing side to keep the outgoing content legible for most of the motion.
 */
object GithubNavigationTransitions {
    private const val DURATION_MILLIS = 450

    private val EmphasizedDecelerate = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun enter(): EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedDecelerate),
        initialOffsetX = { (it * PUSH_TRAVEL).toInt() }
    ) + scaleIn(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedDecelerate),
        initialScale = PUSH_SCALE
    ) + fadeIn(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedAccelerate)
    )

    fun exit(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedAccelerate),
        targetOffsetX = { -(it * PARALLAX_TRAVEL).toInt() }
    ) + fadeOut(
        animationSpec = tween(DURATION_MILLIS / 2, easing = EmphasizedAccelerate)
    )

    fun popEnter(): EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedDecelerate),
        initialOffsetX = { -(it * PARALLAX_TRAVEL).toInt() }
    ) + scaleIn(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedDecelerate),
        initialScale = PARALLAX_SCALE
    ) + fadeIn(
        animationSpec = tween(DURATION_MILLIS / 2, easing = EmphasizedDecelerate)
    )

    fun popExit(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedAccelerate),
        targetOffsetX = { (it * PUSH_TRAVEL).toInt() }
    ) + scaleOut(
        animationSpec = tween(DURATION_MILLIS, easing = EmphasizedAccelerate),
        targetScale = PUSH_SCALE
    ) + fadeOut(
        animationSpec = tween(DURATION_MILLIS / 2, easing = EmphasizedAccelerate)
    )

    private const val PUSH_TRAVEL = 0.5f
    private const val PARALLAX_TRAVEL = 0.25f
    private const val PUSH_SCALE = 0.92f
    private const val PARALLAX_SCALE = 0.95f
}
