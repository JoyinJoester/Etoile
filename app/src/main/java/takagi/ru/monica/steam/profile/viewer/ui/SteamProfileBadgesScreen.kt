package takagi.ru.monica.steam.profile.viewer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.ui.CommunityBadgeIcon
import takagi.ru.monica.steam.community.ui.SteamCommunityBadgeDetailSheet
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot

internal enum class SteamProfileBadgeFilter { ALL, UNLOCKED, LOCKED }

@Composable
internal fun SteamProfileBadgesScreen(
    snapshot: SteamProfileViewerSnapshot,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dockClearance = LocalSteamDockContentClearance.current
    var selectedFilterName by rememberSaveable(snapshot.target.steamId) {
        mutableStateOf(SteamProfileBadgeFilter.ALL.name)
    }
    val selectedFilter = SteamProfileBadgeFilter.entries.firstOrNull {
        it.name == selectedFilterName
    } ?: SteamProfileBadgeFilter.ALL
    val visibleBadges = remember(snapshot.badges, selectedFilter) {
        when (selectedFilter) {
            SteamProfileBadgeFilter.ALL -> snapshot.badges
            SteamProfileBadgeFilter.UNLOCKED -> snapshot.badges.filter(SteamCommunityBadge::isUnlocked)
            SteamProfileBadgeFilter.LOCKED -> snapshot.badges.filterNot(SteamCommunityBadge::isUnlocked)
        }
    }
    var selectedBadge by remember(snapshot.target.steamId) {
        mutableStateOf<SteamCommunityBadge?>(null)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_community_badges),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.steam_profile_badges_title_count,
                        visibleBadges.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SteamProfileBadgeFilter.entries.forEachIndexed { index, filter ->
                SegmentedButton(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilterName = filter.name },
                    shape = SegmentedButtonDefaults.itemShape(
                        index,
                        SteamProfileBadgeFilter.entries.size
                    ),
                    label = { Text(stringResource(filter.labelRes), maxLines = 1) }
                )
            }
        }
        if (visibleBadges.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.steam_profile_no_filtered_badges),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(142.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = dockClearance + 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = visibleBadges,
                    key = { badge ->
                        "${badge.appId}-${badge.badgeId}-${badge.borderColor}-${badge.completionTime}"
                    }
                ) { badge ->
                    SteamProfileBadgeCard(
                        badge = badge,
                        onClick = { selectedBadge = badge }
                    )
                }
            }
        }
    }

    selectedBadge?.let { badge ->
        SteamCommunityBadgeDetailSheet(
            badge = badge,
            onOpenUrl = { url -> openSteamBadgeUrl(context, url) },
            onDismiss = { selectedBadge = null }
        )
    }
}

@Composable
private fun SteamProfileBadgeCard(
    badge: SteamCommunityBadge,
    onClick: () -> Unit
) {
    val title = badge.name.ifBlank {
        stringResource(R.string.steam_community_badge_number, badge.badgeId)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isUnlocked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(88.dp)) {
                CommunityBadgeIcon(
                    imageUrl = badge.iconUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (badge.isUnlocked) 1f else 0.46f)
                )
                if (!badge.isUnlocked) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).size(30.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            badge.gameName.takeIf { it.isNotBlank() && it != title }?.let { gameName ->
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(
                    if (badge.isUnlocked) R.string.steam_profile_badge_unlocked
                    else R.string.steam_profile_badge_locked
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (badge.isUnlocked) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private val SteamProfileBadgeFilter.labelRes: Int
    get() = when (this) {
        SteamProfileBadgeFilter.ALL -> R.string.steam_profile_badge_filter_all
        SteamProfileBadgeFilter.UNLOCKED -> R.string.steam_profile_badge_filter_unlocked
        SteamProfileBadgeFilter.LOCKED -> R.string.steam_profile_badge_filter_locked
    }

private fun openSteamBadgeUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
