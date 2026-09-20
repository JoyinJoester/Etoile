package takagi.ru.monica.github.domain

import kotlinx.coroutines.flow.StateFlow

data class GithubRateLimitSnapshot(
    val resource: String,
    val limit: Int,
    val remaining: Int,
    val used: Int,
    val resetAtEpochSeconds: Long
) {
    val isExhausted: Boolean get() = remaining <= 0
    val isLow: Boolean get() = remaining <= maxOf(10, limit / 20)
}

interface GithubRateLimitMonitor {
    val state: StateFlow<Map<String, GithubRateLimitSnapshot>>
}

/**
 * GitHub limits each resource separately, so a healthy core bucket says nothing
 * about the tiny search one. Ordering puts the bucket that will block requests
 * soonest first.
 */
fun visibleRateLimits(
    snapshots: Map<String, GithubRateLimitSnapshot>
): List<GithubRateLimitSnapshot> = snapshots.values
    .filter(GithubRateLimitSnapshot::isLow)
    .sortedWith(
        compareByDescending<GithubRateLimitSnapshot> { it.isExhausted }
            .thenBy { it.remaining }
            .thenBy { it.resetAtEpochSeconds }
    )
