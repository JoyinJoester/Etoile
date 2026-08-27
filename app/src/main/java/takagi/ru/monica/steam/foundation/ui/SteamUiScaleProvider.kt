package takagi.ru.monica.steam.foundation.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import takagi.ru.monica.data.InterfaceScale

private val LocalSteamUiScale = staticCompositionLocalOf {
    InterfaceScale.DEFAULT_PERCENT
}

internal val LocalSteamUiChromeDensity = staticCompositionLocalOf<Density?> {
    null
}

internal fun ComponentActivity.setSteamUiScaledContent(content: @Composable () -> Unit) {
    setContent {
        ProvideSteamUiScale(content)
    }
}

@Composable
internal fun ProvideSteamUiScale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { SteamUiScalePreferences(context) }
    val scalePercent by preferences.scale.collectAsState(
        initial = InterfaceScale.DEFAULT_PERCENT
    )
    val baseDensity = LocalDensity.current
    val appDensity = remember(baseDensity.density, baseDensity.fontScale, scalePercent) {
        Density(
            density = InterfaceScale.calculateDensity(baseDensity.density, scalePercent),
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(
        LocalDensity provides appDensity,
        LocalSteamUiScale provides scalePercent,
        LocalSteamUiChromeDensity provides appDensity
    ) {
        ProvideSteamAvatarShape(content)
    }
}

/**
 * Keeps high-density page content readable when the large scale is selected.
 * Navigation surfaces intentionally stay outside this provider so their touch
 * targets can retain the user's preferred larger size.
 */
@Composable
internal fun ProvideSteamContentDensity(content: @Composable () -> Unit) {
    val scalePercent = LocalSteamUiScale.current
    val appDensity = LocalDensity.current
    val contentDensity = remember(appDensity.density, appDensity.fontScale, scalePercent) {
        Density(
            density = calculateSteamContentDensity(appDensity.density, scalePercent),
            fontScale = appDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides contentDensity) {
        content()
    }
}

internal fun calculateSteamContentDensity(
    scaledDensity: Float,
    scalePercent: Int
): Float {
    val normalizedPercent = InterfaceScale.normalizePercent(scalePercent)
    val scaleFactor = normalizedPercent / InterfaceScale.DEFAULT_PERCENT.toFloat()
    val contentFactor = scaleFactor.coerceAtMost(1f)
    return (scaledDensity / scaleFactor * contentFactor).coerceAtLeast(0.1f)
}
