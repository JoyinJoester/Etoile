package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.ui.password.MonicaTopActionsDropdownMenu

/** Store destinations in the same compact menu surface used by page overflow actions. */
@Composable
internal fun SteamStoreBrowseMenu(
    selectedFilter: SteamStoreBrowseFilter,
    activeFilterCount: Int,
    onSelectFilter: (SteamStoreBrowseFilter) -> Unit,
    onOpenAdvancedFilters: () -> Unit,
    onOpenFreebies: () -> Unit,
    onOpenPointsShop: () -> Unit,
    onOpenProductActivation: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.coerceAtMost(99).toString()) }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = stringResource(R.string.steam_store_browse)
                )
            }
        }
        MonicaTopActionsDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SteamStoreBrowseFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(storeBrowseFilterLabel(filter)) },
                    leadingIcon = {
                        Icon(storeBrowseFilterIcon(filter), contentDescription = null)
                    },
                    trailingIcon = {
                        if (filter == selectedFilter) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectFilter(filter)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_store_advanced_filters)) },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingIcon = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.coerceAtMost(99).toString()) }
                    }
                },
                onClick = {
                    expanded = false
                    onOpenAdvancedFilters()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_store_freebies)) },
                leadingIcon = {
                    Icon(Icons.Default.Redeem, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onOpenFreebies()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_store_points_shop)) },
                leadingIcon = {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onOpenPointsShop()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_store_activate_product_code)) },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onOpenProductActivation()
                }
            )
        }
    }
}
