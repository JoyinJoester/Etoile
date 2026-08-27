package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val LocalSteamAvatarShape = staticCompositionLocalOf<Shape> {
    RectangleShape
}

/** Shape for avatars that are rendered beneath a Steam avatar-frame overlay. */
internal val LocalSteamAvatarFrameShape = staticCompositionLocalOf<Shape> {
    RectangleShape
}

@Composable
internal fun ProvideSteamAvatarShape(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { SteamAvatarShapePreferences(context) }
    val plainOption by preferences.plainShape.collectAsState(
        initial = SteamAvatarShapeOption.SQUARE
    )
    val framedOption by preferences.framedShape.collectAsState(
        initial = SteamAvatarShapeOption.SQUARE
    )

    CompositionLocalProvider(
        LocalSteamAvatarShape provides plainOption.steamAvatarShape(),
        LocalSteamAvatarFrameShape provides framedOption.steamAvatarShape(),
        content = content
    )
}

internal fun SteamAvatarShapeOption.steamAvatarShape(): Shape = when (this) {
    SteamAvatarShapeOption.SQUARE -> RectangleShape
    SteamAvatarShapeOption.ROUNDED -> RoundedCornerShape(12.dp)
    SteamAvatarShapeOption.CIRCLE -> CircleShape
}
