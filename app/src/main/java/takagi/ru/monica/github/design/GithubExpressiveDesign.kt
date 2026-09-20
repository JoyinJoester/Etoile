package takagi.ru.monica.github.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.DesignStyle

/**
 * 当前设计风格。EtoileTheme 负责提供，组件据此切换圆角/动效等观感。
 * 没有 Provider 时退回 MATERIAL，预览和测试可以直接用。
 */
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.MATERIAL }

/**
 * 圆角令牌。Nothing 是单色工业风，圆角上限 16dp，所以整体收紧；
 * 其余风格保留原本的大圆角。
 */
object GithubExpressiveShapes {
    val compact: RoundedCornerShape
        @Composable get() = if (isNothingStyle()) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)
    val control: RoundedCornerShape
        @Composable get() = if (isNothingStyle()) RoundedCornerShape(8.dp) else RoundedCornerShape(16.dp)
    val container: RoundedCornerShape
        @Composable get() = if (isNothingStyle()) RoundedCornerShape(12.dp) else RoundedCornerShape(24.dp)
    val prominent: RoundedCornerShape
        @Composable get() = if (isNothingStyle()) RoundedCornerShape(16.dp) else RoundedCornerShape(32.dp)
}

@Composable
private fun isNothingStyle() = LocalDesignStyle.current == DesignStyle.NOTHING

/** Nothing 追求机械般的直接反馈，动效更短、线性收尾；其余风格保持原节奏。 */
object GithubExpressiveMotion {
    const val quick = 160
    const val standard = 240
    const val emphasized = 360

    @Composable
    fun <T> quickTween() = tween<T>(durationMillis = if (isNothingStyle()) 90 else quick)

    @Composable
    fun <T> standardTween() = tween<T>(durationMillis = if (isNothingStyle()) 140 else standard)

    @Composable
    fun <T> expressiveSpring() = if (isNothingStyle()) {
        spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    } else {
        spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
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
    val detailWorkspaceWidth = 960.dp
    val desktopNavigationWidth = 1200.dp
    val contentMaxWidth = 840.dp
    val wideContentMaxWidth = 1200.dp
    val codeContentMaxWidth = 1440.dp
    val formMaxWidth = 640.dp
    val navigationRailWidth = 96.dp
    val compactHorizontalPadding = 16.dp
    val expandedHorizontalPadding = 32.dp
}

object GithubExpressiveSizes {
    val minimumTouchTarget = 48.dp
    val standardIcon = 24.dp
}
