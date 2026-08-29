package takagi.ru.monica.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme as MaterialColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// MIUI 标志性的超椭圆圆角（squircle），基于 miuix-squircle 的路径算法。
// 用路径而非着色器实现，任意 API 级别都生效；四角始终均匀。
class SquircleShape(all: Dp) : CornerBasedShape(
    topStart = CornerSize(all),
    topEnd = CornerSize(all),
    bottomEnd = CornerSize(all),
    bottomStart = CornerSize(all)
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val maxRadius = minOf(size.width, size.height) / 2f
        val radius = topStart.coerceIn(0f, maxRadius)
        return Outline.Generic(
            Path().apply {
                if (radius <= 0f) {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                } else {
                    addSquircleRect(size.width, size.height, radius)
                }
            }
        )
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

// Miuix 设计作用于 M3 全局的形状：HyperOS 式 squircle 大圆角
val MiuixStyleShapes = Shapes(
    extraSmall = SquircleShape(8.dp),
    small = SquircleShape(16.dp),
    medium = SquircleShape(20.dp),
    large = SquircleShape(24.dp),
    extraLarge = SquircleShape(28.dp)
)

// 将当前 M3 色板映射为 miuix 色板，让 miuix 组件跟随全局主题
// （包括 Nothing 单色风）。未映射的字段保留 miuix 默认值。
fun MaterialColorScheme.toMiuixColors(darkTheme: Boolean): Colors {
    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryVariant = primaryContainer,
            onPrimaryVariant = onPrimaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryVariant = secondaryContainer,
            onSecondaryVariant = onSecondaryContainer,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            onBackgroundVariant = onSurfaceVariant,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceSecondary = onSurfaceVariant,
            onSurfaceVariantSummary = onSurfaceVariant,
            onSurfaceVariantActions = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            onSurfaceContainer = onSurface,
            onSurfaceContainerVariant = onSurfaceVariant,
            surfaceContainerHigh = surfaceContainerHigh,
            onSurfaceContainerHigh = onSurface,
            surfaceContainerHighest = surfaceContainerHighest,
            onSurfaceContainerHighest = onSurface,
            outline = outline,
            dividerLine = outlineVariant,
            windowDimming = scrim
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryVariant = primaryContainer,
            onPrimaryVariant = onPrimaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryVariant = secondaryContainer,
            onSecondaryVariant = onSecondaryContainer,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            onBackgroundVariant = onSurfaceVariant,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceSecondary = onSurfaceVariant,
            onSurfaceVariantSummary = onSurfaceVariant,
            onSurfaceVariantActions = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            onSurfaceContainer = onSurface,
            onSurfaceContainerVariant = onSurfaceVariant,
            surfaceContainerHigh = surfaceContainerHigh,
            onSurfaceContainerHigh = onSurface,
            surfaceContainerHighest = surfaceContainerHighest,
            onSurfaceContainerHighest = onSurface,
            outline = outline,
            dividerLine = outlineVariant,
            windowDimming = scrim
        )
    }
}
