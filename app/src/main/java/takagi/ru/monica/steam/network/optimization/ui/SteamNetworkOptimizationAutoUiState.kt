package takagi.ru.monica.steam.network.optimization.ui

import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress

internal sealed interface SteamAutoOptimizationUiState {
    data object Idle : SteamAutoOptimizationUiState
    data class Running(val progress: SteamDnsScanProgress) : SteamAutoOptimizationUiState
    data object Applying : SteamAutoOptimizationUiState
    data class Success(
        val result: SteamDnsOptimizationScanResult,
        val applied: Boolean = false
    ) : SteamAutoOptimizationUiState

    data class Error(
        val availableHostCount: Int,
        val totalHostCount: Int,
        val applyFailed: Boolean = false
    ) : SteamAutoOptimizationUiState
}

internal val SteamAutoOptimizationUiState.isBusy: Boolean
    get() = this is SteamAutoOptimizationUiState.Running ||
        this === SteamAutoOptimizationUiState.Applying
