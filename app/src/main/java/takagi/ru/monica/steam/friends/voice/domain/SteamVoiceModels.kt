package takagi.ru.monica.steam.friends.voice.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SteamVoiceTargetType { GROUP, DIRECT }

@Serializable
data class SteamVoiceTarget(
    val type: SteamVoiceTargetType,
    val title: String,
    val groupId: String? = null,
    val chatId: String? = null,
    val partnerSteamId: String? = null
) {
    init {
        when (type) {
            SteamVoiceTargetType.GROUP -> require(!groupId.isNullOrBlank() && !chatId.isNullOrBlank()) {
                "Group voice targets require group and chat IDs"
            }
            SteamVoiceTargetType.DIRECT -> require(!partnerSteamId.isNullOrBlank()) {
                "Direct voice targets require a partner Steam ID"
            }
        }
    }
}

@Serializable
enum class SteamVoiceConnectionState {
    IDLE,
    REQUESTING_MICROPHONE,
    CONNECTING_MEDIA,
    WAITING_FOR_ACCEPT,
    CONNECTED,
    RECONNECTING,
    FAILED
}

@Serializable
enum class SteamVoiceAudioRoute {
    AUTO,
    EARPIECE,
    SPEAKER,
    WIRED,
    BLUETOOTH
}

@Serializable
data class SteamVoiceParticipant(
    val steamId: String,
    val joined: Boolean = true,
    val micMuted: Boolean = false,
    val outputMuted: Boolean = false,
    val hasNoMic: Boolean = false
)

@Serializable
data class SteamVoiceIncomingRequest(
    val partnerSteamId: String,
    val voiceChatId: String
)

@Serializable
data class SteamVoiceCallState(
    val accountSteamId: String = "",
    val target: SteamVoiceTarget? = null,
    val voiceChatId: String = "",
    val state: SteamVoiceConnectionState = SteamVoiceConnectionState.IDLE,
    val microphoneMuted: Boolean = false,
    val outputMuted: Boolean = false,
    val audioRoute: SteamVoiceAudioRoute = SteamVoiceAudioRoute.AUTO,
    val requestedAudioRoute: SteamVoiceAudioRoute = SteamVoiceAudioRoute.AUTO,
    val availableAudioRoutes: List<SteamVoiceAudioRoute> = listOf(
        SteamVoiceAudioRoute.AUTO,
        SteamVoiceAudioRoute.SPEAKER
    ),
    val participants: List<SteamVoiceParticipant> = emptyList(),
    val incomingRequest: SteamVoiceIncomingRequest? = null,
    val failure: String? = null
) {
    val isActive: Boolean
        get() = target != null && state != SteamVoiceConnectionState.IDLE

    val isConnected: Boolean
        get() = state == SteamVoiceConnectionState.CONNECTED
}

/** WebRTC server/session coordinates sent by VoiceChatClient notification. */
data class SteamVoiceWebRtcSession(
    val ssrc: Long,
    val clientIp: Long,
    val clientPort: Int,
    val serverIp: Long,
    val serverPort: Int
)

data class SteamVoiceRemoteDescription(
    val descriptionJson: String,
    val version: String = "0",
    val ssrcToSteamIds: Map<Long, String> = emptyMap()
)

sealed interface SteamVoiceRealtimeEvent {
    data class ConnectionChanged(val connected: Boolean) : SteamVoiceRealtimeEvent
    data class IncomingDirectRequest(val partnerSteamId: String, val voiceChatId: String) : SteamVoiceRealtimeEvent
    data class DirectResponse(
        val partnerSteamId: String,
        val voiceChatId: String,
        val accepted: Boolean
    ) : SteamVoiceRealtimeEvent
    data class UserJoined(
        val voiceChatId: String,
        val steamId: String,
        val groupId: String? = null,
        val chatId: String? = null
    ) : SteamVoiceRealtimeEvent
    data class UserLeft(
        val voiceChatId: String,
        val steamId: String,
        val groupId: String? = null,
        val chatId: String? = null
    ) : SteamVoiceRealtimeEvent
    data class UserStatus(
        val voiceChatId: String,
        val participant: SteamVoiceParticipant
    ) : SteamVoiceRealtimeEvent
    data class AllUsersStatus(
        val voiceChatId: String,
        val participants: List<SteamVoiceParticipant>
    ) : SteamVoiceRealtimeEvent
    data class VoiceEnded(
        val voiceChatId: String,
        val groupId: String? = null,
        val chatId: String? = null,
        val partnerSteamId: String? = null
    ) : SteamVoiceRealtimeEvent
    data class RejoinRequired(val groupId: String, val chatId: String) : SteamVoiceRealtimeEvent
    data class WebRtcConnected(val session: SteamVoiceWebRtcSession) : SteamVoiceRealtimeEvent
    data class RemoteDescriptionUpdated(val description: SteamVoiceRemoteDescription) : SteamVoiceRealtimeEvent
}
