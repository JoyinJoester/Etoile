package takagi.ru.monica.steam.friends.voice.background

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceIncomingRequest
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

class SteamVoiceCallServicePolicyTest {
    @Test
    fun idleStateStopsForegroundResources() {
        assertEquals(
            SteamVoiceCallServiceMode.IDLE,
            SteamVoiceCallState().voiceServiceMode()
        )
    }

    @Test
    fun incomingCallKeepsNotificationWithoutOpeningAudioResources() {
        assertEquals(
            SteamVoiceCallServiceMode.INCOMING,
            SteamVoiceCallState(
                incomingRequest = SteamVoiceIncomingRequest(
                    partnerSteamId = "76561198000000001",
                    voiceChatId = "voice-1"
                )
            ).voiceServiceMode()
        )
    }

    @Test
    fun outgoingOrAcceptedCallOwnsAudioResources() {
        assertEquals(
            SteamVoiceCallServiceMode.ACTIVE,
            SteamVoiceCallState(
                target = SteamVoiceTarget(
                    type = SteamVoiceTargetType.DIRECT,
                    title = "Friend",
                    partnerSteamId = "76561198000000001"
                ),
                state = SteamVoiceConnectionState.CONNECTING_MEDIA
            ).voiceServiceMode()
        )
    }
}
