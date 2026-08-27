package takagi.ru.monica.steam.navigation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
internal data class SteamWindowChromeInsetsPx(
    val topPx: Int = 0,
    val bottomPx: Int = 0
)

internal fun reduceSteamWindowChromeInsets(
    previous: SteamWindowChromeInsetsPx,
    observedTopPx: Int,
    observedBottomPx: Int,
    fallbackTopPx: Int,
    fallbackBottomPx: Int,
    isInMultiWindowMode: Boolean
): SteamWindowChromeInsetsPx {
    val currentTop = observedTopPx.coerceAtLeast(0)
    val currentBottom = observedBottomPx.coerceAtLeast(0)
    if (!isInMultiWindowMode) {
        return SteamWindowChromeInsetsPx(
            topPx = currentTop,
            bottomPx = currentBottom
        )
    }

    return SteamWindowChromeInsetsPx(
        topPx = when {
            currentTop > 0 -> maxOf(currentTop, fallbackTopPx)
            previous.topPx > 0 -> previous.topPx
            else -> fallbackTopPx
        },
        bottomPx = when {
            currentBottom > 0 -> maxOf(currentBottom, fallbackBottomPx)
            previous.bottomPx > 0 -> previous.bottomPx
            else -> fallbackBottomPx
        }
    )
}

internal fun resolveSteamWindowBottomPaddingPx(
    insets: SteamWindowChromeInsetsPx,
    imeVisible: Boolean
): Int = if (imeVisible) 0 else insets.bottomPx

@Composable
internal fun Modifier.steamWindowTopPadding(): Modifier =
    windowInsetsPadding(rememberSteamWindowTopInsets())

@Composable
internal fun Modifier.steamWindowBottomPadding(
    suppressWhenImeVisible: Boolean = false
): Modifier = windowInsetsPadding(
    rememberSteamWindowBottomInsets(suppressWhenImeVisible)
)

@Composable
internal fun rememberSteamWindowBottomInsets(
    suppressWhenImeVisible: Boolean = false
): WindowInsets {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val stableInsets = rememberSteamWindowChromeInsetsPx()
    val imeVisible = suppressWhenImeVisible && WindowInsets.ime.getBottom(density) > 0
    val leftPx = safeDrawing.getLeft(density, layoutDirection)
    val rightPx = safeDrawing.getRight(density, layoutDirection)
    val bottomPx = resolveSteamWindowBottomPaddingPx(stableInsets, imeVisible)
    return remember(leftPx, rightPx, bottomPx) {
        WindowInsets(leftPx, 0, rightPx, bottomPx)
    }
}

@Composable
private fun rememberSteamWindowTopInsets(): WindowInsets {
    val topPx = rememberSteamWindowChromeInsetsPx().topPx
    return remember(topPx) { WindowInsets(0, topPx, 0, 0) }
}

@Composable
private fun rememberSteamWindowChromeInsetsPx(): SteamWindowChromeInsetsPx {
    LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val safeDrawing = WindowInsets.safeDrawing
    val isInMultiWindowMode = context.findActivity()?.isInMultiWindowMode == true
    val memory = remember(isInMultiWindowMode) { SteamWindowChromeInsetMemory() }
    val next = reduceSteamWindowChromeInsets(
        previous = memory.value,
        observedTopPx = safeDrawing.getTop(density),
        observedBottomPx = safeDrawing.getBottom(density),
        fallbackTopPx = with(density) { MULTI_WINDOW_TOP_FALLBACK.roundToPx() },
        fallbackBottomPx = with(density) { MULTI_WINDOW_BOTTOM_FALLBACK.roundToPx() },
        isInMultiWindowMode = isInMultiWindowMode
    )
    memory.value = next
    return next
}

private class SteamWindowChromeInsetMemory(
    var value: SteamWindowChromeInsetsPx = SteamWindowChromeInsetsPx()
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val MULTI_WINDOW_TOP_FALLBACK = 24.dp
private val MULTI_WINDOW_BOTTOM_FALLBACK = 12.dp
