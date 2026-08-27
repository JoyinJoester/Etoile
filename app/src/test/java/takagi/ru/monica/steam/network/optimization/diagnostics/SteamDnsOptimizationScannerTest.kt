package takagi.ru.monica.steam.network.optimization.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus

class SteamDnsOptimizationScannerTest {
    private val providerA = SteamDnsProvider("a", "Provider A")
    private val providerB = SteamDnsProvider("b", "Provider B")
    private val hostA = "store.steampowered.com"
    private val hostB = "steamcommunity.com"

    @Test
    fun selectsFastestVerifiedAddressAndKeepsAllSourcesForDuplicates() = runBlocking {
        val progress = mutableListOf<SteamDnsScanProgress>()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                val addresses = when (provider.id to hostname) {
                    "a" to hostA -> listOf("10.0.0.1")
                    "b" to hostA -> listOf("10.0.0.2")
                    "a" to hostB,
                    "b" to hostB -> listOf("20.0.0.1")
                    else -> emptyList()
                }
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = addresses,
                    latencyMillis = if (provider.id == "a") 20L else 30L
                )
            },
            probe = SteamHostProbe { target ->
                val latency = when (target.address) {
                    "10.0.0.1" -> 90L
                    "10.0.0.2" -> 25L
                    else -> 40L
                }
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = latency,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA, providerB),
            targetHostnames = listOf(hostA, hostB),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1,
            maxConcurrentProbes = 2
        )

        val result = scanner.scan(progress::add)

        assertTrue(result.isComplete)
        assertEquals("10.0.0.2", result.selectedRoutes.first { it.hostname == hostA }.address)
        assertEquals(
            listOf("a", "b"),
            result.selectedRoutes.first { it.hostname == hostB }.providerIds
        )
        assertEquals(32L, result.averageLatencyMillis)
        assertEquals(4, progress.count { it.stage.name == "RESOLVING" })
        assertEquals(3, progress.count { it.stage.name == "VERIFYING" })
    }

    @Test
    fun incompleteVerificationNeverReportsACompleteOptimization() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = if (hostname == hostA) listOf("10.0.0.1") else emptyList()
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 20L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA, hostB),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1,
            minimumRecoveryProbeAttemptsPerCandidate = 1,
            minimumRecoveryProbeAttemptsPerHost = 1
        )

        val result = scanner.scan()

        assertFalse(result.isComplete)
        assertEquals(listOf(hostB), result.missingHostnames)
    }

    @Test
    fun preferredRouteIsRetestedBeforeItCanWin() = runBlocking {
        val probeCounts = ConcurrentHashMap<String, AtomicInteger>()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.2")
                )
            },
            probe = SteamHostProbe { target ->
                probeCounts.computeIfAbsent(target.address) { AtomicInteger() }.incrementAndGet()
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = if (target.address == "10.0.0.1") 15L else 30L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 2,
            minimumProbeAttemptsPerHost = 2,
            maxConcurrentProbes = 1
        )

        val result = scanner.scan(
            preferredRoutes = listOf(
                SteamDnsSelectedRoute(
                    hostname = hostA,
                    address = "10.0.0.1",
                    providerIds = listOf(providerA.id),
                    latencyMillis = 5L,
                    httpStatusCode = 200
                )
            )
        )

        assertTrue(probeCounts["10.0.0.1"]?.get() ?: 0 >= 2)
        assertEquals("10.0.0.1", result.selectedRoutes.single().address)
    }

    @Test
    fun verificationRequiresRepeatedSuccessInsteadOfOneLuckyProbe() = runBlocking {
        val attempts = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                val attempt = attempts.incrementAndGet()
                SteamHostProbeResult(
                    target = target,
                    status = if (attempt == 1) {
                        SteamHostProbeStatus.AVAILABLE
                    } else {
                        SteamHostProbeStatus.TIMEOUT
                    },
                    latencyMillis = if (attempt == 1) 24L else 5_000L,
                    httpStatusCode = if (attempt == 1) 200 else null
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 3,
            minimumProbeAttemptsPerHost = 3,
            minimumRecoveryProbeAttemptsPerCandidate = 1,
            minimumRecoveryProbeAttemptsPerHost = 1,
            maxConcurrentProbes = 1
        )

        val result = scanner.scan()

        assertFalse(result.isComplete)
    }

    @Test
    fun medianLatencyProtectsSelectionFromOneSlowOutlier() = runBlocking {
        val attempts = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                val attempt = attempts.incrementAndGet()
                val latency = if (attempt == 3) 3_000L else 24L
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = latency,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 3,
            minimumProbeAttemptsPerHost = 3,
            maxConcurrentProbes = 1
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals(24L, result.selectedRoutes.single().latencyMillis)
    }

    @Test
    fun majorityOfSuccessfulChecksKeepsAMobileCandidateUsable() = runBlocking {
        val attempts = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                val attempt = attempts.incrementAndGet()
                SteamHostProbeResult(
                    target = target,
                    status = if (attempt <= 3) {
                        SteamHostProbeStatus.AVAILABLE
                    } else {
                        SteamHostProbeStatus.TIMEOUT
                    },
                    latencyMillis = if (attempt <= 3) 40L else 5_000L,
                    httpStatusCode = if (attempt <= 3) 200 else null
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 5,
            minimumProbeAttemptsPerHost = 5,
            maxConcurrentProbes = 1
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals("10.0.0.1", result.selectedRoutes.single().address)
    }

    @Test
    fun defaultBudgetRunsAtLeastOneHundredHttpsChecks() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                val hostIndex = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES
                    .indexOf(hostname)
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.${hostIndex + 1}")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 30L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA)
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertTrue(result.probeResults.size >= 100)
        assertTrue(
            SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.all { hostname ->
                result.probeResults.any { it.target.hostname == hostname }
            }
        )
    }

    @Test
    fun missingHostIsResolvedAndVerifiedAgainBeforeFinishing() = runBlocking {
        val resolveCalls = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                resolveCalls.incrementAndGet()
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TIMEOUT,
                    latencyMillis = 5_000L
                )
            },
            recoveryProbe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 45L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1,
            minimumRecoveryProbeAttemptsPerCandidate = 2,
            minimumRecoveryProbeAttemptsPerHost = 2
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals(2, resolveCalls.get())
        assertEquals(3, result.probeResults.size)
        assertEquals(45L, result.selectedRoutes.single().latencyMillis)
    }

    @Test
    fun verifiedExistingRouteIsKeptWhenItBeatsNewResolverResults() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.2")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = if (target.address == "10.0.0.1") 18L else 60L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1
        )

        val result = scanner.scan(
            preferredRoutes = listOf(
                SteamDnsSelectedRoute(
                    hostname = hostA,
                    address = "10.0.0.1",
                    providerIds = listOf("system"),
                    latencyMillis = 25L,
                    httpStatusCode = 200
                )
            )
        )

        assertEquals("10.0.0.1", result.selectedRoutes.single().address)
        assertEquals(18L, result.selectedRoutes.single().latencyMillis)
        assertEquals(2, result.probeResults.size)
    }
}
