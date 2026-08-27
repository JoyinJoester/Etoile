package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleError
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleErrorReason
import takagi.ru.monica.ui.LocalReduceAnimations

@Composable
internal fun SteamHostsAdvancedEditor(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    hostCount: Int,
    error: SteamHostsRuleError?,
    hasChanges: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceAnimations = LocalReduceAnimations.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_network_optimization_advanced_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.steam_network_optimization_advanced_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (hasChanges) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Text(
                    text = stringResource(
                        if (hasChanges) {
                            R.string.steam_network_optimization_unsaved
                        } else {
                            R.string.steam_network_optimization_saved
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.steam_network_optimization_collapse_editor
                        } else {
                            R.string.steam_network_optimization_expand_editor
                        }
                    )
                )
            }
        }

        if (reduceAnimations) {
            if (expanded) {
                AdvancedEditorContent(
                    value = value,
                    onValueChange = onValueChange,
                    hostCount = hostCount,
                    error = error,
                    hasChanges = hasChanges,
                    onSave = onSave
                )
            }
        } else {
            AnimatedVisibility(visible = expanded) {
                AdvancedEditorContent(
                    value = value,
                    onValueChange = onValueChange,
                    hostCount = hostCount,
                    error = error,
                    hasChanges = hasChanges,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
private fun AdvancedEditorContent(
    value: String,
    onValueChange: (String) -> Unit,
    hostCount: Int,
    error: SteamHostsRuleError?,
    hasChanges: Boolean,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val supportingText = error?.let { hostsErrorText(it) }
        ?: stringResource(R.string.steam_network_optimization_hosts_helper)

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.steam_network_optimization_hosts_label)) },
                placeholder = {
                    Text(stringResource(R.string.steam_network_optimization_hosts_placeholder))
                },
                supportingText = { Text(supportingText) },
                isError = error != null,
                minLines = 7,
                maxLines = 14
            )
            Text(
                text = if (hostCount > 0 && error == null) {
                    context.getString(R.string.steam_network_optimization_hosts_ready, hostCount)
                } else {
                    context.getString(R.string.steam_network_optimization_hosts_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (error == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            FilledTonalButton(
                onClick = onSave,
                enabled = hasChanges && error == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.steam_network_optimization_hosts_save))
            }
        }
    }
}

@Composable
private fun hostsErrorText(error: SteamHostsRuleError): String {
    val stringId = when (error.reason) {
        SteamHostsRuleErrorReason.INVALID_FORMAT ->
            R.string.steam_network_optimization_error_format
        SteamHostsRuleErrorReason.INVALID_IP ->
            R.string.steam_network_optimization_error_ip
        SteamHostsRuleErrorReason.INVALID_HOSTNAME ->
            R.string.steam_network_optimization_error_hostname
        SteamHostsRuleErrorReason.UNUSABLE_ADDRESS ->
            R.string.steam_network_optimization_error_address
    }
    return LocalContext.current.getString(stringId, error.lineNumber)
}
