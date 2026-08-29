package takagi.ru.monica.github.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubRelativeTime
import takagi.ru.monica.github.domain.GithubTimestamps

/**
 * Shared wall clock for relative timestamps. One ticker at the application root keeps every
 * visible timestamp current without each row owning a coroutine.
 */
@Composable
fun GithubTimestampProvider(content: @Composable () -> Unit) {
    val epochSeconds = remember { mutableLongStateOf(currentEpochSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            epochSeconds.longValue = currentEpochSeconds()
        }
    }
    CompositionLocalProvider(LocalGithubEpochSeconds provides epochSeconds) {
        content()
    }
}

/**
 * Formats a GitHub ISO-8601 timestamp as localized relative time, falling back to the calendar
 * date for future or unparsable values.
 */
@Composable
fun githubRelativeTime(isoTimestamp: String): String {
    val nowEpochSeconds = LocalGithubEpochSeconds.current?.longValue ?: currentEpochSeconds()
    val relative = remember(isoTimestamp, nowEpochSeconds) {
        GithubTimestamps.relativeTo(isoTimestamp, nowEpochSeconds)
    }
    return when (relative) {
        GithubRelativeTime.JustNow -> stringResource(R.string.github_time_just_now)
        is GithubRelativeTime.Minutes ->
            pluralStringResource(R.plurals.github_time_minutes_ago, relative.value, relative.value)
        is GithubRelativeTime.Hours ->
            pluralStringResource(R.plurals.github_time_hours_ago, relative.value, relative.value)
        is GithubRelativeTime.Days ->
            pluralStringResource(R.plurals.github_time_days_ago, relative.value, relative.value)
        is GithubRelativeTime.Months ->
            pluralStringResource(R.plurals.github_time_months_ago, relative.value, relative.value)
        is GithubRelativeTime.Years ->
            pluralStringResource(R.plurals.github_time_years_ago, relative.value, relative.value)
        is GithubRelativeTime.AbsoluteDate -> relative.isoDate
    }
}

/** Relative time for optional timestamps, with a caller-supplied label when absent. */
@Composable
fun githubRelativeTimeOrElse(isoTimestamp: String?, fallback: String): String =
    isoTimestamp?.takeIf { it.isNotBlank() }?.let { githubRelativeTime(it) } ?: fallback

private val LocalGithubEpochSeconds = staticCompositionLocalOf<LongState?> { null }

// Buckets are minute-granular, so a slightly late tick is never visible.
private const val TICK_MILLIS = 60_000L

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
