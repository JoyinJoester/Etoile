package takagi.ru.monica.steam.store.freebie.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.store.data.SteamStoreSessionException
import takagi.ru.monica.steam.store.freebie.data.SteamFreebieCache
import takagi.ru.monica.steam.store.freebie.data.SteamFreebieRateLimitException
import takagi.ru.monica.steam.store.freebie.data.SteamFreebieService
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieCatalog
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieFilter
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieLoadFailure
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus

internal data class SteamFreebieUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    val accountsLoading: Boolean = false,
    val accountSourceError: String? = null,
    val catalog: SteamFreebieCatalog? = null,
    val catalogFromCache: Boolean = false,
    val loading: Boolean = false,
    val failure: SteamFreebieLoadFailure? = null,
    val filter: SteamFreebieFilter = SteamFreebieFilter.ALL,
    val claimingPackageIds: Set<Int> = emptySet(),
    val verifyingPackageIds: Set<Int> = emptySet(),
    val claimResults: Map<Int, SteamFreebieClaimResult> = emptyMap()
)

internal class SteamFreebieViewModel(
    private val accountSourceRepository: SteamAccountSourceRepository,
    private val cache: SteamFreebieCache,
    private val service: SteamFreebieService = SteamFreebieService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamFreebieUiState())
    val uiState: StateFlow<SteamFreebieUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private val verificationJobs = mutableMapOf<Int, Job>()

    init {
        viewModelScope.launch {
            accountSourceRepository.state.collect { sourceState ->
                val previousAccountId = _uiState.value.selectedAccountId
                val accounts = sourceState.accounts.filter { it.hasAuthenticatedSession }
                val selected = accounts
                    .firstOrNull { it.id == sourceState.selectedAccountId }
                    ?: accounts.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = selected?.id,
                    storageSource = sourceState.storageSource,
                    mdbxDatabases = sourceState.mdbxDatabases,
                    accountsLoading = sourceState.loading,
                    accountSourceError = sourceState.errorMessage
                )
                if (previousAccountId != selected?.id || _uiState.value.catalog == null) {
                    resetForAccount(selected?.id)
                    load(force = true)
                }
            }
        }
    }

    fun load(force: Boolean = false) {
        val initial = _uiState.value
        if (initial.loading) return
        if (!force && initial.catalog != null && !initial.catalogFromCache) return
        val accountId = initial.selectedAccountId
        val account = selectedAccount()
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_uiState.value.catalog == null) {
                val cached = withContext(Dispatchers.IO) { cache.read(accountId) }
                if (requestIsCurrent(accountId, generation) && cached != null) {
                    _uiState.value = _uiState.value.copy(
                        catalog = cached,
                        catalogFromCache = true
                    )
                }
            }
            if (!requestIsCurrent(accountId, generation)) return@launch
            _uiState.value = _uiState.value.copy(loading = true, failure = null)
            runCatching {
                withContext(Dispatchers.IO) { loadWithSessionRetry(account) }
            }.onSuccess { catalog ->
                if (!requestIsCurrent(accountId, generation)) return@onSuccess
                withContext(Dispatchers.IO) { cache.write(accountId, catalog) }
                _uiState.value = _uiState.value.copy(
                    catalog = catalog,
                    catalogFromCache = false,
                    loading = false,
                    failure = null
                )
            }.onFailure { error ->
                if (!requestIsCurrent(accountId, generation)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    failure = error.toLoadFailure()
                )
            }
        }
    }

    fun claim(item: SteamFreebieItem) {
        val packageId = item.packageId ?: return
        val account = selectedAccount() ?: run {
            _uiState.value = _uiState.value.copy(
                claimResults = _uiState.value.claimResults + (
                    packageId to SteamFreebieClaimResult(
                        SteamFreebieClaimStatus.SESSION_REQUIRED
                    )
                )
            )
            return
        }
        if (packageId in _uiState.value.claimingPackageIds) return
        val accountId = account.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                claimingPackageIds = _uiState.value.claimingPackageIds + packageId,
                claimResults = _uiState.value.claimResults - packageId
            )
            val result = runCatching {
                withContext(Dispatchers.IO) { claimWithSessionRetry(account, item) }
            }.getOrElse { error ->
                SteamFreebieClaimResult(
                    status = when (error.toLoadFailure()) {
                        SteamFreebieLoadFailure.SESSION_REQUIRED ->
                            SteamFreebieClaimStatus.SESSION_REQUIRED
                        SteamFreebieLoadFailure.RATE_LIMITED ->
                            SteamFreebieClaimStatus.RATE_LIMITED
                        SteamFreebieLoadFailure.NETWORK,
                        SteamFreebieLoadFailure.INVALID_RESPONSE ->
                            SteamFreebieClaimStatus.FAILED
                    }
                )
            }
            if (_uiState.value.selectedAccountId != accountId) return@launch
            val updatedCatalog = if (
                result.status == SteamFreebieClaimStatus.CLAIMED ||
                result.status == SteamFreebieClaimStatus.ALREADY_OWNED
            ) {
                _uiState.value.catalog?.copy(
                    items = _uiState.value.catalog?.items.orEmpty().map { current ->
                        if (current.appId == item.appId) {
                            current.copy(ownership = SteamStoreOwnershipStatus.OWNED)
                        } else {
                            current
                        }
                    }
                )
            } else {
                _uiState.value.catalog
            }
            _uiState.value = _uiState.value.copy(
                catalog = updatedCatalog,
                claimingPackageIds = _uiState.value.claimingPackageIds - packageId,
                claimResults = _uiState.value.claimResults + (packageId to result)
            )
            updatedCatalog?.let { catalog ->
                withContext(Dispatchers.IO) {
                    cache.write(accountId, catalog)
                }
            }
            if (result.status == SteamFreebieClaimStatus.PENDING_VERIFICATION) {
                scheduleOwnershipVerification(accountId, item)
            }
        }
    }

    /** Refreshes an accepted claim without submitting the license a second time. */
    fun refreshClaim(item: SteamFreebieItem) {
        val packageId = item.packageId ?: return
        val account = selectedAccount() ?: return
        if (packageId in _uiState.value.claimingPackageIds ||
            packageId in _uiState.value.verifyingPackageIds
        ) return
        val accountId = account.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                verifyingPackageIds = _uiState.value.verifyingPackageIds + packageId
            )
            val ownership = withContext(Dispatchers.IO) {
                service.verifyOwnership(account, item)
            }
            if (_uiState.value.selectedAccountId != accountId) return@launch
            val owned = ownership == SteamStoreOwnershipStatus.OWNED
            val result = if (owned) {
                SteamFreebieClaimResult(SteamFreebieClaimStatus.CLAIMED)
            } else {
                _uiState.value.claimResults[packageId]
                    ?: SteamFreebieClaimResult(SteamFreebieClaimStatus.PENDING_VERIFICATION)
            }
            val updatedCatalog = if (owned) {
                markOwned(_uiState.value.catalog, item.appId)
            } else {
                _uiState.value.catalog
            }
            _uiState.value = _uiState.value.copy(
                catalog = updatedCatalog,
                verifyingPackageIds = _uiState.value.verifyingPackageIds - packageId,
                claimResults = _uiState.value.claimResults + (packageId to result)
            )
            updatedCatalog?.let { catalog ->
                withContext(Dispatchers.IO) { cache.write(accountId, catalog) }
            }
            if (owned) verificationJobs.remove(packageId)?.cancel()
        }
    }

    fun selectFilter(filter: SteamFreebieFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun selectAccount(accountId: Long) {
        accountSourceRepository.selectAccount(accountId)
    }

    fun selectStorageSource(source: SteamStorageSource) {
        accountSourceRepository.selectStorageSource(source)
    }

    fun refreshAccountSource() {
        accountSourceRepository.refreshCurrentSource()
    }

    fun selectedAccount(): SteamAccount? = _uiState.value.accounts
        .firstOrNull { it.id == _uiState.value.selectedAccountId }

    private suspend fun loadWithSessionRetry(account: SteamAccount?): SteamFreebieCatalog {
        if (account == null) return service.load(null)
        val prepared = refreshAccountSession(account, force = false)
        return try {
            service.load(prepared)
        } catch (error: SteamStoreSessionException) {
            val refreshed = refreshAccountSession(prepared, force = true)
            if (refreshed.accessToken == prepared.accessToken &&
                refreshed.steamLoginSecure == prepared.steamLoginSecure
            ) {
                throw error
            }
            service.load(refreshed)
        }
    }

    private suspend fun claimWithSessionRetry(
        account: SteamAccount,
        item: SteamFreebieItem
    ): SteamFreebieClaimResult {
        val prepared = refreshAccountSession(account, force = false)
        val first = service.claim(prepared, item)
        if (first.status != SteamFreebieClaimStatus.SESSION_REQUIRED) return first
        val refreshed = refreshAccountSession(prepared, force = true)
        return if (
            refreshed.accessToken != prepared.accessToken ||
            refreshed.steamLoginSecure != prepared.steamLoginSecure
        ) {
            service.claim(refreshed, item)
        } else {
            first
        }
    }

    private suspend fun refreshAccountSession(
        account: SteamAccount,
        force: Boolean
    ): SteamAccount {
        val refreshed = accountSourceRepository.resolveSession(account, force)
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { current ->
                if (current.id == refreshed.id) refreshed else current
            }
        )
        return refreshed
    }

    private fun resetForAccount(accountId: Long?) {
        loadJob?.cancel()
        loadGeneration++
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            catalog = null,
            catalogFromCache = false,
            loading = false,
            failure = null,
            filter = SteamFreebieFilter.ALL,
            claimingPackageIds = emptySet(),
            verifyingPackageIds = emptySet(),
            claimResults = emptyMap()
        )
        verificationJobs.values.forEach { it.cancel() }
        verificationJobs.clear()
    }

    private fun scheduleOwnershipVerification(
        accountId: Long,
        item: SteamFreebieItem
    ) {
        val packageId = item.packageId ?: return
        verificationJobs[packageId]?.cancel()
        verificationJobs[packageId] = viewModelScope.launch {
            repeat(AUTOMATIC_VERIFICATION_ATTEMPTS) { attempt ->
                kotlinx.coroutines.delay(AUTOMATIC_VERIFICATION_DELAY_MILLIS)
                if (_uiState.value.selectedAccountId != accountId) return@launch
                val ownership = withContext(Dispatchers.IO) {
                    selectedAccount()?.let { account -> service.verifyOwnership(account, item) }
                }
                if (ownership == SteamStoreOwnershipStatus.OWNED) {
                    val updatedCatalog = markOwned(_uiState.value.catalog, item.appId)
                    _uiState.value = _uiState.value.copy(
                        catalog = updatedCatalog,
                        claimResults = _uiState.value.claimResults + (
                            packageId to SteamFreebieClaimResult(
                                SteamFreebieClaimStatus.CLAIMED
                            )
                        )
                    )
                    updatedCatalog?.let { catalog ->
                        withContext(Dispatchers.IO) { cache.write(accountId, catalog) }
                    }
                    return@launch
                }
            }
        }.also { job ->
            job.invokeOnCompletion { verificationJobs.remove(packageId, job) }
        }
    }

    private fun markOwned(
        catalog: SteamFreebieCatalog?,
        appId: Int
    ): SteamFreebieCatalog? = catalog?.copy(
        items = catalog.items.map { current ->
            if (current.appId == appId) {
                current.copy(ownership = SteamStoreOwnershipStatus.OWNED)
            } else {
                current
            }
        }
    )

    private fun requestIsCurrent(accountId: Long?, generation: Long): Boolean =
        _uiState.value.selectedAccountId == accountId && generation == loadGeneration

    private fun Throwable.toLoadFailure(): SteamFreebieLoadFailure = when (this) {
        is SteamStoreSessionException -> SteamFreebieLoadFailure.SESSION_REQUIRED
        is SteamFreebieRateLimitException -> SteamFreebieLoadFailure.RATE_LIMITED
        is IOException -> SteamFreebieLoadFailure.NETWORK
        else -> SteamFreebieLoadFailure.INVALID_RESPONSE
    }

    companion object {
        private const val AUTOMATIC_VERIFICATION_ATTEMPTS = 5
        private const val AUTOMATIC_VERIFICATION_DELAY_MILLIS = 2_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamFreebieViewModel(
                        accountSourceRepository = accountSourceRepository,
                        cache = SteamFreebieCache(appContext)
                    ) as T
            }
        }
    }
}
