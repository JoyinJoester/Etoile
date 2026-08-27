package takagi.ru.monica.steam.network.optimization.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute

@OptIn(ExperimentalCoroutinesApi::class)
class SteamNetworkOptimizationViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scanWaitsForExplicitApplyAndPassesExistingRoutesForComparison() = runTest(scheduler) {
        val releaseScan = CompletableDeferred<Unit>()
        var applied = false
        var scannedExistingRoutes = emptyList<SteamDnsSelectedRoute>()
        val viewModel = SteamNetworkOptimizationViewModel(
            scan = { providers, existingRoutes, onProgress ->
                assertEquals(SteamDnsProvider.DEFAULTS, providers)
                scannedExistingRoutes = existingRoutes
                onProgress(
                    SteamDnsScanProgress(
                        stage = SteamDnsScanStage.VERIFYING,
                        completed = 1,
                        total = 2
                    )
                )
                releaseScan.await()
                partialResult()
            }
        )
        val existingRoutes = listOf(
            SteamDnsSelectedRoute(
                hostname = "store.steampowered.com",
                address = "9.9.9.9",
                providerIds = listOf("system"),
                latencyMillis = 20L,
                httpStatusCode = 200
            )
        )

        viewModel.startScan(existingRoutes)
        runCurrent()
        assertTrue(viewModel.scanState.value is SteamAutoOptimizationUiState.Running)

        releaseScan.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.scanState.value
        assertTrue(state is SteamAutoOptimizationUiState.Success)
        assertTrue(!applied)
        assertEquals(existingRoutes, scannedExistingRoutes)
        assertEquals(
            2,
            (state as SteamAutoOptimizationUiState.Success).result.availableHostCount
        )
        assertTrue(!state.applied)

        viewModel.applyScannedOptimization { result ->
            applied = true
            result.isApplicable
        }
        advanceUntilIdle()

        assertTrue(applied)
        assertTrue(
            (viewModel.scanState.value as SteamAutoOptimizationUiState.Success).applied
        )
    }

    private fun partialResult(): SteamDnsOptimizationScanResult =
        SteamDnsOptimizationScanResult(
            targetHostnames = listOf(
                "store.steampowered.com",
                "steamcommunity.com",
                "api.steampowered.com"
            ),
            resolutions = emptyList(),
            probeResults = emptyList(),
            selectedRoutes = listOf(
                SteamDnsSelectedRoute(
                    hostname = "store.steampowered.com",
                    address = "1.1.1.1",
                    providerIds = listOf("system"),
                    latencyMillis = 30L,
                    httpStatusCode = 200
                ),
                SteamDnsSelectedRoute(
                    hostname = "steamcommunity.com",
                    address = "1.1.1.2",
                    providerIds = listOf("system"),
                    latencyMillis = 40L,
                    httpStatusCode = 200
                )
            )
        )
}
