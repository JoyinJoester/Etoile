package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun CommunityUnlockSection(
    progress: SteamCommunityUnlockProgress,
    stale: Boolean,
    onOpenGame: (Int) -> Unit,
    onOpenStore: () -> Unit,
    onOpenRules: () -> Unit
) {
    val status = progress.status
    val unlocked = status == SteamCommunityRestrictionStatus.UNRESTRICTED
    val spendDisplayMode = communitySpendDisplayMode(progress)
    var showBudgetGames by rememberSaveable { mutableStateOf(false) }
    val containerColor = when (progress.status) {
        SteamCommunityRestrictionStatus.LIMITED -> MaterialTheme.colorScheme.secondaryContainer
        SteamCommunityRestrictionStatus.UNRESTRICTED -> MaterialTheme.colorScheme.primaryContainer
        SteamCommunityRestrictionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (progress.status) {
        SteamCommunityRestrictionStatus.LIMITED -> MaterialTheme.colorScheme.onSecondaryContainer
        SteamCommunityRestrictionStatus.UNRESTRICTED -> MaterialTheme.colorScheme.onPrimaryContainer
        SteamCommunityRestrictionStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = contentColor.copy(alpha = 0.10f),
                    contentColor = contentColor
                ) {
                    Icon(
                        imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_community_unlock_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = unlockStatusText(progress.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.76f)
                    )
                    if (stale) {
                        Text(
                            text = stringResource(R.string.steam_community_cached_section),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            if (shouldShowCommunitySpendEstimate(progress.status)) {
                CommunitySpendEstimateButton(
                    progress = progress,
                    displayMode = spendDisplayMode,
                    contentColor = contentColor,
                    onClick = { showBudgetGames = true }
                )
            } else {
                Text(
                    text = stringResource(R.string.steam_community_unlock_complete),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GoogleSansFlexFontFamily
                    ),
                    fontWeight = FontWeight.Bold
                )
            }

            if (shouldShowCommunitySpendEstimate(progress.status)) {
                CommunityProgressSourceLabel(
                    displayMode = spendDisplayMode,
                    contentColor = contentColor
                )
            }

            if (shouldShowCommunityProgressIndicator(progress)) {
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.14f)
                )
            }

            Text(
                text = communitySpendSummary(progress, spendDisplayMode),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f)
            )

            CommunityUnlockActions(onOpenStore = onOpenStore, onOpenRules = onOpenRules)
        }
    }

    if (showBudgetGames) {
        CommunityBudgetGamesSheet(
            progress = progress,
            onDismissRequest = { showBudgetGames = false },
            onOpenGame = { appId ->
                showBudgetGames = false
                onOpenGame(appId)
            }
        )
    }
}

@Composable
private fun CommunitySpendEstimateButton(
    progress: SteamCommunityUnlockProgress,
    displayMode: CommunitySpendDisplayMode,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val historyMayCover = displayMode == CommunitySpendDisplayMode.TRANSACTION_ESTIMATE &&
        progress.mayHaveReachedThreshold
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.10f),
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        historyMayCover -> stringResource(
                            R.string.steam_community_unlock_history_may_cover
                        )
                        displayMode == CommunitySpendDisplayMode.OFFICIAL -> stringResource(
                            R.string.steam_community_unlock_official_remaining,
                            remainingAmount(progress)
                        )
                        displayMode == CommunitySpendDisplayMode.TRANSACTION_ESTIMATE -> stringResource(
                            R.string.steam_community_unlock_estimated_remaining,
                            remainingAmount(progress)
                        )
                        else -> stringResource(R.string.steam_community_unlock_amount_unknown)
                    },
                    style = (if (historyMayCover) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    }).copy(
                        fontFamily = GoogleSansFlexFontFamily
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        historyMayCover -> stringResource(
                            R.string.steam_community_unlock_waiting_confirmation
                        )
                        displayMode == CommunitySpendDisplayMode.UNKNOWN -> stringResource(
                            R.string.steam_community_unlock_games_full_threshold_action
                        )
                        else -> stringResource(R.string.steam_community_unlock_games_action)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.76f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    R.string.steam_community_unlock_games_action
                ),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun unlockStatusText(status: SteamCommunityRestrictionStatus): String = stringResource(
    when (status) {
        SteamCommunityRestrictionStatus.LIMITED -> R.string.steam_community_unlock_limited
        SteamCommunityRestrictionStatus.UNRESTRICTED -> R.string.steam_community_unlock_unrestricted
        SteamCommunityRestrictionStatus.UNKNOWN -> R.string.steam_community_unlock_unknown
    }
)

internal fun shouldShowCommunitySpendEstimate(
    status: SteamCommunityRestrictionStatus
): Boolean = status != SteamCommunityRestrictionStatus.UNRESTRICTED
