package takagi.ru.monica.steam.network.optimization.diagnostics

import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget

fun interface SteamHostProbe {
    suspend fun probe(target: SteamHostProbeTarget): SteamHostProbeResult
}
