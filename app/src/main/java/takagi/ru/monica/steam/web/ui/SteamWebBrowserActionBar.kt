package takagi.ru.monica.steam.web.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.web.domain.SteamWebBrowserState
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionActionBarAction

@Composable
internal fun SteamWebBrowserActionBar(
    state: SteamWebBrowserState,
    controller: SteamWebBrowserController,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    SelectionActionBar(
        modifier = modifier,
        selectedCount = 0,
        onExit = onClose,
        onSelectAll = {},
        showSelectionControls = false,
        actions = listOf(
            SelectionActionBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.steam_web_back),
                enabled = state.canGoBack,
                onClick = controller::goBack
            ),
            SelectionActionBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.steam_web_forward),
                enabled = state.canGoForward,
                onClick = controller::goForward
            ),
            SelectionActionBarAction(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.steam_web_refresh),
                enabled = controller.attached,
                onClick = controller::reload
            ),
            SelectionActionBarAction(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.steam_web_share),
                enabled = state.currentUrl.isNotBlank(),
                onClick = onShare
            ),
            SelectionActionBarAction(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.steam_web_open_external),
                enabled = state.currentUrl.isNotBlank(),
                onClick = onOpenExternal
            )
        ),
        exitContentDescription = stringResource(R.string.steam_web_return_to_monica),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
    )
}
