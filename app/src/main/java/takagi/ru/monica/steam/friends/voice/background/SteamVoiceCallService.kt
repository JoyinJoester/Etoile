package takagi.ru.monica.steam.friends.voice.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceAudioSession
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime

class SteamVoiceCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var publisher: SteamVoiceNotificationPublisher
    private lateinit var audioSession: SteamVoiceAudioSession
    private lateinit var wakeLock: PowerManager.WakeLock
    private var stateJob: Job? = null
    private var audioSessionStarted = false

    override fun onCreate() {
        super.onCreate()
        publisher = SteamVoiceNotificationPublisher(this)
        val runtime = SteamVoiceCallRuntime.get(this)
        audioSession = SteamVoiceAudioSession(this, runtime::updateAudioRoutes)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        val initialState = runtime.state.value
        startForegroundCompat(
            notification = publisher.notification(initialState),
            microphoneActive = initialState.isActive
        )
        stateJob = scope.launch {
            runtime.state.collectLatest { state ->
                when (state.voiceServiceMode()) {
                    SteamVoiceCallServiceMode.ACTIVE -> {
                        startActiveResources()
                        audioSession.applyRoute(state.requestedAudioRoute)
                        startForegroundCompat(
                            notification = publisher.notification(state),
                            microphoneActive = true
                        )
                    }
                    SteamVoiceCallServiceMode.INCOMING -> {
                        stopActiveResources()
                        startForegroundCompat(
                            notification = publisher.notification(state),
                            microphoneActive = false
                        )
                    }
                    SteamVoiceCallServiceMode.IDLE -> {
                        stopActiveResources()
                        publisher.cancel()
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = SteamVoiceCallRuntime.get(this)
        when (intent?.action) {
            ACTION_ACCEPT -> runtime.acceptIncomingFromNotification()
            ACTION_REJECT -> runtime.rejectIncomingFromNotification()
            ACTION_TOGGLE_MIC -> runtime.toggleMicrophone()
            ACTION_TOGGLE_OUTPUT -> runtime.toggleOutput()
            ACTION_STOP -> runtime.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        stopActiveResources()
        publisher.cancel()
        super.onDestroy()
    }

    private fun startActiveResources() {
        if (!audioSessionStarted) {
            runCatching(audioSession::start)
                .onSuccess { audioSessionStarted = true }
                .onFailure { error ->
                    SteamDiagLogger.append(
                        "voice_audio_session failed type=${error::class.java.simpleName}"
                    )
                }
        }
        if (!wakeLock.isHeld) {
            runCatching { wakeLock.acquire() }.onFailure { error ->
                SteamDiagLogger.append(
                    "voice_wake_lock failed type=${error::class.java.simpleName}"
                )
            }
        }
    }

    private fun stopActiveResources() {
        if (audioSessionStarted) {
            audioSession.stop()
            audioSessionStarted = false
        }
        if (::wakeLock.isInitialized && wakeLock.isHeld) runCatching { wakeLock.release() }
    }

    private fun startForegroundCompat(
        notification: android.app.Notification,
        microphoneActive: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                if (microphoneActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(
                SteamVoiceNotificationPublisher.NOTIFICATION_ID,
                notification,
                types
            )
        } else {
            startForeground(SteamVoiceNotificationPublisher.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_ACCEPT = "takagi.ru.monica.steam.voice.ACCEPT"
        const val ACTION_REJECT = "takagi.ru.monica.steam.voice.REJECT"
        const val ACTION_TOGGLE_MIC = "takagi.ru.monica.steam.voice.TOGGLE_MIC"
        const val ACTION_TOGGLE_OUTPUT = "takagi.ru.monica.steam.voice.TOGGLE_OUTPUT"
        const val ACTION_STOP = "takagi.ru.monica.steam.voice.STOP"
        private const val WAKE_LOCK_TAG = "Etoile:VoiceCall"

        fun start(context: android.content.Context, action: String? = null) {
            androidx.core.content.ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SteamVoiceCallService::class.java)
                    .setAction(action)
            )
        }

        fun stop(context: android.content.Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, SteamVoiceCallService::class.java)
            )
        }
    }
}
