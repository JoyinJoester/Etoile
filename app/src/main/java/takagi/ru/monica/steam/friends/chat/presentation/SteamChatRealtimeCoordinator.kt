package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages

/** Owns foreground realtime lifecycle without coupling the reducer to Android UI. */
internal class SteamChatRealtimeCoordinator(
    private val scope: CoroutineScope,
    private val gateway: SteamChatRealtimeGateway?,
    private val cache: SteamChatCache,
    private val outbox: SteamChatOutbox?,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
    private val state: () -> SteamChatUiState,
    private val updateState: (SteamChatUiState) -> Unit,
    private val acknowledge: (SteamAccount, String) -> Unit,
    private val reconcile: () -> Unit
) {
    val enabled: Boolean get() = gateway != null

    private val reducer = SteamChatRealtimeReducer()
    private val cacheMutex = Mutex()
    private val typingJobs = mutableMapOf<String, Job>()
    private val acknowledgementJobs = mutableMapOf<String, Job>()
    private var reconciliationJob: Job? = null
    private var collectorJob: Job? = null
    private var account: SteamAccount? = null
    private var accountKey: String = ""
    private var foreground = false

    fun selectAccount(account: SteamAccount?, accountKey: String) {
        stopJobs(clearTyping = true)
        reducer.reset()
        this.account = account
        this.accountKey = accountKey
        restartCollector()
    }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (!active) stopJobs(clearTyping = true)
        restartCollector()
    }

    fun closeThread(partnerSteamId: String?) {
        partnerSteamId ?: return
        typingJobs.remove(partnerSteamId)?.cancel()
        acknowledgementJobs.remove(partnerSteamId)?.cancel()
        updateState(
            state().copy(typingPartnerSteamIds = state().typingPartnerSteamIds - partnerSteamId)
        )
    }

    private fun restartCollector() {
        collectorJob?.cancel()
        collectorJob = null
        val currentAccount = account
        val realtime = gateway
        if (!foreground || currentAccount == null || realtime == null) {
            updateState(state().copy(realtimeConnected = false))
            return
        }
        val currentKey = accountKey
        collectorJob = scope.launch {
            try {
                realtime.events(currentAccount).collect { event ->
                    if (!isCurrent(currentAccount, currentKey)) return@collect
                    val effect = reducer.reduce(
                        state = state(),
                        event = event,
                        accountSteamId = currentAccount.steamId,
                        nowMillis = nowMillis()
                    )
                    updateState(effect.state)
                    effect.message?.let { message ->
                        handleMessage(currentAccount, currentKey, message, effect.state)
                    }
                    effect.acknowledgePartnerSteamId?.let { partnerSteamId ->
                        scheduleAcknowledgement(currentAccount, currentKey, partnerSteamId)
                    }
                    if (effect.reconcileAuthoritativeState) scheduleReconciliation(
                        currentAccount,
                        currentKey
                    )
                    if (event is SteamChatRealtimeEvent.Typing && !event.localEcho) {
                        scheduleTypingExpiry(currentAccount, currentKey, event.partnerSteamId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logSteamChatFailure("realtime", error)
                if (isCurrent(currentAccount, currentKey)) {
                    updateState(state().copy(realtimeConnected = false))
                }
            }
        }
    }

    private fun handleMessage(
        account: SteamAccount,
        accountKey: String,
        message: SteamChatMessage,
        stateAfterEvent: SteamChatUiState
    ) {
        val selected = stateAfterEvent.selectedPartnerSteamId == message.partnerSteamId
        val snapshot = stateAfterEvent.thread?.takeIf {
            selected && it.partnerSteamId == message.partnerSteamId
        }
        scope.launch(ioDispatcher) {
            cacheMutex.withLock {
                val cached = cache.loadThread(account.steamId, message.partnerSteamId)
                val base = snapshot ?: cached ?: SteamChatThreadSnapshot(
                    accountSteamId = account.steamId,
                    partnerSteamId = message.partnerSteamId,
                    messages = emptyList(),
                    moreAvailable = false,
                    fetchedAt = nowMillis()
                )
                cache.saveThread(
                    accountSteamId = account.steamId,
                    partnerSteamId = message.partnerSteamId,
                    snapshot = base.copy(
                        messages = mergeSteamChatMessages(base.messages, listOf(message)),
                        fetchedAt = nowMillis()
                    )
                )
                stateAfterEvent.sessions?.let { cache.saveSessions(account.steamId, it) }
            }
        }
        if (!message.isOutgoing(account.steamId) || outbox == null) return
        scope.launch(ioDispatcher) {
            runSteamChatCatching {
                completeMatchingRealtimeOutboxEcho(outbox, account, accountKey, message)
            }.onFailure { logSteamChatFailure("realtime_outbox_complete", it) }
        }
    }

    private fun scheduleAcknowledgement(
        account: SteamAccount,
        accountKey: String,
        partnerSteamId: String
    ) {
        acknowledgementJobs[partnerSteamId]?.cancel()
        acknowledgementJobs[partnerSteamId] = scope.launch {
            delay(ACK_DEBOUNCE_MILLIS)
            if (isCurrent(account, accountKey) &&
                state().selectedPartnerSteamId == partnerSteamId
            ) {
                acknowledge(account, partnerSteamId)
            }
            acknowledgementJobs.remove(partnerSteamId)
        }
    }

    private fun scheduleTypingExpiry(
        account: SteamAccount,
        accountKey: String,
        partnerSteamId: String
    ) {
        typingJobs[partnerSteamId]?.cancel()
        typingJobs[partnerSteamId] = scope.launch {
            delay(TYPING_EXPIRY_MILLIS)
            if (isCurrent(account, accountKey)) {
                updateState(
                    state().copy(
                        typingPartnerSteamIds = state().typingPartnerSteamIds - partnerSteamId
                    )
                )
            }
            typingJobs.remove(partnerSteamId)
        }
    }

    private fun scheduleReconciliation(account: SteamAccount, accountKey: String) {
        reconciliationJob?.cancel()
        reconciliationJob = scope.launch {
            delay(RECONCILIATION_DEBOUNCE_MILLIS)
            if (foreground && isCurrent(account, accountKey)) reconcile()
        }
    }

    private fun stopJobs(clearTyping: Boolean) {
        collectorJob?.cancel()
        collectorJob = null
        reconciliationJob?.cancel()
        reconciliationJob = null
        typingJobs.values.forEach(Job::cancel)
        typingJobs.clear()
        acknowledgementJobs.values.forEach(Job::cancel)
        acknowledgementJobs.clear()
        if (clearTyping) {
            updateState(
                state().copy(
                    realtimeConnected = false,
                    typingPartnerSteamIds = emptySet()
                )
            )
        }
    }

    private fun isCurrent(account: SteamAccount, key: String): Boolean =
        this.account?.id == account.id &&
            this.account?.steamId == account.steamId &&
            accountKey == key

    private companion object {
        const val RECONCILIATION_DEBOUNCE_MILLIS = 1_500L
        const val ACK_DEBOUNCE_MILLIS = 250L
        const val TYPING_EXPIRY_MILLIS = 6_000L
    }
}
