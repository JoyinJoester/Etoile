package takagi.ru.monica.github.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object GithubExpressiveShapes {
    val compact = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(16.dp)
    val container = RoundedCornerShape(24.dp)
    val prominent = RoundedCornerShape(32.dp)
}

object GithubExpressiveMotion {
    const val quick = 160
    const val standard = 240
    const val emphasized = 360

    fun <T> quickTween() = tween<T>(durationMillis = quick)
    fun <T> standardTween() = tween<T>(durationMillis = standard)
    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

data class GithubSemanticColors(
    val review: Color,
    val mention: Color,
    val assigned: Color,
    val release: Color
)

@Composable
fun githubSemanticColors() = GithubSemanticColors(
    review = MaterialTheme.colorScheme.tertiary,
    mention = MaterialTheme.colorScheme.primary,
    assigned = MaterialTheme.colorScheme.error,
    release = MaterialTheme.colorScheme.secondary
)

object GithubAdaptiveLayout {
    val expandedWidth = 600.dp
    val detailTwoPaneWidth = 840.dp
    val contentMaxWidth = 920.dp
    val compactHorizontalPadding = 16.dp
    val expandedHorizontalPadding = 32.dp
}

object GithubExpressiveSizes {
    val minimumTouchTarget = 48.dp
    val standardIcon = 24.dp
}
