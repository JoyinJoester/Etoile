package takagi.ru.monica.github.domain

import kotlinx.coroutines.flow.StateFlow

data class GithubCacheFallbackSnapshot(
    val cachedAtEpochMillis: Long,
    val detectedAtEpochMillis: Long
)

interface GithubCacheFallbackMonitor {
    val state: StateFlow<GithubCacheFallbackSnapshot?>
}
