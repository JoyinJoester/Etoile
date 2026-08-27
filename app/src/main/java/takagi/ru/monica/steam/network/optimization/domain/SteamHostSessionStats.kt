package takagi.ru.monica.steam.network.optimization.domain

data class SteamHostHitStats(
    val hitCount: Long = 0L,
    val lastHitAtEpochMillis: Long = 0L
)

data class SteamHostSessionStats(
    val totalHitCount: Long = 0L,
    val hosts: Map<String, SteamHostHitStats> = emptyMap()
)
