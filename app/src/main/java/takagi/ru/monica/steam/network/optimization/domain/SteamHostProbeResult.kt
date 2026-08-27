package takagi.ru.monica.steam.network.optimization.domain

data class SteamHostProbeTarget(
    val hostname: String,
    val address: String
) {
    val key: String get() = "$hostname|$address"
}

enum class SteamHostProbeStatus {
    AVAILABLE,
    TIMEOUT,
    TLS_ERROR,
    CONNECTION_ERROR
}

data class SteamHostProbeResult(
    val target: SteamHostProbeTarget,
    val status: SteamHostProbeStatus,
    val latencyMillis: Long? = null,
    val httpStatusCode: Int? = null,
    val errorType: String? = null
) {
    val isAvailable: Boolean get() = status == SteamHostProbeStatus.AVAILABLE
}
