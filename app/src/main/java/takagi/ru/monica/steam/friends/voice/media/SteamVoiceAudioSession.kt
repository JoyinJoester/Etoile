package takagi.ru.monica.steam.friends.voice.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute

/** Maintains voice focus and follows Android communication-device changes during a call. */
internal class SteamVoiceAudioSession(
    context: Context,
    private val onRoutesChanged: (
        List<SteamVoiceAudioRoute>,
        SteamVoiceAudioRoute,
        SteamVoiceAudioRoute
    ) -> Unit = { _, _, _ -> }
) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previousMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousBluetoothScoOn: Boolean? = null
    private var focusRequest: AudioFocusRequest? = null
    private var requestedRoute = SteamVoiceAudioRoute.AUTO
    private var started = false
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            applyRoute(requestedRoute)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            applyRoute(requestedRoute)
        }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            runCatching { SteamDiagLogger.append("voice_audio_focus lost=$change") }
        }
    }

    fun start() {
        if (started) return
        started = true
        previousMode = audioManager.mode
        @Suppress("DEPRECATION")
        run {
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            previousBluetoothScoOn = audioManager.isBluetoothScoOn
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestAudioFocus()
        runCatching { audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler) }
            .onFailure(::logAudioFailure)
        applyRoute(requestedRoute)
    }

    fun applyRoute(route: SteamVoiceAudioRoute) {
        requestedRoute = route
        if (!started) return
        val available = availableRoutes()
        val normalized = route.takeIf { it in available } ?: SteamVoiceAudioRoute.AUTO
        requestedRoute = normalized
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyModernRoute(normalized)
            } else {
                applyLegacyRoute(normalized)
            }
        }.onFailure { error ->
            requestedRoute = SteamVoiceAudioRoute.AUTO
            logAudioFailure(error)
        }
        publishRoutes()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        @Suppress("DEPRECATION")
        runCatching {
            if (previousBluetoothScoOn == true) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn == true
        }
        abandonAudioFocus()
        previousMode?.let { mode -> audioManager.mode = mode }
        previousMode = null
        previousSpeakerphoneOn = null
        previousBluetoothScoOn = null
        requestedRoute = SteamVoiceAudioRoute.AUTO
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun availableRoutes(): List<SteamVoiceAudioRoute> = linkedSetOf(
        SteamVoiceAudioRoute.AUTO
    ).apply {
        currentOutputDevices().mapNotNullTo(this, ::routeForDevice)
        add(SteamVoiceAudioRoute.SPEAKER)
    }.toList()

    private fun publishRoutes() {
        val available = availableRoutes()
        val active = activeRoute(available)
        onRoutesChanged(available, active, requestedRoute)
    }

    private fun activeRoute(available: List<SteamVoiceAudioRoute>): SteamVoiceAudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return runCatching { audioManager.communicationDevice }
                .onFailure(::logAudioFailure)
                .getOrNull()
                ?.let(::routeForDevice)
                ?.takeIf { it in available }
                ?: SteamVoiceAudioRoute.AUTO
        }
        @Suppress("DEPRECATION")
        return when {
            audioManager.isSpeakerphoneOn -> SteamVoiceAudioRoute.SPEAKER
            audioManager.isBluetoothScoOn -> SteamVoiceAudioRoute.BLUETOOTH
            SteamVoiceAudioRoute.WIRED in available -> SteamVoiceAudioRoute.WIRED
            SteamVoiceAudioRoute.EARPIECE in available -> SteamVoiceAudioRoute.EARPIECE
            else -> SteamVoiceAudioRoute.AUTO
        }
    }

    private fun currentOutputDevices(): List<AudioDeviceInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
    }.onFailure(::logAudioFailure).getOrDefault(emptyList())

    private fun routeForDevice(device: AudioDeviceInfo): SteamVoiceAudioRoute? = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> SteamVoiceAudioRoute.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> SteamVoiceAudioRoute.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> SteamVoiceAudioRoute.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> SteamVoiceAudioRoute.BLUETOOTH
        else -> if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) SteamVoiceAudioRoute.BLUETOOTH else null
    }

    private fun applyModernRoute(route: SteamVoiceAudioRoute) {
        if (route == SteamVoiceAudioRoute.AUTO) {
            audioManager.clearCommunicationDevice()
            return
        }
        val device = audioManager.availableCommunicationDevices.firstOrNull {
            routeForDevice(it) == route
        }
        if (device == null || !audioManager.setCommunicationDevice(device)) {
            audioManager.clearCommunicationDevice()
            requestedRoute = SteamVoiceAudioRoute.AUTO
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyRoute(route: SteamVoiceAudioRoute) {
        when (route) {
            SteamVoiceAudioRoute.SPEAKER -> {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = true
            }
            SteamVoiceAudioRoute.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            SteamVoiceAudioRoute.AUTO,
            SteamVoiceAudioRoute.EARPIECE,
            SteamVoiceAudioRoute.WIRED -> {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private fun logAudioFailure(error: Throwable) {
        runCatching {
            SteamDiagLogger.append(
                "voice_audio_route failed type=${error.javaClass.simpleName.ifBlank { "Unknown" }}"
            )
        }
    }
}
