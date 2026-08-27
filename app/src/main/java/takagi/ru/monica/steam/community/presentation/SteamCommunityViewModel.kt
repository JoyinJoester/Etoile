package takagi.ru.monica.steam.community.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.community.data.SteamCommunityCache
import takagi.ru.monica.steam.community.data.SteamCommunityPreferencesCache
import takagi.ru.monica.steam.community.data.SteamCommunityService
import takagi.ru.monica.steam.community.eligibility.data.SteamCommunityEligibilityService
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityEligibilityGateway
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.community.eligibility.domain.CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
import takagi.ru.monica.steam.community.eligibility.domain.withSteamLevelEvidence
import takagi.ru.monica.steam.community.domain.SteamCommunityGateway
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot
import takagi.ru.monica.steam.community.domain.STEAM_COMMUNITY_CORE_SECTIONS
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

class SteamCommunityViewModel(
    private val gateway: SteamCommunityGateway,
    private val cache: SteamCommunityCache,
    private val eligibilityGateway: SteamCommunityEligibilityGateway? = null,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamCommunityUiState())
    val uiState: StateFlow<SteamCommunityUiState> = _uiState.asStateFlow()

    private var activeAccount: SteamAccount? = null
    private var requestGeneration = 0L

    fun selectAccount(account: SteamAccount?) {
        if (account?.id == activeAccount?.id && account?.steamId == activeAccount?.steamId) {
            activeAccount = account
            return
        }
        activeAccount = account
        val generation = ++requestGeneration
        if (account == null || !account.hasRealSteamId) {
            _uiState.value = SteamCommunityUiState(
                accountId = account?.id,
                accountSteamId = account?.steamId,
                failure = SteamCommunityFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        _uiState.value = SteamCommunityUiState(
            accountId = account.id,
            accountSteamId = account.steamId,
            loading = true
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.load(account.steamId) }
            if (!isCurrent(account, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                snapshot = cached,
                loading = cached == null,
                refreshing = cached != null,
                fromCache = cached != null
            )
            fetch(account, generation, cached)
        }
    }

    fun refresh() {
        val account = activeAccount?.takeIf(SteamAccount::hasRealSteamId) ?: run {
            _uiState.value = _uiState.value.copy(
                failure = SteamCommunityFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        val generation = ++requestGeneration
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.snapshot == null,
            refreshing = _uiState.value.snapshot != null,
            failure = null
        )
        fetch(account, generation, _uiState.value.snapshot)
    }

    private fun fetch(
        account: SteamAccount,
        generation: Long,
        cached: SteamCommunitySnapshot?
    ) {
        viewModelScope.launch {
            val result = runCommunityCatching {
                withContext(ioDispatcher) {
                    withSessionRetry(account) { prepared ->
                        coroutineScope {
                            val snapshot = async { gateway.fetch(prepared) }
                            val eligibility = eligibilityGateway?.let { gateway ->
                                async { runCommunityCatching { gateway.fetch(prepared) } }
                            }
                            val base = snapshot.await()
                            when (val result = eligibility?.await()) {
                                null -> base
                                else -> result.fold(
                                    onSuccess = { progress ->
                                        base.copy(
                                            unlockProgress = progress.withSteamLevelEvidence(
                                                base.steamLevel
                                            )
                                        )
                                    },
                                    onFailure = {
                                        val levelProgress = null.withSteamLevelEvidence(
                                            base.steamLevel
                                        )
                                        base.copy(
                                            unlockProgress = levelProgress,
                                            unavailableSections = if (levelProgress == null) {
                                                base.unavailableSections +
                                                    SteamCommunitySection.ELIGIBILITY
                                            } else {
                                                base.unavailableSections
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (!isCurrent(account, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "community refresh failed type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    fromCache = _uiState.value.snapshot != null,
                    failure = error.toFailureReason()
                )
                return@launch
            }

            val fresh = result.getOrThrow()
            val merge = mergeCommunitySnapshot(fresh, cached)
            withContext(ioDispatcher) { cache.save(merge.snapshot) }
            if (!isCurrent(account, generation)) return@launch
            val hasContent = merge.snapshot.hasVisibleContent()
            _uiState.value = _uiState.value.copy(
                snapshot = merge.snapshot,
                loading = false,
                refreshing = false,
                fromCache = merge.staleSections.isNotEmpty(),
                staleSections = merge.staleSections,
                failure = if (
                    !hasContent &&
                    merge.snapshot.unavailableSections.containsAll(
                        STEAM_COMMUNITY_CORE_SECTIONS
                    )
                ) {
                    SteamCommunityFailureReason.UNAVAILABLE
                } else {
                    null
                }
            )
        }
    }

    private suspend fun prepareSession(
        account: SteamAccount,
        forceRefresh: Boolean = false
    ): SteamAccount {
        val resolved = sessionResolver.resolveOrKeep(account, forceRefresh)
        val current = activeAccount
        if (current?.id == account.id && current.steamId == account.steamId) {
            activeAccount = resolved
        }
        return resolved
    }

    private suspend fun <T> withSessionRetry(
        account: SteamAccount,
        block: suspend (SteamAccount) -> T
    ): T {
        val prepared = prepareSession(account)
        return try {
            block(prepared)
        } catch (error: Throwable) {
            if (!error.requiresSessionRefresh()) throw error
            val refreshed = prepareSession(account, forceRefresh = true)
            block(refreshed)
        }
    }

    private fun isCurrent(account: SteamAccount, generation: Long): Boolean =
        activeAccount?.id == account.id && activeAccount?.steamId == account.steamId &&
            requestGeneration == generation

    private fun Throwable.toFailureReason(): SteamCommunityFailureReason = when (this) {
        is IOException -> SteamCommunityFailureReason.NETWORK
        is SteamApiException -> when {
            requiresSessionRefresh() -> SteamCommunityFailureReason.SESSION_REQUIRED
            else -> SteamCommunityFailureReason.UNAVAILABLE
        }
        is IllegalArgumentException, is IllegalStateException ->
            SteamCommunityFailureReason.SESSION_REQUIRED
        else -> SteamCommunityFailureReason.UNAVAILABLE
    }

    private fun Throwable.requiresSessionRefresh(): Boolean {
        val error = this as? SteamApiException ?: return false
        return error.eResult?.let { it == 5 || it == 15 || it == 401 || it == 403 } == true ||
            error.httpStatusCode?.let { it == 401 || it == 403 } == true
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamCommunityViewModel(
                        gateway = SteamCommunityService(),
                        cache = SteamCommunityPreferencesCache(appContext),
                        eligibilityGateway = SteamCommunityEligibilityService(),
                        sessionResolver = accountSourceRepository.sessionResolver()
                    ) as T
            }
        }
    }
}

internal data class SteamCommunityMerge(
    val snapshot: SteamCommunitySnapshot,
    val staleSections: Set<SteamCommunitySection>
)

internal fun mergeCommunitySnapshot(
    fresh: SteamCommunitySnapshot,
    cached: SteamCommunitySnapshot?
): SteamCommunityMerge {
    val safeCache = cached?.takeIf { it.accountSteamId == fresh.accountSteamId }
    val stale = linkedSetOf<SteamCommunitySection>()
    fun usesCache(section: SteamCommunitySection, available: Boolean): Boolean {
        val use = section in fresh.unavailableSections && available
        if (use) stale += section
        return use
    }

    val profileFromCache = usesCache(
        SteamCommunitySection.PROFILE,
        safeCache?.profile != null
    )
    val levelFromCache = usesCache(
        SteamCommunitySection.LEVEL,
        safeCache?.steamLevel != null
    )
    val badgesFromCache = usesCache(
        SteamCommunitySection.BADGES,
        safeCache?.let {
            it.badges.isNotEmpty() || it.playerXp != null || it.playerXpNeededToLevelUp != null
        } == true
    )
    val gamesFromCache = usesCache(
        SteamCommunitySection.RECENT_GAMES,
        safeCache?.recentGames?.isNotEmpty() == true
    )
    val eligibilityFromCache = safeCache?.unlockProgress?.let { cachedProgress ->
        val freshProgress = fresh.unlockProgress
        val use = SteamCommunitySection.ELIGIBILITY in fresh.unavailableSections ||
            freshProgress == null ||
            (
                freshProgress.status == SteamCommunityRestrictionStatus.UNKNOWN &&
                    cachedProgress.isSafeEligibilityFallback()
                )
        if (use) stale += SteamCommunitySection.ELIGIBILITY
        use
    } == true

    val merged = fresh.copy(
        profile = if (profileFromCache) safeCache?.profile else fresh.profile,
        steamLevel = if (levelFromCache) safeCache?.steamLevel else fresh.steamLevel,
        badges = if (badgesFromCache) safeCache?.badges.orEmpty() else fresh.badges,
        playerXp = if (badgesFromCache) safeCache?.playerXp else fresh.playerXp,
        playerXpNeededToLevelUp = if (badgesFromCache) {
            safeCache?.playerXpNeededToLevelUp
        } else {
            fresh.playerXpNeededToLevelUp
        },
        recentGames = if (gamesFromCache) {
            safeCache?.recentGames.orEmpty()
        } else {
            fresh.recentGames
        },
        unlockProgress = if (eligibilityFromCache) {
            safeCache?.unlockProgress
        } else {
            fresh.unlockProgress
        }
    )
    return SteamCommunityMerge(
        snapshot = merged.copy(
            unlockProgress = merged.unlockProgress.withSteamLevelEvidence(merged.steamLevel)
        ),
        staleSections = stale
    )
}

private fun SteamCommunityUnlockProgress.isSafeEligibilityFallback(): Boolean =
    status != SteamCommunityRestrictionStatus.UNRESTRICTED ||
        evidenceRevision >= CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION

private fun SteamCommunitySnapshot.hasVisibleContent(): Boolean =
    profile != null || steamLevel != null || badges.isNotEmpty() ||
        playerXp != null || recentGames.isNotEmpty() || unlockProgress != null

private suspend fun <T> runCommunityCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
