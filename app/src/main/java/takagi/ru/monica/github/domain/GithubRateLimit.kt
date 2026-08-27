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
