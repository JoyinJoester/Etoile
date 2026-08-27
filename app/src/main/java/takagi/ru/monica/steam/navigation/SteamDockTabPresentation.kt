package takagi.ru.monica.steam.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R

internal fun SteamDockTab.icon(): ImageVector = when (this) {
    SteamDockTab.TOKEN -> Icons.Default.Security
    SteamDockTab.LIBRARY -> Icons.Default.SportsEsports
    SteamDockTab.STORE -> Icons.Default.Storefront
    SteamDockTab.CHAT -> Icons.Default.ChatBubble
    SteamDockTab.SETTINGS -> Icons.Default.Settings
}

@Composable
internal fun SteamDockTab.label(): String = when (this) {
    SteamDockTab.TOKEN -> stringResource(R.string.steam_dock_token)
    SteamDockTab.LIBRARY -> stringResource(R.string.steam_library_title)
    SteamDockTab.STORE -> stringResource(R.string.steam_store_title)
    SteamDockTab.CHAT -> stringResource(R.string.steam_chat_title)
    SteamDockTab.SETTINGS -> stringResource(R.string.settings_title)
}
