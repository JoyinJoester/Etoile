package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.community.domain.SteamCommunityRecentGame

@Composable
internal fun CommunityBadges(
    badges: List<SteamCommunityBadge>,
    unavailable: Boolean,
    onBadgeClick: (SteamCommunityBadge) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val badgeWidth = if (fontScale > 1.15f) 184.dp else 154.dp
    when {
        badges.isNotEmpty() -> LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(badges, key = {
                "${it.appId}-${it.badgeId}-${it.borderColor}-${it.completionTime}"
            }) { badge ->
                val badgeTitle = badge.name.ifBlank {
                    stringResource(R.string.steam_community_badge_number, badge.badgeId)
                }
                Card(
                    onClick = { onBadgeClick(badge) },
                    modifier = Modifier.width(badgeWidth),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (badge.isUnlocked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        CommunityBadgeIcon(
                            imageUrl = badge.iconUrl,
                            contentDescription = badgeTitle,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            badgeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        badge.gameName.takeIf { it.isNotBlank() && it != badgeTitle }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                    alpha = 0.78f
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (badge.isUnlocked) {
                                stringResource(R.string.steam_community_badge_level, badge.level)
                            } else {
                                stringResource(R.string.steam_profile_badge_locked)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.steam_community_badge_xp, badge.xp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
        unavailable -> CommunityUnavailableCard()
        else -> CommunityEmptyState(
            title = stringResource(R.string.steam_community_no_badges),
            summary = stringResource(R.string.steam_community_no_badges_summary)
        )
    }
}

@Composable
internal fun CommunityRecentGames(
    games: List<SteamCommunityRecentGame>,
    unavailable: Boolean
) {
    when {
        games.isNotEmpty() -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column {
                games.forEachIndexed { index, game ->
                    ListItem(
                        headlineContent = {
                            Text(
                                game.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(
                                        R.string.steam_community_total_playtime,
                                        formatCommunityMinutes(game.playtimeForeverMinutes)
                                    )
                                )
                                if (game.playtimeTwoWeeksMinutes > 0) {
                                    Text(
                                        stringResource(
                                            R.string.steam_community_recent_playtime,
                                            formatCommunityMinutes(game.playtimeTwoWeeksMinutes)
                                        )
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            CommunityGameIcon(
                                imageUrl = game.iconUrl,
                                modifier = Modifier.size(52.dp)
                            )
                        },
                        trailingContent = {
                            if (game.lastPlayedAt > 0L) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = stringResource(
                                        R.string.steam_community_last_played,
                                        formatCommunityDate(game.lastPlayedAt)
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != games.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 84.dp))
                    }
                }
            }
        }
        unavailable -> CommunityUnavailableCard()
        else -> CommunityEmptyState(
            title = stringResource(R.string.steam_community_no_recent_games),
            summary = stringResource(R.string.steam_community_no_recent_games_summary)
        )
    }
}

@Composable
internal fun CommunityExploreLinks(
    profile: SteamCommunityProfile?,
    steamId: String,
    onOpenUrl: (String) -> Unit
) {
    val base = communityProfileBaseUrl(profile?.profileUrl.orEmpty(), steamId)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("profile") {
            CommunityLinkButton(
                label = stringResource(R.string.steam_community_open_profile),
                icon = { Icon(Icons.Default.Public, contentDescription = null) },
                onClick = { onOpenUrl(base) }
            )
        }
        item("screenshots") {
            CommunityLinkButton(
                label = stringResource(R.string.steam_community_screenshots),
                icon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = { onOpenUrl("${base}screenshots/") }
            )
        }
        item("guides") {
            CommunityLinkButton(
                label = stringResource(R.string.steam_community_guides),
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                onClick = { onOpenUrl("${base}myworkshopfiles/?section=guides") }
            )
        }
        item("discussions") {
            CommunityLinkButton(
                label = stringResource(R.string.steam_community_discussions),
                icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                onClick = { onOpenUrl("https://steamcommunity.com/discussions/") }
            )
        }
        item("groups") {
            CommunityLinkButton(
                label = stringResource(R.string.steam_community_groups),
                icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                onClick = { onOpenUrl("${base}groups/") }
            )
        }
    }
}

@Composable
private fun CommunityLinkButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private fun communityProfileBaseUrl(profileUrl: String, steamId: String): String {
    val safe = profileUrl.trim().takeIf {
        it.startsWith("https://steamcommunity.com/", ignoreCase = true)
    } ?: "https://steamcommunity.com/profiles/$steamId/"
    return if (safe.endsWith('/')) safe else "$safe/"
}

@Composable
private fun formatCommunityMinutes(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.steam_community_minutes, 0)
    minutes < 60 -> stringResource(R.string.steam_community_minutes, minutes)
    else -> stringResource(R.string.steam_community_hours, minutes / 60f)
}
