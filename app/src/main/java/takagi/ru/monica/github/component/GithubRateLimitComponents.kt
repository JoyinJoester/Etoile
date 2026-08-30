package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    val rateLimit: GithubRateLimitSnapshot? = null,
    val cacheFallback: GithubCacheFallbackSnapshot? = null
)

internal val LocalGithubServiceStatus = compositionLocalOf { GithubServiceStatusState() }

@Composable
fun GithubServiceStatusProvider(
    rateLimit: GithubRateLimitSnapshot?,
    cacheFallback: GithubCacheFallbackSnapshot?,
    content: @Composable () -> Unit
) {
    val status = remember(rateLimit, cacheFallback) {
        GithubServiceStatusState(rateLimit = rateLimit, cacheFallback = cacheFallback)
    }
    CompositionLocalProvider(LocalGithubServiceStatus provides status, content = content)
}

@Composable
fun GithubServiceStatusNotices(modifier: Modifier = Modifier) {
    val status = LocalGithubServiceStatus.current
    GithubServiceStatusNotices(
        rateLimit = status.rateLimit,
        cacheFallback = status.cacheFallback,
        modifier = modifier
    )
}

@Composable
fun GithubServiceStatusNotices(
    rateLimit: GithubRateLimitSnapshot?,
    cacheFallback: GithubCacheFallbackSnapshot?,
    modifier: Modifier = Modifier
) {
    val showRateLimit = rateLimit?.isLow == true
    if (cacheFallback == null && !showRateLimit) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cacheFallback?.let { GithubCacheFallbackNotice(it) }
        if (showRateLimit) GithubRateLimitNotice(requireNotNull(rateLimit))
    }
}

@Composable
fun GithubCacheFallbackNotice(
    snapshot: GithubCacheFallbackSnapshot,
    modifier: Modifier = Modifier
) {
    val cachedTime = remember(snapshot.cachedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
            Date(snapshot.cachedAtEpochMillis)
        )
    }

    GithubStatusNotice(
        icon = Icons.Default.CloudOff,
        title = stringResource(R.string.github_cache_fallback_title),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.github_cache_fallback_message, cachedTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Shared, non-blocking notice for low or exhausted GitHub API quota.
 * It intentionally renders nothing while the quota is healthy so callers can
 * place it at the top of any screen without branching layout code.
 */
@Composable
fun GithubRateLimitNotice(
    snapshot: GithubRateLimitSnapshot,
    modifier: Modifier = Modifier
) {
    if (!snapshot.isLow) return

    val containerColor = if (snapshot.isExhausted) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (snapshot.isExhausted) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val resetTime = remember(snapshot.resetAtEpochSeconds) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(
            Date(snapshot.resetAtEpochSeconds * 1000L)
        )
    }

    GithubStatusNotice(
        icon = Icons.Default.Schedule,
        title = stringResource(
            if (snapshot.isExhausted) {
                R.string.github_rate_limit_exhausted
            } else {
                R.string.github_rate_limit_low
            }
        ),
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Text(
            text = stringResource(
                R.string.github_rate_limit_value,
                snapshot.remaining,
                snapshot.limit
            ) + " · " + stringResource(R.string.github_rate_limit_resets, resetTime),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun GithubStatusNotice(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    supportingContent: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = GithubExpressiveShapes.container,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp).padding(top = 1.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                supportingContent()
            }
        }
    }
}
