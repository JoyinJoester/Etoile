/*
 * Backdrop composition follows the compose-miuix-ui and KernelSU liquid-glass
 * examples. Miuix is Apache-2.0; KernelSU is GPL-3.0.
 */
package takagi.ru.monica.steam.navigation.liquidglass.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Stable
internal class SteamLiquidGlassBackdrop internal constructor(
    internal val delegate: LayerBackdrop
)

@Composable
internal fun rememberSteamLiquidGlassBackdrop(): SteamLiquidGlassBackdrop {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { SteamLiquidGlassBackdrop(backdrop) }
}

internal fun Modifier.steamLiquidGlassBackdropSource(
    backdrop: SteamLiquidGlassBackdrop,
    enabled: Boolean
): Modifier = if (enabled) layerBackdrop(backdrop.delegate) else this

internal fun isSteamLiquidGlassRuntimeSupported(): Boolean = isRuntimeShaderSupported()

@Stable
private class CombinedBackdrop(
    private val first: Backdrop,
    private val second: Backdrop
) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
internal fun rememberSteamCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
