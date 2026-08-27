package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityProgressSource
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.store.domain.formatSteamPrice

internal enum class CommunitySpendDisplayMode {
    OFFICIAL,
    TRANSACTION_ESTIMATE,
    UNKNOWN
}

internal fun communitySpendDisplayMode(
    progress: SteamCommunityUnlockProgress
): CommunitySpendDisplayMode = when (progress.progressSource) {
    SteamCommunityProgressSource.STEAM_SUPPORT -> CommunitySpendDisplayMode.OFFICIAL
    SteamCommunityProgressSource.TRANSACTION_HISTORY ->
        CommunitySpendDisplayMode.TRANSACTION_ESTIMATE
    SteamCommunityProgressSource.NONE -> if (progress.exactProgress) {
        CommunitySpendDisplayMode.OFFICIAL
    } else {
        CommunitySpendDisplayMode.UNKNOWN
    }
}

internal fun shouldShowCommunityProgressIndicator(
    progress: SteamCommunityUnlockProgress
): Boolean = progress.status != SteamCommunityRestrictionStatus.UNRESTRICTED &&
    progress.hasMeasuredProgress

@Composable
internal fun CommunityProgressSourceLabel(
    displayMode: CommunitySpendDisplayMode,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = contentColor.copy(alpha = 0.10f),
        contentColor = contentColor
    ) {
        Text(
            text = stringResource(
                when (displayMode) {
                    CommunitySpendDisplayMode.OFFICIAL ->
                        R.string.steam_community_unlock_source_official
                    CommunitySpendDisplayMode.TRANSACTION_ESTIMATE ->
                        R.string.steam_community_unlock_source_history
                    CommunitySpendDisplayMode.UNKNOWN ->
                        R.string.steam_community_unlock_source_unknown
                }
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun communitySpendSummary(
    progress: SteamCommunityUnlockProgress,
    displayMode: CommunitySpendDisplayMode
): String {
    if (progress.status == SteamCommunityRestrictionStatus.UNRESTRICTED) {
        return stringResource(R.string.steam_community_unlock_complete_summary)
    }
    return when (displayMode) {
        CommunitySpendDisplayMode.OFFICIAL -> stringResource(
            R.string.steam_community_unlock_official_progress,
            formatUsd(progress.remainingUsdCents)
        )
        CommunitySpendDisplayMode.TRANSACTION_ESTIMATE -> {
            if (progress.mayHaveReachedThreshold) {
                stringResource(R.string.steam_community_unlock_history_may_cover_summary)
            } else {
                val lower = progress.spentUsdCents ?: 0
                val upper = progress.estimatedSpentUpperUsdCents ?: lower
                stringResource(
                    R.string.steam_community_unlock_history_progress,
                    formatUsd(lower),
                    formatUsd(upper)
                )
            }
        }
        CommunitySpendDisplayMode.UNKNOWN -> stringResource(
            R.string.steam_community_unlock_no_amount_summary
        )
    }
}

internal fun remainingAmount(progress: SteamCommunityUnlockProgress): String {
    val local = progress.localRemainingMinor
    return if (local != null && local in 0..Int.MAX_VALUE.toLong()) {
        formatSteamPrice(local.toInt(), progress.accountCurrencyCode)
    } else {
        formatUsd(progress.remainingUsdCents)
    }
}

private fun formatUsd(cents: Int): String = String.format(Locale.US, "$%.2f", cents / 100.0)
