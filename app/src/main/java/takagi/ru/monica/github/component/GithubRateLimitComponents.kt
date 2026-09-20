package takagi.ru.monica.github.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubCacheFallbackSnapshot
import takagi.ru.monica.github.domain.GithubRateLimitSnapshot
import java.text.DateFormat
import java.util.Date

internal data class GithubServiceStatusState(
    val rateLimits: List<GithubRateLimitSnapshot> = emptyList(),
    val cacheFallback: GithubCacheFallbackSnapshot? = null
)

internal val LocalGithubServiceStatus = compositionLocalOf { GithubServiceStatusState() }

@Composable
fun GithubServiceStatusProvider(
    rateLimits: List<GithubRateLimitSnapshot>,
    cacheFallback: GithubCacheFallbackSnapshot?,
    content: @Composable () -> Unit
) {
    val status = remember(rateLimits, cacheFallback) {
        GithubServiceStatusState(rateLimits = rateLimits, cacheFallback = cacheFallback)
    }
    CompositionLocalProvider(LocalGithubServiceStatus provides status, content = content)
}

@Composable
fun GithubServiceStatusNotices(modifier: Modifier = Modifier) {
    val status = LocalGithubServiceStatus.current
    GithubServiceStatusNotices(
        rateLimits = status.rateLimits,
        cacheFallback = status.cacheFallback,
        modifier = modifier
    )
}

@Composable
fun GithubServiceStatusNotices(
    rateLimits: List<GithubRateLimitSnapshot>,
    cacheFallback: GithubCacheFallbackSnapshot?,
    modifier: Modifier = Modifier
) {
    if (cacheFallback == null && rateLimits.isEmpty()) return

    val anyExhausted = rateLimits.any { it.isExhausted }
    val quotaAlert = rateLimits.isNotEmpty()
    val bannerColor = when {
        anyExhausted -> MaterialTheme.colorScheme.errorContainer
        quotaAlert -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = GithubExpressiveShapes.compact,
        color = bannerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cacheFallback?.let { snapshot ->
                val cachedTime = remember(snapshot.cachedAtEpochMillis) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
                        Date(snapshot.cachedAtEpochMillis)
                    )
                }
                GithubCompactStatusRow(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.github_cache_fallback_title),
                    supportingText = stringResource(R.string.github_cache_fallback_message, cachedTime),
                    tint = when {
                        anyExhausted -> MaterialTheme.colorScheme.onErrorContainer
                        quotaAlert -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            rateLimits.forEach { snapshot ->
                val resetTime = remember(snapshot.resetAtEpochSeconds) {
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(
                        Date(snapshot.resetAtEpochSeconds * 1000L)
                    )
                }
                val tint = if (snapshot.isExhausted) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
                GithubCompactStatusRow(
                    icon = Icons.Default.Schedule,
                    title = stringResource(rateLimitTitle(snapshot)),
                    supportingText = stringResource(
                        R.string.github_rate_limit_value,
                        snapshot.remaining,
                        snapshot.limit
                    ) + " · " + stringResource(R.string.github_rate_limit_resets, resetTime),
                    tint = tint
                )
            }
        }
    }
}

@StringRes
private fun rateLimitTitle(snapshot: GithubRateLimitSnapshot): Int = when {
    snapshot.isSearchResource && snapshot.isExhausted -> R.string.github_rate_limit_exhausted_search
    snapshot.isSearchResource -> R.string.github_rate_limit_low_search
    snapshot.isExhausted -> R.string.github_rate_limit_exhausted
    else -> R.string.github_rate_limit_low
}

private val GithubRateLimitSnapshot.isSearchResource: Boolean
    get() = resource.equals("search", ignoreCase = true)

@Composable
private fun GithubCompactStatusRow(
    icon: ImageVector,
    title: String,
    supportingText: String,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp).padding(top = 1.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelMedium,
                color = tint.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
