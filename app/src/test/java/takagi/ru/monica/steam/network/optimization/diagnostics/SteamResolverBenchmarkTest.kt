package takagi.ru.monica.steam.network.optimization.diagnostics

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult

class SteamResolverBenchmarkTest {
    @Test
    fun boundsConcurrentResolutionWorkAcrossBenchmarks() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val resolver = SteamDnsResolver { provider, hostname ->
            val running = active.incrementAndGet()
            peak.updateAndGet { current -> maxOf(current, running) }
            try {
                delay(40)
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("104.18.20.10"),
                    latencyMillis = 40L
                )
            } finally {
                active.decrementAndGet()
            }
        }
        val benchmark = SteamResolverBenchmark(
            resolver = resolver,
            hostnames = List(8) { index -> "host-$index.steampowered.com" },
            maxConcurrentResolutions = 2
        )

        val results = coroutineScope {
            List(3) {
                async { benchmark.benchmark(SteamDnsProvider.CLOUDFLARE) }
            }.awaitAll()
        }

        assertTrue(results.all { it.successfulHosts == 8 })
        assertEquals(24, results.sumOf { it.successfulHosts })
        assertTrue(peak.get() <= 2)
    }
}
