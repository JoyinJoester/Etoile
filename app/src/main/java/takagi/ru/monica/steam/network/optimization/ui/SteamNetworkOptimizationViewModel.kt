package takagi.ru.monica.steam.network.optimization.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsOptimizationScanner
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute

internal class SteamNetworkOptimizationViewModel(
    private val scan: suspend (
        List<SteamDnsProvider>,
        List<SteamDnsSelectedRoute>,
        (SteamDnsScanProgress) -> Unit
    ) ->
        SteamDnsOptimizationScanResult = createDefaultNetworkScan()
) : ViewModel() {
    private val mutableScanState = MutableStateFlow<SteamAutoOptimizationUiState>(
        SteamAutoOptimizationUiState.Idle
    )
    val scanState: StateFlow<SteamAutoOptimizationUiState> = mutableScanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(
        existingRoutes: List<SteamDnsSelectedRoute> = emptyList(),
        providers: List<SteamDnsProvider> = SteamDnsProvider.DEFAULTS
    ) {
        if (scanJob?.isActive == true || mutableScanState.value.isBusy) return
        if (providers.isEmpty()) return
        scanJob = viewModelScope.launch {
            mutableScanState.value = SteamAutoOptimizationUiState.Running(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.RESOLVING,
                    completed = 0,
                    total = providers.size *
                        SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            )
            try {
                val result = scan(providers, existingRoutes) { progress ->
                    mutableScanState.value = SteamAutoOptimizationUiState.Running(progress)
                }
                if (!result.isApplicable) {
                    mutableScanState.value = SteamAutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount
                    )
                    return@launch
                }
                mutableScanState.value = SteamAutoOptimizationUiState.Success(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableScanState.value = SteamAutoOptimizationUiState.Error(
                    availableHostCount = 0,
                    totalHostCount = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            }
        }
    }

    fun applyScannedOptimization(
        applyOptimization: (SteamDnsOptimizationScanResult) -> Boolean
    ) {
        val scanned = mutableScanState.value as? SteamAutoOptimizationUiState.Success ?: return
        if (scanned.applied) return
        mutableScanState.value = SteamAutoOptimizationUiState.Applying
        val applied = runCatching {
            applyOptimization(scanned.result)
        }.getOrDefault(false)
        mutableScanState.value = if (applied) {
            scanned.copy(applied = true)
        } else {
            SteamAutoOptimizationUiState.Error(
                availableHostCount = scanned.result.availableHostCount,
                totalHostCount = scanned.result.totalHostCount,
                applyFailed = true
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        mutableScanState.value = SteamAutoOptimizationUiState.Idle
    }
}

private fun createDefaultNetworkScan(): suspend (
    List<SteamDnsProvider>,
    List<SteamDnsSelectedRoute>,
    (SteamDnsScanProgress) -> Unit
) ->
    SteamDnsOptimizationScanResult {
    return { providers, existingRoutes, onProgress ->
        SteamDnsOptimizationScanner(providers = providers).scan(
            onProgress = onProgress,
            preferredRoutes = existingRoutes
        )
    }
}
