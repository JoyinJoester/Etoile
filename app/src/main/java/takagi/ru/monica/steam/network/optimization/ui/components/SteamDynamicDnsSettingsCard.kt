package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun SteamDynamicDnsSettingsCard(
    enabled: Boolean,
    activeProviderCount: Int,
    cacheCount: Int,
    refreshing: Boolean,
    canEnable: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onForceRefresh: () -> Unit
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = contentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_network_dynamic_dns_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Text(
                        text = when {
                            !enabled -> stringResource(R.string.steam_network_dynamic_dns_disabled)
                            activeProviderCount > 0 -> stringResource(
                                R.string.steam_network_dynamic_dns_active,
                                activeProviderCount
                            )
                            else -> stringResource(R.string.steam_network_dynamic_dns_no_sources)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.84f)
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = enabled || canEnable,
                    onCheckedChange = onEnabledChange
                )
            }

            Text(
                text = stringResource(R.string.steam_network_dynamic_dns_description),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Text(
                text = stringResource(R.string.steam_network_dynamic_dns_scope),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.steam_network_dynamic_dns_cache_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = if (cacheCount > 0) {
                        stringResource(R.string.steam_network_dynamic_dns_cache_count, cacheCount)
                    } else {
                        stringResource(R.string.steam_network_dynamic_dns_cache_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
                Text(
                    text = stringResource(R.string.steam_network_dynamic_dns_cache_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f),
                    enabled = cacheCount > 0 && !refreshing
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_network_dynamic_dns_cache_clear),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onForceRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && activeProviderCount > 0 && !refreshing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_network_dynamic_dns_cache_refresh),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
