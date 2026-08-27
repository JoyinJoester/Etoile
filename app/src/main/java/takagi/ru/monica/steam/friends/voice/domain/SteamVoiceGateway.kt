package takagi.ru.monica.steam.friends.voice.domain

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.steam.data.SteamAccount

interface SteamVoiceGateway {
    fun initiateWebRtc(
        account: SteamAccount,
        localDescriptionJson: String,
        clientName: String,
        clientVersion: String
    ): String

    fun updateWebRtc(
        account: SteamAccount,
        session: SteamVoiceWebRtcSession,
        localDescriptionJson: String
    ): String

    fun acknowledgeRemoteDescription(
        account: SteamAccount,
        session: SteamVoiceWebRtcSession,
        version: String
    )

    fun joinGroupVoice(
        account: SteamAccount,
        groupId: String,
        chatId: String
    ): String

    fun leaveGroupVoice(account: SteamAccount, groupId: String, chatId: String)

    fun requestDirectVoice(account: SteamAccount, partnerSteamId: String): String

    fun answerDirectVoice(
        account: SteamAccount,
        partnerSteamId: String,
        voiceChatId: String,
        accepted: Boolean
    )

    fun leaveDirectVoice(
        account: SteamAccount,
        partnerSteamId: String,
        voiceChatId: String
    )

    fun updateVoiceWebRtcData(
        account: SteamAccount,
        voiceChatId: String,
        session: SteamVoiceWebRtcSession,
        userAgent: String
    )

    fun notifyVoiceStatus(
        account: SteamAccount,
        voiceChatId: String,
        microphoneMuted: Boolean,
        outputMuted: Boolean,
        hasNoMicrophone: Boolean
    )
}

fun interface SteamVoiceRealtimeGateway {
    fun events(account: SteamAccount): Flow<SteamVoiceRealtimeEvent>
}
