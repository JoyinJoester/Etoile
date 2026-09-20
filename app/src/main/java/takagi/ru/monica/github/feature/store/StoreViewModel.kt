package takagi.ru.monica.github.feature.store

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import takagi.ru.monica.github.domain.StoreReleaseResolver
import takagi.ru.monica.github.domain.FdroidSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.data.GithubApkInsightsStore
import takagi.ru.monica.github.data.GithubApkManager
import takagi.ru.monica.github.data.GithubFdroidIndexRepository
import takagi.ru.monica.github.data.GithubStoreSourceStore
import takagi.ru.monica.github.domain.FdroidApp
import takagi.ru.monica.github.domain.GithubApkDetails
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.GithubStoreApp
import takagi.ru.monica.github.domain.GithubStoreSource
import takagi.ru.monica.github.domain.mergeItems
import java.io.File

/**
 * 商店目录的搜索条件：标记为 Android 应用的开源仓库按星标排序，
 * 详情页再拉取最新 Release 并解析 APK 资产。
 */
private const val STORE_CATALOG_QUERY = "topic:android-app stars:>100 archived:false fork:false sort:stars-desc"

enum class StoreCatalogTab { RECOMMENDED, FDROID }
enum class FdroidCatalogFilter { ALL, INSTALLED, UPDATES }

/** 单个 APK 的下载/解析进度。key 为 GitHub 资产 id 或 F-Droid apkName。 */
@Immutable
data class ApkDownloadUiState(
    val progress: Float? = null,
    val file: File? = null,
    val details: GithubApkDetails? = null,
    val error: Boolean = false
)

@Immutable
data class StoreUiState(
    val apps: List<GithubRepository> = emptyList(),
    val sources: List<GithubRepository> = emptyList(),
    val sourceInput: String = "",
    val sourceInputError: Boolean = false,
    val isLoadingSources: Boolean = false,
    val query: String = "",
    val catalogTab: StoreCatalogTab = StoreCatalogTab.FDROID,
    val fdroidApps: List<FdroidApp> = emptyList(),
    val fdroidFilter: FdroidCatalogFilter = FdroidCatalogFilter.ALL,
    val isLoadingFdroid: Boolean = false,
    val fdroidError: Boolean = false,
    val enabledFdroidSources: Set<String> = FdroidSources.defaults,
    val failedFdroidSources: List<String> = emptyList(),
    val fdroidInstalled: Map<String, String> = emptyMap(),
    val updates: List<FdroidApp> = emptyList(),
    val nextPage: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val selected: GithubStoreApp? = null,
    val selectedFdroid: FdroidApp? = null,
    val installedVersion: String? = null,
    val downloads: Map<String, ApkDownloadUiState> = emptyMap(),
    val isLoadingDetail: Boolean = false,
    val detailError: Boolean = false
) {
    val filteredApps: List<GithubRepository>
        get() = query.trim().takeIf(String::isNotBlank)?.let { value ->
            apps.filter {
                it.fullName.contains(value, ignoreCase = true) ||
                    it.description?.contains(value, ignoreCase = true) == true
            }
        } ?: apps
    val filteredSources: List<GithubRepository>
        get() = query.trim().takeIf(String::isNotBlank)?.let { value ->
            sources.filter { it.fullName.contains(value, ignoreCase = true) }
        } ?: sources
    val filteredFdroidApps: List<FdroidApp>
        get() {
            val base = when (fdroidFilter) {
                FdroidCatalogFilter.ALL -> fdroidApps
                FdroidCatalogFilter.INSTALLED -> fdroidApps.filter { it.packageName in fdroidInstalled }
                FdroidCatalogFilter.UPDATES -> updates
            }
            val term = query.trim()
            return if (term.isEmpty()) base else base.filter {
                it.name.contains(term, true) || it.packageName.contains(term, true) || it.summary?.contains(term, true) == true
            }
        }
    val canLoadMore: Boolean get() = nextPage != null && !isLoading && !isLoadingMore
}

