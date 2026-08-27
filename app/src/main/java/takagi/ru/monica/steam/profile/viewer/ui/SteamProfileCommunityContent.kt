package takagi.ru.monica.steam.profile.viewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.ui.CommunityBadges
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility

@Composable
internal fun SteamProfileCommunityMetrics(
    snapshot: SteamProfileViewerSnapshot,
    onOpenBadges: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenPerfectGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SteamProfileMetricCard(
                icon = Icons.Default.SportsEsports,
                value = snapshot.targetGameCount.toString(),
                label = stringResource(R.string.steam_profile_game_count),
                modifier = Modifier.weight(1f)
            )
            SteamProfileMetricCard(
                icon = Icons.Default.Schedule,
                value = formatSteamProfilePlaytime(snapshot.targetPlaytimeMinutes),
                label = stringResource(R.string.steam_profile_total_playtime),
                modifier = Modifier.weight(1f)
            )
            SteamProfileMetricCard(
                icon = Icons.Default.EmojiEvents,
                value = if (snapshot.isSelf) {
                    snapshot.perfectGameCount.toString()
                } else {
                    snapshot.commonGameCount.toString()
                },
                label = stringResource(
                    if (snapshot.isSelf) R.string.steam_profile_perfect_games
                    else R.string.steam_profile_common_games
                ),
                onClick = onOpenPerfectGames.takeIf {
                    snapshot.isSelf &&
                        snapshot.gameDataVisibility == SteamProfileGameDataVisibility.AVAILABLE
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SteamProfileMetricCard(
                icon = Icons.Default.Badge,
                value = snapshot.badgeCount.profileCountValue(
                    snapshot.badges.count { it.isUnlocked }.takeIf {
                        snapshot.badges.isNotEmpty()
                    }
                ),
                label = stringResource(R.string.steam_community_badges),
                onClick = onOpenBadges.takeIf { snapshot.badges.isNotEmpty() },
                modifier = Modifier.weight(1f)
            )
            SteamProfileMetricCard(
                icon = Icons.Default.People,
                value = snapshot.friendCount.profileCountValue(),
                label = stringResource(R.string.steam_friends_title),
                onClick = onOpenFriends,
                modifier = Modifier.weight(1f)
            )
            SteamProfileMetricCard(
                icon = Icons.Default.Groups,
                value = snapshot.groupCount.profileCountValue(),
                label = stringResource(R.string.steam_community_groups),
                onClick = onOpenGroups,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun SteamProfileBadgePreview(
    snapshot: SteamProfileViewerSnapshot,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_community_badges),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.steam_profile_badges_title_count,
                        snapshot.badges.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpenBadges,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.steam_profile_view_all_badges))
            }
        }
        CommunityBadges(
            badges = snapshot.badges,
            unavailable = false,
            onBadgeClick = { onOpenBadges() }
        )
    }
}

@Composable
private fun SteamProfileMetricCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 104.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.heightIn(min = 104.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content
        )
    }
}

private fun Int?.profileCountValue(fallback: Int? = null): String =
    (this ?: fallback)?.toString() ?: "—"
