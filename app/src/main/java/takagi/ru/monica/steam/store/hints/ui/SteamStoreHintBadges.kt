package takagi.ru.monica.steam.store.hints.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamStoreHintBadges(
    hints: List<SteamStoreHintKind>,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (hints.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        hints.distinct().forEach { hint ->
            SteamStoreHintBadge(hint = hint, compact = compact)
        }
    }
}

@Composable
private fun SteamStoreHintBadge(hint: SteamStoreHintKind, compact: Boolean) {
    val visuals = steamStoreHintVisuals(hint)
    Surface(
        shape = RoundedCornerShape(50),
        color = visuals.containerColor,
        contentColor = visuals.contentColor,
        tonalElevation = if (compact) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Text(
                text = stringResource(visuals.label),
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun steamStoreHintVisuals(hint: SteamStoreHintKind): SteamStoreHintVisuals = when (hint) {
    SteamStoreHintKind.OWNED -> SteamStoreHintVisuals(
        icon = Icons.Default.CheckCircle,
        label = R.string.steam_store_hint_owned,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    SteamStoreHintKind.FAMILY_SHARED -> SteamStoreHintVisuals(
        icon = Icons.Default.Groups,
        label = R.string.steam_store_hint_family_shared,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
    SteamStoreHintKind.WISHLIST -> SteamStoreHintVisuals(
        icon = Icons.Default.Favorite,
        label = R.string.steam_store_hint_wishlist,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )
    SteamStoreHintKind.SUPPORTS_FAMILY_SHARING -> SteamStoreHintVisuals(
        icon = Icons.Default.FamilyRestroom,
        label = R.string.steam_store_hint_supports_family_sharing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

private data class SteamStoreHintVisuals(
    val icon: ImageVector,
    val label: Int,
    val containerColor: Color,
    val contentColor: Color
)
