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
    // The unauthenticated search bucket holds only ten requests. An absolute
    // floor of ten would warn even when it is full; cap it at half the bucket.
    val isLow: Boolean get() = remaining <= minOf(maxOf(10, limit / 20), limit.coerceAtLeast(0) / 2)
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
