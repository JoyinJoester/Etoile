package takagi.ru.monica.steam.network.optimization.domain

data class SteamNetworkOptimizationSettings(
    val enabled: Boolean = false,
    val hostsText: String = "",
    val hostCount: Int = 0,
    val fallbackToSystemDns: Boolean = true
)
