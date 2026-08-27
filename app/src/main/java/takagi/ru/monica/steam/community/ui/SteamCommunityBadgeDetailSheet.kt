package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamCommunityBadgeDetailSheet(
    badge: SteamCommunityBadge,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val title = badge.name.ifBlank {
        stringResource(R.string.steam_community_badge_number, badge.badgeId)
    }
    MonicaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommunityBadgeIcon(
                    imageUrl = badge.iconUrl,
                    contentDescription = title,
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge.gameName.isNotBlank() && badge.gameName != title) {
                        Text(
                            text = badge.gameName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.steam_community_badge_number,
                            badge.badgeId
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    BadgeDetailRow(
                        label = stringResource(R.string.steam_community_badge_id),
                        value = "#${badge.badgeId}"
                    )
                    HorizontalDivider()
                    BadgeDetailRow(
                        label = stringResource(R.string.steam_profile_badge_status),
                        value = stringResource(
                            if (badge.isUnlocked) R.string.steam_profile_badge_unlocked
                            else R.string.steam_profile_badge_locked
                        )
                    )
                    HorizontalDivider()
                    if (badge.level > 0) {
                        BadgeDetailRow(
                            label = stringResource(R.string.steam_community_level),
                            value = badge.level.toString()
                        )
                        HorizontalDivider()
                    }
                    BadgeDetailRow(
                        label = stringResource(R.string.steam_community_badge_xp_label),
                        value = badge.xp.toString()
                    )
                    if (badge.scarcity > 0) {
                        HorizontalDivider()
                        BadgeDetailRow(
                            label = stringResource(R.string.steam_community_badge_scarcity),
                            value = badge.scarcity.toString()
                        )
                    }
                    val unlocked = badge.unlockedAt.ifBlank {
                        badge.completionTime.takeIf { it > 0L }?.let(::formatCommunityDate).orEmpty()
                    }
                    if (unlocked.isNotBlank()) {
                        HorizontalDivider()
                        BadgeDetailRow(
                            label = stringResource(R.string.steam_community_badge_unlocked),
                            value = unlocked
                        )
                    }
                }
            }

            if (badge.detailUrl.isNotBlank()) {
                FilledTonalButton(
                    onClick = {
                        onOpenUrl(badge.detailUrl)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_community_badge_view_on_steam),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
