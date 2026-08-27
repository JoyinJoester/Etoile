package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun CommunityUnlockActions(
    onOpenStore: () -> Unit,
    onOpenRules: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 330.dp || fontScale > 1.10f
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityOpenStoreButton(onClick = onOpenStore, modifier = Modifier.fillMaxWidth())
                CommunityRulesButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityOpenStoreButton(onClick = onOpenStore, modifier = Modifier.weight(1f))
                CommunityRulesButton(onClick = onOpenRules, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CommunityOpenStoreButton(onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Default.Storefront, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.steam_community_unlock_open_store))
    }
}

@Composable
private fun CommunityRulesButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Default.Info, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.steam_community_unlock_rules))
    }
}
