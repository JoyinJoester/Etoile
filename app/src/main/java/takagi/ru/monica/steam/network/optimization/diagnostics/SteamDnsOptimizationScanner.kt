package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkTargetCatalog

internal class SteamDnsOptimizationScanner(
    private val resolver: SteamDnsResolver = OkHttpSteamDnsResolver(),
    private val probe: SteamHostProbe = OkHttpSteamHostProbe(),
    private val recoveryProbe: SteamHostProbe = OkHttpSteamHostProbe(timeoutMillis = 7_000L),
    private val providers: List<SteamDnsProvider> = SteamDnsProvider.DEFAULTS,
    private val targetHostnames: List<String> = DEFAULT_TARGET_HOSTNAMES,
    private val maxCandidatesPerHost: Int = 12,
    private val minimumProbeAttemptsPerCandidate: Int = 5,
    private val minimumProbeAttemptsPerHost: Int = 36,
    private val minimumProbeAttemptsByHost: Map<String, Int> =
        if (targetHostnames == DEFAULT_TARGET_HOSTNAMES) DEFAULT_PROBE_ATTEMPTS else emptyMap(),
    private val minimumRecoveryProbeAttemptsPerCandidate: Int = 3,
    private val minimumRecoveryProbeAttemptsPerHost: Int = 12,
    private val maxConcurrentProbes: Int = 8
) {
    suspend fun scan(
        onProgress: (SteamDnsScanProgress) -> Unit = {},
        preferredRoutes: List<SteamDnsSelectedRoute> = emptyList()
    ): SteamDnsOptimizationScanResult = coroutineScope {
        var resolutions = resolveHosts(
            hostnames = targetHostnames,
            stage = SteamDnsScanStage.RESOLVING,
            onProgress = onProgress
        )
        var candidates = buildCandidates(targetHostnames, resolutions, preferredRoutes)
        var probeResults = runProbeTasks(
            tasks = buildProbeTasks(
                candidates = candidates,
                minimumAttemptsPerCandidate = minimumProbeAttemptsPerCandidate,
                minimumAttemptsForHost = { hostname ->
                    minimumProbeAttemptsByHost[hostname] ?: minimumProbeAttemptsPerHost
                }
            ),
            probeEngine = probe,
            stage = SteamDnsScanStage.VERIFYING,
            onProgress = onProgress
        )
        var selectedRoutes = selectRoutes(candidates, probeResults)
        val missingHostnames = targetHostnames.filterNot { hostname ->
            selectedRoutes.any { route -> route.hostname.equals(hostname, ignoreCase = true) }
        }

        if (missingHostnames.isNotEmpty()) {
            val recoveredResolutions = resolveHosts(
                hostnames = missingHostnames,
                stage = SteamDnsScanStage.RECOVERING,
                onProgress = onProgress
            )
            resolutions = (resolutions + recoveredResolutions).distinct()
            val recoveryCandidates = buildCandidates(
                hostnames = missingHostnames,
                resolutions = resolutions,
                preferredRoutes = preferredRoutes
            )
            candidates = mergeCandidates(candidates + recoveryCandidates)
            probeResults += runProbeTasks(
                tasks = buildProbeTasks(
                    candidates = recoveryCandidates,
                    minimumAttemptsPerCandidate = minimumRecoveryProbeAttemptsPerCandidate,
                    minimumAttemptsForHost = { minimumRecoveryProbeAttemptsPerHost }
                ),
                probeEngine = recoveryProbe,
                stage = SteamDnsScanStage.RECOVERING,
                onProgress = onProgress
            )
            selectedRoutes = selectRoutes(candidates, probeResults)
        }

        SteamDnsOptimizationScanResult(
            targetHostnames = targetHostnames,
            resolutions = resolutions,
            probeResults = probeResults,
            selectedRoutes = selectedRoutes
        )
    }

    private suspend fun resolveHosts(
        hostnames: List<String>,
        stage: SteamDnsScanStage,
        onProgress: (SteamDnsScanProgress) -> Unit
    ): List<SteamDnsResolutionResult> = coroutineScope {
        val tasks = providers.flatMap { provider ->
            hostnames.map { hostname -> provider to hostname }
        }
        val deferred = tasks.map { (provider, hostname) ->
            async(Dispatchers.IO) { resolveSafely(provider, hostname) }
        }
        deferred.mapIndexed { index, result ->
            result.await().also { resolution ->
                onProgress(
                    SteamDnsScanProgress(
                        stage = stage,
                        completed = index + 1,
                        total = deferred.size,
                        currentSource = resolution.provider.displayName
                    )
                )
            }
        }
    }

    private suspend fun runProbeTasks(
        tasks: List<SteamDnsCandidate>,
        probeEngine: SteamHostProbe,
        stage: SteamDnsScanStage,
        onProgress: (SteamDnsScanProgress) -> Unit
    ): List<SteamHostProbeResult> = coroutineScope {
        if (tasks.isEmpty()) return@coroutineScope emptyList()
        val probeSemaphore = Semaphore(maxConcurrentProbes.coerceAtLeast(1))
        val resultChannel = Channel<SteamHostProbeResult>(Channel.UNLIMITED)
        val jobs = tasks.map { candidate ->
            launch(Dispatchers.IO) {
                val result = probeSemaphore.withPermit {
                    probeSafely(probeEngine, candidate.hostname, candidate.address)
                }
                resultChannel.send(result)
            }
        }
        buildList {
            repeat(tasks.size) { index ->
                val result = resultChannel.receive()
                add(result)
                onProgress(
                    SteamDnsScanProgress(
                        stage = stage,
                        completed = index + 1,
                        total = tasks.size,
                        currentSource = result.target.hostname
                    )
                )
            }
            jobs.joinAll()
            resultChannel.close()
        }
    }

    private suspend fun resolveSafely(
        provider: SteamDnsProvider,
        hostname: String
    ): SteamDnsResolutionResult = try {
        resolver.resolve(provider, hostname)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamDnsResolutionResult(
            provider = provider,
            hostname = hostname,
            errorType = error::class.java.simpleName
        )
    }

    private suspend fun probeSafely(
        probeEngine: SteamHostProbe,
        hostname: String,
        address: String
    ): SteamHostProbeResult = try {
        probeEngine.probe(SteamHostProbeTarget(hostname, address))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamHostProbeResult(
            target = SteamHostProbeTarget(hostname, address),
            status = SteamHostProbeStatus.CONNECTION_ERROR,
            errorType = error::class.java.simpleName
        )
    }

    private fun buildCandidates(
        hostnames: List<String>,
        resolutions: List<SteamDnsResolutionResult>,
        preferredRoutes: List<SteamDnsSelectedRoute>
    ): List<SteamDnsCandidate> = hostnames.flatMap { hostname ->
        val hostResolutions = resolutions
            .asSequence()
            .filter { it.hostname.equals(hostname, ignoreCase = true) && it.isAvailable }
            .sortedBy { it.latencyMillis ?: Long.MAX_VALUE }
            .toList()
        val providerIdsByAddress = linkedMapOf<String, MutableSet<String>>()
        hostResolutions.forEach { resolution ->
            resolution.addresses.forEach { address ->
                providerIdsByAddress.getOrPut(address) { linkedSetOf() }
                    .add(resolution.provider.id)
            }
        }
        val preferredHostRoutes = preferredRoutes
            .filter { route -> route.hostname.equals(hostname, ignoreCase = true) }
            .distinctBy(SteamDnsSelectedRoute::address)
        preferredHostRoutes.forEach { route ->
            providerIdsByAddress.getOrPut(route.address) { linkedSetOf() }
                .addAll(route.providerIds)
        }

        val roundRobinAddresses = buildList {
            val largestAnswer = hostResolutions.maxOfOrNull { it.addresses.size } ?: 0
            repeat(largestAnswer) { addressIndex ->
                hostResolutions.forEach { resolution ->
                    resolution.addresses.getOrNull(addressIndex)?.let { address ->
                        if (address !in this) add(address)
                    }
                }
            }
        }

        buildList {
            preferredHostRoutes.forEach { route ->
                if (route.address !in this) add(route.address)
            }
            roundRobinAddresses
                .take(maxCandidatesPerHost.coerceAtLeast(1))
                .forEach { address ->
                    if (address !in this) add(address)
                }
        }.map { address ->
            SteamDnsCandidate(
                hostname = hostname,
                address = address,
                providerIds = providerIdsByAddress[address].orEmpty().toList()
            )
        }
    }

    private fun mergeCandidates(candidates: List<SteamDnsCandidate>): List<SteamDnsCandidate> =
        candidates.groupBy(SteamDnsCandidate::key).values.map { duplicates ->
            val first = duplicates.first()
            first.copy(providerIds = duplicates.flatMap { it.providerIds }.distinct())
        }

    private fun buildProbeTasks(
        candidates: List<SteamDnsCandidate>,
        minimumAttemptsPerCandidate: Int,
        minimumAttemptsForHost: (String) -> Int
    ): List<SteamDnsCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val candidateCountByHost = candidates.groupingBy(SteamDnsCandidate::hostname).eachCount()
        val attemptsByCandidateKey = candidates.associate { candidate ->
            val hostCandidateCount = candidateCountByHost[candidate.hostname].orEmptyCount()
            val hostBudgetAttempts = divideRoundingUp(
                minimumAttemptsForHost(candidate.hostname).coerceAtLeast(1),
                hostCandidateCount
            )
            candidate.key to maxOf(
                minimumAttemptsPerCandidate.coerceAtLeast(1),
                hostBudgetAttempts
            )
        }
        val largestAttemptCount = attemptsByCandidateKey.values.maxOrNull() ?: 0
        return buildList {
            repeat(largestAttemptCount) { attemptIndex ->
                candidates.forEach { candidate ->
                    if (attemptIndex < attemptsByCandidateKey.getValue(candidate.key)) add(candidate)
                }
            }
        }
    }

    private fun selectRoutes(
        candidates: List<SteamDnsCandidate>,
        probeResults: List<SteamHostProbeResult>
    ): List<SteamDnsSelectedRoute> {
        val candidateSources = candidates.associate { it.key to it.providerIds }
        val evaluations = candidates.mapNotNull { candidate ->
            evaluateCandidate(
                candidate = candidate,
                results = probeResults.filter { result -> result.target.key == candidate.key }
            )
        }
        return targetHostnames.mapNotNull { hostname ->
            evaluations
                .asSequence()
                .filter { evaluation ->
                    evaluation.candidate.hostname == hostname && evaluation.isStable
                }
                .minWithOrNull(
                    compareByDescending<SteamDnsCandidateEvaluation> { it.successPercent }
                        .thenBy { it.medianLatencyMillis }
                        .thenBy { it.p90LatencyMillis }
                        .thenByDescending { it.successfulProbeCount }
                        .thenBy { it.candidate.address }
                )
                ?.let { evaluation ->
                    SteamDnsSelectedRoute(
                        hostname = hostname,
                        address = evaluation.candidate.address,
                        providerIds = candidateSources[evaluation.candidate.key].orEmpty(),
                        latencyMillis = evaluation.medianLatencyMillis,
                        httpStatusCode = evaluation.httpStatusCode
                    )
                }
        }
    }

    private fun evaluateCandidate(
        candidate: SteamDnsCandidate,
        results: List<SteamHostProbeResult>
    ): SteamDnsCandidateEvaluation? {
        val successfulResults = results.filter(SteamHostProbeResult::isAvailable)
        val latencies = successfulResults.mapNotNull(SteamHostProbeResult::latencyMillis).sorted()
        if (successfulResults.isEmpty() || latencies.isEmpty()) return null
        return SteamDnsCandidateEvaluation(
            candidate = candidate,
            successfulProbeCount = successfulResults.size,
            totalProbeCount = results.size,
            medianLatencyMillis = percentile(latencies, 0.5),
            p90LatencyMillis = percentile(latencies, 0.9),
            httpStatusCode = successfulResults.firstNotNullOfOrNull { it.httpStatusCode }
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        if (sortedValues.isEmpty()) return Long.MAX_VALUE
        val position = sortedValues.lastIndex * percentile.coerceIn(0.0, 1.0)
        val lowerIndex = position.toInt().coerceIn(sortedValues.indices)
        val upperIndex = kotlin.math.ceil(position).toInt().coerceIn(sortedValues.indices)
        if (lowerIndex == upperIndex) return sortedValues[lowerIndex]
        val lower = sortedValues[lowerIndex]
        val upper = sortedValues[upperIndex]
        val weight = position - lowerIndex
        return (lower + (upper - lower) * weight).toLong()
    }

    private fun divideRoundingUp(value: Int, divisor: Int): Int =
        (value + divisor.coerceAtLeast(1) - 1) / divisor.coerceAtLeast(1)

    private fun Int?.orEmptyCount(): Int = this?.coerceAtLeast(1) ?: 1

    private data class SteamDnsCandidate(
        val hostname: String,
        val address: String,
        val providerIds: List<String>
    ) {
        val key: String get() = "$hostname|$address"
    }

    private data class SteamDnsCandidateEvaluation(
        val candidate: SteamDnsCandidate,
        val successfulProbeCount: Int,
        val totalProbeCount: Int,
        val medianLatencyMillis: Long,
        val p90LatencyMillis: Long,
        val httpStatusCode: Int?
    ) {
        val successPercent: Int
            get() = if (totalProbeCount <= 0) 0 else successfulProbeCount * 100 / totalProbeCount
        val isStable: Boolean
            get() = totalProbeCount > 0 &&
                successfulProbeCount * 100 >= totalProbeCount * MIN_SUCCESS_PERCENT
    }

    companion object {
        private const val MIN_SUCCESS_PERCENT = 60

        val DEFAULT_TARGET_HOSTNAMES: List<String> = SteamNetworkTargetCatalog.hostnames
        val DEFAULT_PROBE_ATTEMPTS: Map<String, Int> =
            SteamNetworkTargetCatalog.minimumProbeAttemptsByHost
    }
}
