package takagi.ru.monica.steam.links.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.ui.screens.SettingsItem

@Composable
internal fun SteamLinkHandlingSettingsEntry() {
    val context = LocalContext.current
    SettingsItem(
        icon = Icons.Default.Link,
        title = stringResource(R.string.steam_link_handling_title),
        subtitle = stringResource(R.string.steam_link_handling_description),
        onClick = { openSteamLinkHandlingSettings(context) }
    )
}

internal fun openSteamLinkHandlingSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }.forContext(context)
    if (runCatching { context.startActivity(primary) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                .forContext(context)
        )
    }
}

private fun Intent.forContext(context: Context): Intent = apply {
    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