sealed interface StoreAction {
    data class FilterFdroid(val filter: FdroidCatalogFilter) : StoreAction
    data object NextApp : StoreAction
    data class ToggleFdroidSource(val url: String, val enabled: Boolean) : StoreAction
    data object Refresh : StoreAction
    data object Retry : StoreAction
    data object LoadMore : StoreAction
    data class Search(val query: String) : StoreAction
    data class OpenApp(val repository: GithubRepository) : StoreAction
    data class OpenFdroidApp(val app: FdroidApp) : StoreAction
    data object CloseApp : StoreAction
    data object RetryDetail : StoreAction
    data object RetryFdroid : StoreAction
    data class CatalogTabSelected(val tab: StoreCatalogTab) : StoreAction
    data class DownloadApk(val key: String, val url: String, val fileName: String, val repoKey: String, val expectedSha256: String? = null) : StoreAction
    data object AddSource : StoreAction
    data class RemoveSource(val fullName: String) : StoreAction
    data class SourceInputChanged(val value: String) : StoreAction
}

class StoreViewModel(
    private val searchRepository: GithubRepositorySearchRepository,
    private val releasesRepository: GithubReleasesRepository,
    private val repositoryDetailsRepository: GithubRepositoryDetailsRepository? = null,
    private val sourceStore: GithubStoreSourceStore? = null,
    private val apkManager: GithubApkManager? = null,
    private val fdroidIndexRepository: GithubFdroidIndexRepository? = null,
    private val insightsStore: GithubApkInsightsStore? = null
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()
    private val releaseResolver = StoreReleaseResolver(releasesRepository, apkManager?.supportedAbis.orEmpty())
    private val releaseCache = mutableMapOf<String, takagi.ru.monica.github.domain.GithubRelease>()
    private var loadJob: Job? = null
    private var failedReset = true
    private var detailJob: Job? = null
    private var fdroidJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        _state.update { it.copy(enabledFdroidSources = sourceStore?.enabledFdroidSources() ?: FdroidSources.defaults, isLoading = false) }
        loadFdroid()
        loadSources()
    }

    fun onAction(action: StoreAction) {
        when (action) {
            is StoreAction.FilterFdroid -> _state.update { it.copy(fdroidFilter = action.filter) }
            StoreAction.NextApp -> {
                val current = _state.value
                if (current.selectedFdroid != null) {
                    val index = current.filteredFdroidApps.indexOfFirst { it.packageName == current.selectedFdroid.packageName }
                    current.filteredFdroidApps.getOrNull(index + 1)?.takeIf { index >= 0 }?.let(::openFdroidApp)
                } else {
                    val list = (current.filteredSources + current.filteredApps).distinctBy { it.fullName }
                    val index = list.indexOfFirst { it.fullName == current.selected?.repository?.fullName }
                    list.getOrNull(index + 1)?.takeIf { index >= 0 }?.let(::openApp)
                }
            }
            is StoreAction.ToggleFdroidSource -> {
                sourceStore?.setFdroidEnabled(action.url, action.enabled)
                _state.update { it.copy(enabledFdroidSources = if (action.enabled) it.enabledFdroidSources + action.url else it.enabledFdroidSources - action.url) }
                loadFdroid()
            }
            StoreAction.Refresh -> { releaseCache.clear(); load(reset = true, refreshing = true) }
            StoreAction.Retry -> load(
                reset = failedReset,
                refreshing = failedReset && _state.value.apps.isNotEmpty()
            )
            StoreAction.LoadMore -> load(reset = false)
            is StoreAction.Search -> _state.update { it.copy(query = action.query) }
            is StoreAction.OpenApp -> openApp(action.repository)
            is StoreAction.OpenFdroidApp -> openFdroidApp(action.app)
            StoreAction.CloseApp -> {
                detailJob?.cancel()
                _state.update {
                    it.copy(selected = null, selectedFdroid = null, isLoadingDetail = false, detailError = false, installedVersion = null)
                }
            }
            StoreAction.RetryDetail -> _state.value.selected?.let { openApp(it.repository) }
            StoreAction.RetryFdroid -> loadFdroid(forceRefresh = true)
            is StoreAction.CatalogTabSelected -> {
                _state.update { it.copy(catalogTab = action.tab) }
                if (action.tab == StoreCatalogTab.RECOMMENDED && _state.value.apps.isEmpty()) load(reset = true)
                if (action.tab == StoreCatalogTab.FDROID && _state.value.fdroidApps.isEmpty()) {
                    loadFdroid()
                }
            }
            is StoreAction.DownloadApk -> downloadApk(action)
            is StoreAction.SourceInputChanged -> _state.update { it.copy(sourceInput = action.value, sourceInputError = false) }
            StoreAction.AddSource -> addSource()
            is StoreAction.RemoveSource -> removeSource(action.fullName)
        }
    }

    /** GitHub 仓库条目详情。 */
    private fun openApp(repository: GithubRepository) {
        detailJob?.cancel()
        _state.update {
            it.copy(
                selected = GithubStoreApp(repository = repository),
                selectedFdroid = null,
                isLoadingDetail = true,
                detailError = false,
                installedVersion = installedVersionFor(repository.fullName, null)
            )
        }
        val fullName = repository.fullName.split("/")
        val owner = fullName.getOrNull(0) ?: return
        val name = fullName.getOrNull(1) ?: return
        detailJob = viewModelScope.launch {
            takagi.ru.monica.github.data.githubRunCatching {
                releaseCache[repository.fullName] ?: releaseResolver.resolve(owner, name)
            }.fold(
                onSuccess = { result ->
                    _state.update { state ->
                        state.copy(
                            selected = state.selected?.copy(latestRelease = result),
                            isLoadingDetail = false,
                            detailError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingDetail = false, detailError = true) }
                }
            )
        }
    }

    /** F-Droid 应用详情(信息已含在索引里，包名可直接查已装版本)。 */
    private fun openFdroidApp(app: FdroidApp) {
        detailJob?.cancel()
        _state.update {
            it.copy(
                selected = null,
                selectedFdroid = app,
                isLoadingDetail = false,
                detailError = false,
                installedVersion = apkManager?.installedVersion(app.packageName)
            )
        }
    }

    private fun installedVersionFor(repoKey: String, fallbackPackageName: String?): String? {
        val manager = apkManager ?: return null
        val insights = insightsStore
        val packageName = fallbackPackageName ?: insights?.packageName(repoKey) ?: return null
        return manager.installedVersion(packageName)
    }

    private fun downloadApk(action: StoreAction.DownloadApk) {
        val manager = apkManager ?: return
        if (downloadJobs.containsKey(action.key)) return
        _state.update {
            it.copy(downloads = it.downloads + (action.key to ApkDownloadUiState(progress = 0f)))
        }
        downloadJobs[action.key] = viewModelScope.launch {
            try {
                val expectedHash = action.expectedSha256
                    ?: _state.value.fdroidApps.firstOrNull { it.apkUrl == action.url }?.sha256
                val safeName = action.key.hashCode().toUInt().toString(16) + "-" + java.io.File(action.fileName).name
                val file = manager.download(action.url, safeName) { progress ->
                    _state.update { state ->
                        state.copy(
                            downloads = state.downloads + (
                                action.key to (state.downloads[action.key]
                                    ?: ApkDownloadUiState()).copy(progress = progress)
                                )
                        )
                    }
                }
                if (expectedHash != null) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count >= 0) { digest.update(buffer, 0, count); count = input.read(buffer) }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedHash, true)) { file.delete(); error("APK checksum mismatch") }
                }
                val details = manager.parse(file)
                if (details != null && (details.minSdk ?: 1) <= android.os.Build.VERSION.SDK_INT) {
                    insightsStore?.remember(action.repoKey, details.packageName, details.versionName)
                    _state.update { state ->
                        state.copy(
                            downloads = state.downloads + (
                                action.key to (state.downloads[action.key]
                                    ?: ApkDownloadUiState()).copy(progress = null, file = file, details = details)
                                ),
                            installedVersion = if (state.selected?.repository?.fullName == action.repoKey || state.selectedFdroid?.packageName == action.repoKey) manager.installedVersion(details.packageName) else state.installedVersion
                        )
                    }
                } else {
                    _state.update { state ->
                        state.copy(
                            downloads = state.downloads + (
                                action.key to (state.downloads[action.key]
                                    ?: ApkDownloadUiState()).copy(progress = null, error = true)
                                )
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { state ->
                    state.copy(
                        downloads = state.downloads + (
                            action.key to (state.downloads[action.key]
                                ?: ApkDownloadUiState()).copy(progress = null, error = true)
                            )
                    )
                }
            } finally {
                downloadJobs.remove(action.key)
            }
        }
    }

    private fun loadFdroid(forceRefresh: Boolean = false) {
        val repository = fdroidIndexRepository ?: return
        fdroidJob?.cancel()
        _state.update { it.copy(isLoadingFdroid = true, fdroidError = false) }
        fdroidJob = viewModelScope.launch {
            val enabled = FdroidSources.builtIn.filter { it.url in _state.value.enabledFdroidSources }
            val results = coroutineScope {
                val slots = Semaphore(2)
                enabled.map { source -> async { slots.withPermit { source to repository.loadSource(source, forceRefresh = forceRefresh) } } }.awaitAll()
            }
            val failed = results.filter { it.second.isFailure }.map { it.first.name }
            _state.update { it.copy(failedFdroidSources = failed) }
            val catalog = results.flatMap { it.second.getOrNull().orEmpty() }
                .groupBy { it.packageName }.map { (_, variants) -> variants.maxBy { it.versionCode } }
                .sortedBy { it.name.lowercase() }
            val loaded = if (catalog.isEmpty() && failed.isNotEmpty()) Result.failure(IllegalStateException("Sources unavailable")) else Result.success(catalog)
            loaded.fold(
                onSuccess = { apps ->
                    // 与设备已装应用比对：算出已装版本表与"可更新"清单。
                    val installed = apps.mapNotNull { app ->
                        apkManager?.installedVersion(app.packageName)
                            ?.let { version -> app.packageName to version }
                    }.toMap()
                    val updates = apps.filter { app ->
                        val current = installed[app.packageName]
                        current != null && (apkManager?.installedVersionCode(app.packageName)?.let { app.versionCode > it } == true)
                    }
                    _state.update {
                        it.copy(
                            fdroidApps = apps,
                            fdroidInstalled = installed,
                            updates = updates,
                            isLoadingFdroid = false,
                            fdroidError = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingFdroid = false, fdroidError = true) }
                }
            )
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return
        val page = if (reset) 1 else current.nextPage ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                apps = if (reset && !refreshing) emptyList() else it.apps,
                isLoading = reset && !refreshing,
                isLoadingMore = !reset,
                isRefreshing = refreshing,
                error = false
            )
        }
        loadJob = viewModelScope.launch {
            searchRepository.search(STORE_CATALOG_QUERY, page = page, perPage = 30).fold(
                onSuccess = { result: GithubPage<GithubRepository> ->
                    val slots = Semaphore(3)
                    val validated = coroutineScope {
                        result.items.map { repo -> async { slots.withPermit {
                            takagi.ru.monica.github.data.githubRunCatching {
                                val parts = repo.fullName.split("/")
                                val release = releaseCache[repo.fullName] ?: releaseResolver.resolve(parts[0], parts[1])
                                if (release != null) releaseCache[repo.fullName] = release
                                repo.takeIf { release != null }
                            }
                        } } }.awaitAll()
                    }
                    val verifiedPage = result.copy(items = validated.mapNotNull { it.getOrNull() })
                    _state.update { state ->
                        state.copy(
                            apps = verifiedPage.mergeItems(state.apps, reset, GithubRepository::id),
                            nextPage = result.nextPage,
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = validated.any { it.isFailure }
                        )
                    }
                },
                onFailure = {
                    failedReset = reset
                    _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = true) }
                }
            )
        }
    }

    private fun addSource() {
        val store = sourceStore ?: return
        if (repositoryDetailsRepository == null) return
        val normalized = GithubStoreSource.normalize(_state.value.sourceInput)
        if (normalized == null) {
            _state.update { it.copy(sourceInputError = true) }
            return
        }
        if (_state.value.sources.any { it.fullName.equals(normalized, ignoreCase = true) }) {
            _state.update { it.copy(sourceInput = "", sourceInputError = false) }
            return
        }
        store.add(normalized)
        _state.update { it.copy(sourceInput = "", sourceInputError = false, isLoadingSources = true) }
        viewModelScope.launch {
            val parts = normalized.split("/")
            repositoryDetailsRepository.details(parts[0], parts[1]).fold(
                onSuccess = { details ->
                    val release = takagi.ru.monica.github.data.githubRunCatching { releaseResolver.resolve(parts[0], parts[1]) }.getOrNull()
                    if (release == null) {
                        store.remove(normalized)
                        _state.update { it.copy(isLoadingSources = false, sourceInputError = true) }
                        return@fold
                    }
                    releaseCache[details.repository.fullName] = release
                    _state.update { state ->
                        state.copy(
                            sources = (state.sources + details.repository)
                                .sortedBy(GithubRepository::fullName),
                            isLoadingSources = false
                        )
                    }
                },
                onFailure = {
                    store.remove(normalized)
                    _state.update { it.copy(isLoadingSources = false, sourceInputError = true) }
                }
            )
        }
    }

    private fun removeSource(fullName: String) {
        sourceStore?.remove(fullName)
        _state.update { state ->
            state.copy(sources = state.sources.filterNot { it.fullName.equals(fullName, ignoreCase = true) })
        }
    }

    private fun loadSources() {
        val store = sourceStore ?: return
        val details = repositoryDetailsRepository ?: return
        val names = store.sources()
        if (names.isEmpty()) return
        _state.update { it.copy(isLoadingSources = true) }
        viewModelScope.launch {
            val loaded = names.mapNotNull { fullName ->
                val parts = fullName.split("/")
                if (parts.size != 2) return@mapNotNull null
                val repo = details.details(parts[0], parts[1]).getOrNull()?.repository ?: return@mapNotNull null
                val release = takagi.ru.monica.github.data.githubRunCatching { releaseResolver.resolve(parts[0], parts[1]) }.getOrNull()
                if (release != null) releaseCache[repo.fullName] = release
                repo.takeIf { release != null }
            }
            _state.update { state ->
                state.copy(
                    sources = (state.sources + loaded).distinctBy { it.fullName.lowercase() }
                        .sortedBy(GithubRepository::fullName),
                    isLoadingSources = false
                )
            }
        }
    }

    class Factory(
        private val searchRepository: GithubRepositorySearchRepository,
        private val releasesRepository: GithubReleasesRepository,
        private val repositoryDetailsRepository: GithubRepositoryDetailsRepository? = null,
        private val sourceStore: GithubStoreSourceStore? = null,
        private val apkManager: GithubApkManager? = null,
        private val fdroidIndexRepository: GithubFdroidIndexRepository? = null,
        private val insightsStore: GithubApkInsightsStore? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StoreViewModel::class.java))
            return StoreViewModel(
                searchRepository,
                releasesRepository,
                repositoryDetailsRepository,
                sourceStore,
                apkManager,
                fdroidIndexRepository,
                insightsStore
            ) as T
        }
    }
}
