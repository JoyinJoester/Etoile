package takagi.ru.monica.steam.network.optimization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.network.optimization.domain.SteamHostHitStats
import takagi.ru.monica.steam.network.optimization.domain.SteamHostSessionStats

internal class SteamHostSessionStatsTracker(
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutableStats = MutableStateFlow(SteamHostSessionStats())
    val stats: StateFlow<SteamHostSessionStats> = mutableStats.asStateFlow()

    @Synchronized
    fun record(hostname: String) {
        val current = mutableStats.value
        val previous = current.hosts[hostname] ?: SteamHostHitStats()
        mutableStats.value = current.copy(
            totalHitCount = current.totalHitCount + 1L,
            hosts = current.hosts + (
                hostname to previous.copy(
                    hitCount = previous.hitCount + 1L,
                    lastHitAtEpochMillis = clock()
                )
            )
        )
    }

    @Synchronized
    fun clear() {
        mutableStats.value = SteamHostSessionStats()
    }
}
