package takagi.ru.monica.steam.library.screenshots.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.screenshots.data.SteamGameScreenshotsService
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshot
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsPage

internal data class SteamGameScreenshotsUiState(
    val screenshots: List<SteamGameScreenshot> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val loadMoreFailed: Boolean = false
)

internal class SteamGameScreenshotsViewModel(
    private val service: SteamGameScreenshotsService = SteamGameScreenshotsService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamGameScreenshotsUiState())
    val uiState: StateFlow<SteamGameScreenshotsUiState> = _uiState.asStateFlow()

    private var account: SteamAccount? = null
    private var target: SteamGameScreenshotsPage? = null
    private var nextPage = 1
    private var generation = 0L
    private var loadJob: Job? = null

    fun attach(account: SteamAccount, target: SteamGameScreenshotsPage) {
        if (this.account == account && this.target == target) return
        this.account = account
        this.target = target
        nextPage = 1
        loadJob?.cancel()
        generation++
        _uiState.value = SteamGameScreenshotsUiState(loading = true)
        loadFirstPage(showRefreshIndicator = false)
    }

    fun refresh() {
        if (account == null || target == null) return
        loadFirstPage(showRefreshIndicator = _uiState.value.screenshots.isNotEmpty())
    }

    fun loadMore() {
        val selectedAccount = account ?: return
        val selectedTarget = target ?: return
        val current = _uiState.value
        if (
            current.loading || current.refreshing || current.loadingMore ||
            !current.hasMore
        ) return
        val requestedPage = nextPage
        val requestGeneration = ++generation
        loadJob?.cancel()
        _uiState.value = current.copy(
            loadingMore = true,
            loadMoreFailed = false
        )
        loadJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    service.fetch(selectedAccount, selectedTarget, requestedPage)
                }
            }
            if (generation != requestGeneration) return@launch
            result.onSuccess { batch ->
                val previous = _uiState.value.screenshots
                val merged = (previous + batch.screenshots)
                    .distinctBy(SteamGameScreenshot::publishedFileId)
                val appended = merged.size > previous.size
                nextPage = requestedPage + 1
                _uiState.value = _uiState.value.copy(
                    screenshots = merged,
                    loadingMore = false,
                    hasMore = batch.hasMore && appended,
                    loadMoreFailed = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    loadMoreFailed = true
                )
            }
        }
    }

    private fun loadFirstPage(showRefreshIndicator: Boolean) {
        val selectedAccount = account ?: return
        val selectedTarget = target ?: return
        val requestGeneration = ++generation
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            loading = !showRefreshIndicator,
            refreshing = showRefreshIndicator,
            loadingMore = false,
            loadFailed = false,
            loadMoreFailed = false
        )
        loadJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    service.fetch(selectedAccount, selectedTarget, FIRST_PAGE)
                }
            }
            if (generation != requestGeneration) return@launch
            result.onSuccess { batch ->
                nextPage = FIRST_PAGE + 1
                _uiState.value = SteamGameScreenshotsUiState(
                    screenshots = batch.screenshots,
                    hasMore = batch.hasMore
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    loadFailed = true
                )
            }
        }
    }

    companion object {
        private const val FIRST_PAGE = 1

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SteamGameScreenshotsViewModel() as T
        }
    }
}
