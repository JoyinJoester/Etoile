package takagi.ru.monica.steam.friends.chat.background.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatRealtimeService
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationDecision
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPolicy
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

class SteamChatBackgroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferences: SteamChatBackgroundPreferences
    private lateinit var sourceRepository: SteamAccountSourceRepository
    private lateinit var notificationPublisher: SteamChatNotificationPublisher
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferences = SteamChatBackgroundPreferences(this)
        sourceRepository = SteamAccountSourceRepository.get(this)
        notificationPublisher = SteamChatNotificationPublisher(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(
            notificationPublisher.foregroundNotification(
                handle = null,
                state = SteamChatBackgroundConnectionState.WAITING_FOR_ACCOUNT
            )
        )
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch { monitorSelectedAccount() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationPublisher.cancelForeground()
        super.onDestroy()
    }

    private suspend fun monitorSelectedAccount() {
        preferences.settings
            .combine(sourceRepository.state) { settings, state ->
                val account = state.accounts.firstOrNull { candidate ->
                    candidate.id == state.selectedAccountId
                }
                SteamChatBackgroundTarget(
                    enabled = settings.enabled,
                    handle = account?.let { selected ->
                        sourceRepository.sessionHandleForSource(selected, state.storageSource)
                    }
                )
            }
            .distinctUntilChangedBy { target ->
                target.enabled to target.handle?.stableKey
            }
            .collectLatest { target ->
                if (!target.enabled) {
                    stopSelf()
                    return@collectLatest
                }
                val handle = target.handle
                if (handle == null) {
                    notificationPublisher.updateForeground(
                        handle = null,
                        state = SteamChatBackgroundConnectionState.WAITING_FOR_ACCOUNT
                    )
                    return@collectLatest
                }
                monitor(handle)
            }
    }

    private suspend fun monitor(handle: SteamAccountSessionHandle) {
        SteamVoiceCallRuntime.get(this).observeAccount(handle.account)
        notificationPublisher.updateForeground(
            handle,
            SteamChatBackgroundConnectionState.CONNECTING
        )
        while (currentCoroutineContext().isActive) {
            val realtime = SteamFriendChatRealtimeService(
                sessionResolver = SteamAccountSessionResolver { _, forceRefresh ->
                    sourceRepository.sessionManager.resolve(handle, forceRefresh).account
                }
            )
            try {
                realtime.events(handle.account).collect { event ->
                    when (event) {
                        is SteamChatRealtimeEvent.ConnectionChanged -> {
                            notificationPublisher.updateForeground(
                                handle,
                                if (event.connected) {
                                    SteamChatBackgroundConnectionState.CONNECTED
                                } else {
                                    SteamChatBackgroundConnectionState.RECONNECTING
                                }
                            )
                        }
                        is SteamChatRealtimeEvent.Message -> processMessage(handle, event)
                        is SteamChatRealtimeEvent.Acknowledged -> {
                            notificationPublisher.cancelConversation(
                                handle.stableKey,
                                event.partnerSteamId
                            )
                        }
                        is SteamChatRealtimeEvent.ReactionChanged,
                        is SteamChatRealtimeEvent.Typing -> Unit
                        is SteamChatRealtimeEvent.ConversationLeft -> {
                            notificationPublisher.cancelConversation(
                                handle.stableKey,
                                event.partnerSteamId
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "chat_background_monitor failed account=${handle.stableKey.hashCode()} " +
                        "type=${error::class.java.simpleName}"
                )
                notificationPublisher.updateForeground(
                    handle,
                    SteamChatBackgroundConnectionState.RECONNECTING
                )
                delay(RESTART_DELAY_MILLIS)
            }
        }
    }

    private suspend fun processMessage(
        handle: SteamAccountSessionHandle,
        event: SteamChatRealtimeEvent.Message
    ) {
        if (event.message.senderSteamId == handle.account.steamId) {
            notificationPublisher.cancelConversation(
                handle.stableKey,
                event.message.partnerSteamId
            )
            return
        }
        val decision = SteamChatNotificationPolicy.evaluate(
            accountKey = handle.stableKey,
            accountSteamId = handle.account.steamId,
            message = event.message
        )
        if (decision !is SteamChatNotificationDecision.Notify) return
        if (!preferences.claimNotification(decision.identity)) return
        val published = notificationPublisher.publishIncomingMessage(
            handle = handle,
            message = event.message,
            preview = decision.preview
        )
        if (!published) {
            preferences.releaseNotification(decision.identity)
            SteamDiagLogger.append(
                "chat_background_notify failed account=${handle.stableKey.hashCode()} " +
                    "partner=${event.message.partnerSteamId.hashCode()}"
            )
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SteamChatNotificationPublisher.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                SteamChatNotificationPublisher.SERVICE_NOTIFICATION_ID,
                notification
            )
        }
    }

    private data class SteamChatBackgroundTarget(
        val enabled: Boolean,
        val handle: SteamAccountSessionHandle?
    )

    private companion object {
        const val RESTART_DELAY_MILLIS = 5_000L
    }
}
