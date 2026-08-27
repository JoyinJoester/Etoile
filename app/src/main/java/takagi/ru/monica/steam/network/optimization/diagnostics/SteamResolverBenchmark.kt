package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider

internal data class SteamResolverBenchmarkResult(
    val providerId: String,
    val successfulHosts: Int,
    val totalHosts: Int,
    val averageLatencyMillis: Long?,
    val errorTypes: List<String>
) {
    val isAvailable: Boolean get() = successfulHosts > 0
}

/**
 * Lightweight resolver-only benchmark for the detailed DoH settings page.
 *
 * This does not replace the full Steam route scan: it only measures how quickly a resolver can
 * return usable answers for representative Steam mobile endpoints. The full optimizer still
 * performs HTTPS/SNI/certificate verification before learning preferred resolver order.
 */
internal class SteamResolverBenchmark(
    private val resolver: SteamDnsResolver = OkHttpSteamDnsResolver(timeoutMillis = 3_000L),
    private val hostnames: List<String> = DEFAULT_HOSTNAMES,
    maxConcurrentResolutions: Int = MAX_CONCURRENT_RESOLUTIONS
) {
    private val resolutionSemaphore = Semaphore(maxConcurrentResolutions.coerceAtLeast(1))

    suspend fun benchmark(provider: SteamDnsProvider): SteamResolverBenchmarkResult =
        withContext(Dispatchers.IO) {
            val results = coroutineScope {
                hostnames.map { hostname ->
                    async {
                        resolutionSemaphore.withPermit {
                            resolver.resolve(provider, hostname)
                        }
                    }
                }.awaitAll()
            }
            val successful = results.filter { it.isAvailable }
            SteamResolverBenchmarkResult(
                providerId = provider.id,
                successfulHosts = successful.size,
                totalHosts = hostnames.size,
                averageLatencyMillis = successful
                    .mapNotNull { it.latencyMillis }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toLong(),
                errorTypes = results.mapNotNull { it.errorType }.distinct()
            )
        }

    companion object {
        private const val MAX_CONCURRENT_RESOLUTIONS = 6

        val DEFAULT_HOSTNAMES: List<String> = listOf(
            "store.steampowered.com",
            "steamcommunity.com",
            "api.steampowered.com",
            "login.steampowered.com"
        )
    }
}
