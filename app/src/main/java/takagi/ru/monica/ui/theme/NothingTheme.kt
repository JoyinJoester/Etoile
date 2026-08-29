@file:OptIn(ExperimentalTextApi::class)

package takagi.ru.monica.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R

// ============================================
// ⬛ Nothing 风格 - 单色工业设计语言
// Dark: OLED 黑底仪表盘 / Light: 暖白纸面技术手册
// 规范来源: nothing-design skill (accent #D71921)
// ============================================

// 强调与状态色（两种模式一致，红色是"事件"不是装饰）
val NothingAccent = Color(0xFFD71921)
val NothingSuccess = Color(0xFF4A9E5C)
val NothingWarning = Color(0xFFD4A843)
val NothingInteractiveDark = Color(0xFF5B9BF6)
val NothingInteractiveLight = Color(0xFF007AFF)

val NothingDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF000000),

    secondary = Color(0xFF999999),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE8E8E8),

    tertiary = NothingInteractiveDark,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1A1A1A),
    onTertiaryContainer = NothingInteractiveDark,

    error = NothingAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2B0507),
    onErrorContainer = Color(0xFFF2B8B5),

    background = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF999999),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),

    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF222222),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF000000),
    scrim = Color(0xFF000000)
)

val NothingLightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCCCCC),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFFFFFFFF),

    secondary = Color(0xFF666666),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF1A1A1A),

    tertiary = NothingInteractiveLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = NothingInteractiveLight,

    error = NothingAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADEDE),
    onErrorContainer = Color(0xFF7A0C11),

    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    surfaceDim = Color(0xFFDDDDDD),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8),

    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE8E8E8),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF5F5F5),
    scrim = Color(0xFF000000)
)

// 卡片不超过 16px 圆角；按钮走药丸或 4-8px 技术圆角
val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

// Doto = 点阵显示字体（NDot57 的替代），ROND=100 圆点
val NothingDotMatrixFamily = FontFamily(
    Font(
        R.font.doto_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        R.font.doto_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

val NothingGroteskFamily = FontFamily(
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val NothingMonoFamily = FontFamily(
    Font(R.font.space_mono_regular, weight = FontWeight.Normal),
    Font(R.font.space_mono_bold, weight = FontWeight.Bold)
)

// 层级: display(Doto 点阵) > heading/body(Space Grotesk) > label(Space Mono 大写)
val NothingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NothingDotMatrixFamily, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NothingDotMatrixFamily, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.9).sp
    ),
    displaySmall = TextStyle(
        fontFamily = NothingDotMatrixFamily, fontWeight = FontWeight.Medium,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NothingGroteskFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NothingMonoFamily, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NothingMonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NothingMonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.9.sp,
    )
)
