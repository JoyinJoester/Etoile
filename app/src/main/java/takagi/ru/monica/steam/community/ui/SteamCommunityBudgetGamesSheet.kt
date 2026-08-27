package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityBudgetGame
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityBudgetGamesSheet(
    progress: SteamCommunityUnlockProgress,
    onDismissRequest: () -> Unit,
    onOpenGame: (Int) -> Unit
) {
    val targetMinor = progress.localRemainingMinor
        ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        ?.toInt()
        ?: progress.remainingUsdCents
    val currency = if (progress.localRemainingMinor != null) {
        progress.accountCurrencyCode
    } else {
        "USD"
    }
    val upperMinor = communityBudgetUpperMinor(targetMinor)

    MonicaModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_community_unlock_games_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.steam_community_unlock_games_summary,
                        formatSteamPrice(targetMinor, currency),
                        formatSteamPrice(upperMinor, currency)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress.suggestedGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.steam_community_unlock_games_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(progress.suggestedGames, key = SteamCommunityBudgetGame::appId) { game ->
                        CommunityBudgetGameCard(
                            game = game,
                            targetMinor = targetMinor,
                            onClick = { onOpenGame(game.appId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityBudgetGameCard(
    game: SteamCommunityBudgetGame,
    targetMinor: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityGameIcon(game.imageUrl, Modifier.size(width = 104.dp, height = 58.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                CommunityBudgetGameLabels(game)
                CommunityBudgetGamePrice(game)
                Text(
                    text = communityBudgetOverageText(game, targetMinor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommunityBudgetGameLabels(game: SteamCommunityBudgetGame) {
    if (!game.inWishlist && game.discountPercent <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (game.inWishlist) {
            CommunityBudgetLabel(
                text = stringResource(R.string.steam_community_unlock_wishlist),
                emphasized = true
            )
        }
        if (game.discountPercent > 0) {
            CommunityBudgetLabel(text = "-${game.discountPercent}%", emphasized = false)
        }
    }
}

@Composable
private fun CommunityBudgetLabel(text: String, emphasized: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CommunityBudgetGamePrice(game: SteamCommunityBudgetGame) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (game.discountPercent > 0 && game.originalPriceMinor != null) {
            Text(
                text = formatSteamPrice(game.originalPriceMinor, game.currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
        }
        Text(
            text = formatSteamPrice(game.finalPriceMinor, game.currency),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun communityBudgetOverageText(
    game: SteamCommunityBudgetGame,
    targetMinor: Int
): String {
    val overageMinor = (game.finalPriceMinor - targetMinor).coerceAtLeast(0)
    if (overageMinor == 0) {
        return stringResource(R.string.steam_community_unlock_exact_cover)
    }
    val percentage = if (targetMinor > 0) {
        String.format(Locale.getDefault(), "%.1f%%", overageMinor * 100.0 / targetMinor)
    } else {
        "0.0%"
    }
    return stringResource(
        R.string.steam_community_unlock_overage,
        formatSteamPrice(overageMinor, game.currency),
        percentage
    )
}

internal fun communityBudgetUpperMinor(targetMinor: Int): Int =
    ((targetMinor.toLong() * 110L) / 100L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
