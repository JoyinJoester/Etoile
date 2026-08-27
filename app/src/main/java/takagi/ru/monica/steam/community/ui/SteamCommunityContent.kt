package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.presentation.SteamCommunityUiState
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

@Composable
internal fun SteamCommunityContent(
    account: SteamAccount?,
    state: SteamCommunityUiState,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenStoreApp: (Int) -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val snapshot = state.snapshot
    var selectedBadge by remember(account?.steamId) {
        mutableStateOf<SteamCommunityBadge?>(null)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = dockClearance + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when {
            account == null -> item(key = "community-no-account") {
                CommunityEmptyState(
                    title = stringResource(R.string.steam_community_account_required),
                    summary = stringResource(R.string.steam_community_account_required_summary)
                )
            }
            snapshot == null && state.loading -> item(key = "community-loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            snapshot == null -> item(key = "community-failure") {
                CommunityFailureCard(state.failure, onRetry)
            }
            else -> {
                if (state.fromCache || state.failure != null) {
                    item(key = "community-cache-status") {
                        CommunityCacheStatus(state)
                    }
                }
                item(key = "community-hero") {
                    CommunityProfileHero(
                        account = account,
                        profile = snapshot.profile,
                        level = snapshot.steamLevel,
                        stale = SteamCommunitySection.PROFILE in state.staleSections
                    )
                }
                item(key = "community-unlock-progress") {
                    snapshot.unlockProgress?.let { progress ->
                        CommunityUnlockSection(
                            progress = progress,
                            stale = SteamCommunitySection.ELIGIBILITY in state.staleSections,
                            onOpenGame = onOpenStoreApp,
                            onOpenStore = onOpenStore,
                            onOpenRules = {
                                onOpenUrl(
                                    "https://help.steampowered.com/en/wizard/HelpWithLimitedAccount"
                                )
                            }
                        )
                    }
                }
                item(key = "community-level-xp") {
                    CommunityLevelCard(
                        level = snapshot.steamLevel,
                        playerXp = snapshot.playerXp,
                        xpNeeded = snapshot.playerXpNeededToLevelUp,
                        unavailable = SteamCommunitySection.LEVEL in snapshot.unavailableSections &&
                            SteamCommunitySection.BADGES in snapshot.unavailableSections
                    )
                }
                item(key = "community-badges-title") {
                    CommunitySectionHeader(
                        title = stringResource(R.string.steam_community_badges),
                        supporting = stringResource(
                            R.string.steam_community_badge_count,
                            snapshot.badges.size
                        )
                    )
                }
                item(key = "community-badges") {
                    CommunityBadges(
                        badges = snapshot.badges,
                        unavailable = SteamCommunitySection.BADGES in snapshot.unavailableSections,
                        onBadgeClick = { selectedBadge = it }
                    )
                }
                item(key = "community-recent-title") {
                    CommunitySectionHeader(
                        title = stringResource(R.string.steam_community_recent_games),
                        supporting = stringResource(R.string.steam_community_recent_games_summary)
                    )
                }
                item(key = "community-recent-games") {
                    CommunityRecentGames(
                        games = snapshot.recentGames,
                        unavailable = SteamCommunitySection.RECENT_GAMES in
                            snapshot.unavailableSections
                    )
                }
                item(key = "community-explore-title") {
                    CommunitySectionHeader(
                        title = stringResource(R.string.steam_community_explore),
                        supporting = stringResource(R.string.steam_community_explore_summary)
                    )
                }
                item(key = "community-explore") {
                    CommunityExploreLinks(
                        profile = snapshot.profile,
                        steamId = account.steamId,
                        onOpenUrl = onOpenUrl
                    )
                }
                item(key = "community-updated") {
                    Text(
                        text = stringResource(
                            R.string.steam_community_last_updated,
                            formatCommunityTimestamp(snapshot.fetchedAt)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    selectedBadge?.let { badge ->
        SteamCommunityBadgeDetailSheet(
            badge = badge,
            onOpenUrl = onOpenUrl,
            onDismiss = { selectedBadge = null }
        )
    }
}
