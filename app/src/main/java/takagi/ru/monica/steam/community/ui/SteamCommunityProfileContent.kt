package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun CommunityProfileHero(
    account: SteamAccount,
    profile: SteamCommunityProfile?,
    level: Int?,
    stale: Boolean
) {
    val displayName = profile?.displayName.orEmpty().ifBlank {
        account.displayName.ifBlank { account.accountName.ifBlank { account.visibleSteamId } }
    }
    val fontScale = LocalDensity.current.fontScale
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackLevel = maxWidth < 340.dp || fontScale > 1.15f
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommunityAvatar(
                        imageUrl = profile?.avatarUrl.orEmpty(),
                        fallback = displayName.take(1).uppercase(),
                        modifier = Modifier.size(if (stackLevel) 58.dp else 64.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = GoogleSansFlexFontFamily
                            ),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        profile?.realName?.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = account.visibleSteamId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!stackLevel) level?.let { CommunityLevelBadge(it) }
                }
                if (stackLevel) level?.let { CommunityLevelBadge(it) }
                profile?.summary?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val memberSince = profile?.createdAt?.takeIf { it > 0L }?.let {
                    stringResource(R.string.steam_community_member_since, formatCommunityDate(it))
                }
                val cachedLabel = stringResource(R.string.steam_community_cached_section)
                    .takeIf { stale }
                val metadata = listOfNotNull(
                    profile?.countryCode?.takeIf(String::isNotBlank),
                    memberSince,
                    cachedLabel
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityLevelBadge(level: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.steam_community_level),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun CommunityLevelCard(
    level: Int?,
    playerXp: Int?,
    xpNeeded: Int?,
    unavailable: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.steam_community_progress),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            CommunityMetricRow(
                label = stringResource(R.string.steam_community_level),
                value = level?.toString() ?: "—"
            )
            HorizontalDivider()
            CommunityMetricRow(
                label = stringResource(R.string.steam_community_total_xp),
                value = playerXp?.toString() ?: "—"
            )
            HorizontalDivider()
            CommunityMetricRow(
                label = stringResource(R.string.steam_community_next_level),
                value = xpNeeded?.toString() ?: "—"
            )
            if (unavailable) CommunityInlineWarning()
        }
    }
}

@Composable
private fun CommunityMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = GoogleSansFlexFontFamily
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
