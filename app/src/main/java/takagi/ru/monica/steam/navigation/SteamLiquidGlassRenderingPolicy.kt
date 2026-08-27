package takagi.ru.monica.steam.navigation

/**
 * Runtime backdrop shaders cannot safely sample Android platform surfaces
 * such as WebView on every GPU. Fullscreen browser surfaces hide the dock;
 * this guard also disables runtime effects for any remaining platform view.
 */
internal fun shouldEnableSteamLiquidGlassRuntimeEffects(
    dockStyle: SteamDockStyle,
    dockVisible: Boolean,
    platformViewActive: Boolean
): Boolean = dockStyle == SteamDockStyle.LIQUID_GLASS &&
    dockVisible &&
    !platformViewActive

internal fun shouldShowSteamDock(
    hasConfiguration: Boolean,
    isDockPage: Boolean,
    chatThreadOpen: Boolean,
    platformViewActive: Boolean,
    imeVisible: Boolean
): Boolean = hasConfiguration &&
    isDockPage &&
    !chatThreadOpen &&
    !platformViewActive &&
    !imeVisible
