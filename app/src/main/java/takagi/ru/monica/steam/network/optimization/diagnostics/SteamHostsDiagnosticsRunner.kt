package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRule

internal class SteamHostsDiagnosticsRunner(
    private val probe: SteamHostProbe = OkHttpSteamHostProbe()
) {
    suspend fun run(
        rules: List<SteamHostsRule>,
        onResult: (SteamHostProbeResult) -> Unit = {}
    ): List<SteamHostProbeResult> {
        val results = mutableListOf<SteamHostProbeResult>()
        rules.forEach { rule ->
            rule.addresses.forEach { address ->
                coroutineContext.ensureActive()
                val result = probe.probe(
                    SteamHostProbeTarget(
                        hostname = rule.hostname,
                        address = address
                    )
                )
                results += result
                onResult(result)
            }
        }
        return results
    }
}
