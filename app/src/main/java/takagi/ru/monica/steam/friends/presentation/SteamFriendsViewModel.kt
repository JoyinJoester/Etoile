package takagi.ru.monica.steam.friends.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.data.SteamFriendsCache
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.friends.data.SteamFriendsService
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

class SteamFriendsViewModel(
    private val gateway: SteamFriendsGateway,
    private val cache: SteamFriendsCache,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamFriendsUiState())
    val uiState: StateFlow<SteamFriendsUiState> = _uiState.asStateFlow()

    private var activeAccount: SteamAccount? = null
    private var requestGeneration = 0L
    private var actionGeneration = 0L
    private var searchGeneration = 0L

    fun selectAccount(account: SteamAccount?) {
        if (account?.id == activeAccount?.id && account?.steamId == activeAccount?.steamId) {
            activeAccount = account
            return
        }
        activeAccount = account
        val generation = ++requestGeneration
        actionGeneration++
        searchGeneration++
        if (account == null) {
            _uiState.value = SteamFriendsUiState(
                failure = SteamFriendsFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        _uiState.value = SteamFriendsUiState(
            accountId = account.id,
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
            fetch(account, generation, silent = cached != null)
        }
    }

    fun refresh() {
        val account = activeAccount ?: run {
            _uiState.value = _uiState.value.copy(
                failure = SteamFriendsFailureReason.ACCOUNT_REQUIRED
            )
            return
        }
        val generation = ++requestGeneration
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.snapshot == null,
            refreshing = _uiState.value.snapshot != null,
            failure = null
        )
        fetch(account, generation, silent = false)
    }

    fun findFriendCandidates(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            clearFriendDiscovery()
            return
        }
        val account = activeAccount ?: run {
            _uiState.value = _uiState.value.copy(
                discovery = SteamFriendDiscoveryUiState(
                    submittedQuery = normalizedQuery,
                    searched = true,
                    failure = SteamFriendsFailureReason.ACCOUNT_REQUIRED
                )
            )
            return
        }
        val generation = ++searchGeneration
        _uiState.value = _uiState.value.copy(
            discovery = SteamFriendDiscoveryUiState(
                submittedQuery = normalizedQuery,
                searching = true
            )
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    withSessionRetry(account) { prepared ->
                        gateway.findCandidates(prepared, normalizedQuery)
                    }
                }
            }
            if (!isSearchCurrent(account, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "friends discovery failed type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    discovery = SteamFriendDiscoveryUiState(
                        submittedQuery = normalizedQuery,
                        searched = true,
                        failure = error.toFailureReason()
                    )
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                discovery = SteamFriendDiscoveryUiState(
                    submittedQuery = normalizedQuery,
                    results = mergeKnownRelationships(result.getOrThrow()),
                    searched = true
                )
            )
        }
    }

    fun clearFriendDiscovery() {
        searchGeneration++
        _uiState.value = _uiState.value.copy(
            discovery = SteamFriendDiscoveryUiState()
        )
    }

    fun respondToInvite(friend: SteamFriend, accept: Boolean) {
        if (friend.relationship != SteamFriendRelationship.REQUEST_INCOMING) return
        val account = activeAccount ?: return
        if (_uiState.value.actionSteamId != null) return
        val generation = ++actionGeneration
        _uiState.value = _uiState.value.copy(
            actionSteamId = friend.steamId,
            actionFeedback = null
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    withSessionRetry(account) { prepared ->
                        gateway.respondToInvite(prepared, friend.steamId, accept)
                    }
                }
            }
            if (!isActionCurrent(account, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "friends invite_action failed accept=$accept type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    actionSteamId = null,
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = false,
                        message = error.message
                    )
                )
                return@launch
            }

            val actionResult = result.getOrThrow()
            if (actionResult.success) {
                val current = _uiState.value.snapshot
                val updated = current?.copy(
                    friends = current.friends.mapNotNull { existing ->
                        if (existing.steamId != friend.steamId) return@mapNotNull existing
                        if (accept) {
                            existing.copy(relationship = SteamFriendRelationship.FRIEND)
                        } else {
                            null
                        }
                    }
                )
                if (updated != null) {
                    withContext(ioDispatcher) { cache.save(account.steamId, updated) }
                }
                if (!isActionCurrent(account, generation)) return@launch
                _uiState.value = _uiState.value.copy(
                    snapshot = updated,
                    fromCache = false,
                    actionSteamId = null,
                    discovery = _uiState.value.discovery.copy(
                        results = _uiState.value.discovery.results.map { candidate ->
                            if (candidate.steamId != friend.steamId) candidate
                            else if (accept) {
                                candidate.copy(relationship = SteamFriendRelationship.FRIEND)
                            } else {
                                candidate.copy(relationship = SteamFriendRelationship.UNKNOWN)
                            }
                        }
                    ),
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = true,
                        message = actionResult.message
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    actionSteamId = null,
                    actionFeedback = SteamFriendActionFeedback(
                        steamId = friend.steamId,
                        accepted = accept,
                        success = false,
                        message = actionResult.message
                    )
                )
            }
        }
    }

    fun consumeActionFeedback() {
        _uiState.value = _uiState.value.copy(actionFeedback = null)
    }

    fun changeRelationship(friend: SteamFriend, action: SteamFriendRelationshipAction) {
        val account = activeAccount ?: return
        if (_uiState.value.actionSteamId != null) return
        val generation = ++actionGeneration
        _uiState.value = _uiState.value.copy(
            actionSteamId = friend.steamId,
            actionFeedback = null
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    withSessionRetry(account) { prepared ->
                        gateway.changeRelationship(prepared, friend.steamId, action)
                    }
                }
            }
            if (!isActionCurrent(account, generation)) return@launch
            val actionResult = result.getOrNull()
            val success = actionResult?.success == true
            _uiState.value = _uiState.value.copy(
                actionSteamId = null,
                discovery = if (success) {
                    _uiState.value.discovery.copy(
                        results = _uiState.value.discovery.results.map { candidate ->
                            if (candidate.steamId != friend.steamId) candidate
                            else candidate.copy(
                                relationship = when (action) {
                                    SteamFriendRelationshipAction.ADD ->
                                        SteamFriendRelationship.REQUEST_OUTGOING
                                    SteamFriendRelationshipAction.BLOCK ->
                                        SteamFriendRelationship.BLOCKED
                                    SteamFriendRelationshipAction.REMOVE,
                                    SteamFriendRelationshipAction.UNBLOCK ->
                                        SteamFriendRelationship.UNKNOWN
                                }
                            )
                        }
                    )
                } else {
                    _uiState.value.discovery
                },
                actionFeedback = SteamFriendActionFeedback(
                    steamId = friend.steamId,
                    accepted = false,
                    success = success,
                    message = actionResult?.message ?: result.exceptionOrNull()?.message,
                    relationshipAction = action
                )
            )
            if (success) refresh()
        }
    }

    private fun fetch(account: SteamAccount, generation: Long, silent: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    withSessionRetry(account) { prepared -> gateway.fetch(prepared) }
                }
            }
            if (!isCurrent(account, generation)) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                SteamDiagLogger.append(
                    "friends refresh failed silent=$silent type=${error.javaClass.simpleName}"
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    fromCache = _uiState.value.snapshot != null,
                    failure = error.toFailureReason()
                )
                return@launch
            }
            val snapshot = result.getOrThrow()
            withContext(ioDispatcher) { cache.save(account.steamId, snapshot) }
            if (!isCurrent(account, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                snapshot = snapshot,
                loading = false,
                refreshing = false,
                fromCache = false,
                failure = null,
                discovery = _uiState.value.discovery.copy(
                    results = mergeKnownRelationships(
                        candidates = _uiState.value.discovery.results,
                        snapshot = snapshot
                    )
                )
            )
        }
    }

    private fun mergeKnownRelationships(
        candidates: List<SteamFriend>,
        snapshot: SteamFriendsSnapshot? = _uiState.value.snapshot
    ): List<SteamFriend> {
        val knownById = snapshot?.friends.orEmpty().associateBy(SteamFriend::steamId)
        return candidates.map { candidate -> knownById[candidate.steamId] ?: candidate }
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
        block: (SteamAccount) -> T
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

    private fun isActionCurrent(account: SteamAccount, generation: Long): Boolean =
        activeAccount?.id == account.id && activeAccount?.steamId == account.steamId &&
            actionGeneration == generation

    private fun isSearchCurrent(account: SteamAccount, generation: Long): Boolean =
        activeAccount?.id == account.id && activeAccount?.steamId == account.steamId &&
            searchGeneration == generation

    private fun Throwable.toFailureReason(): SteamFriendsFailureReason = when (this) {
        is IOException -> SteamFriendsFailureReason.NETWORK
        is SteamApiException -> when (eResult) {
            401, 403, 5, 15 -> SteamFriendsFailureReason.SESSION_REQUIRED
            else -> SteamFriendsFailureReason.UNAVAILABLE
        }
        is IllegalArgumentException, is IllegalStateException -> SteamFriendsFailureReason.SESSION_REQUIRED
        else -> SteamFriendsFailureReason.UNAVAILABLE
    }

    private fun Throwable.requiresSessionRefresh(): Boolean {
        val error = this as? SteamApiException ?: return false
        return error.eResult?.let { it == 5 || it == 15 } == true ||
            error.httpStatusCode?.let { it == 401 || it == 403 } == true
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val sessionResolver = SteamAccountSourceRepository
                .get(appContext)
                .sessionResolver()
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SteamFriendsViewModel(
                        gateway = SteamFriendsService(),
                        cache = SteamFriendsPreferencesCache(appContext),
                        sessionResolver = sessionResolver
                    ) as T
                }
            }
        }
    }
}
