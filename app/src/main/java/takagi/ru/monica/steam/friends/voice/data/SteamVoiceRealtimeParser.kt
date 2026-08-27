package takagi.ru.monica.steam.friends.voice.data

import takagi.ru.monica.steam.friends.chat.domain.steamId64FromAccountId
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRemoteDescription
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceWebRtcSession
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

internal object SteamVoiceRealtimeParser {
    fun parse(envelope: SteamCmEnvelope): SteamVoiceRealtimeEvent? {
        if (envelope.eMsg !in SUPPORTED_SERVICE_MESSAGES) return null
        val method = envelope.header.targetJobName?.substringBefore('#') ?: return null
        return runCatching {
            when (method) {
                INCOMING_DIRECT_METHOD -> parseIncomingDirect(envelope.body)
                DIRECT_RESPONSE_METHOD -> parseDirectResponse(envelope.body)
                USER_JOINED_METHOD -> parseUserPresence(envelope.body, joined = true)
                USER_LEFT_METHOD -> parseUserPresence(envelope.body, joined = false)
                USER_STATUS_METHOD -> parseUserStatusEvent(envelope.body)
                ALL_USERS_STATUS_METHOD -> parseAllUsersStatus(envelope.body)
                VOICE_ENDED_METHOD -> parseVoiceEnded(envelope.body)
                REJOIN_METHOD -> parseRejoin(envelope.body)
                WEBRTC_CONNECTED_METHOD -> parseWebRtcConnected(envelope.body)
                REMOTE_DESCRIPTION_METHOD -> parseRemoteDescription(envelope.body)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseIncomingDirect(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val voiceId = fields[1].fixed64Id() ?: return null
        val partner = fields[2].steamId64() ?: return null
        return SteamVoiceRealtimeEvent.IncomingDirectRequest(partner, voiceId)
    }

    private fun parseDirectResponse(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val voiceId = fields[1].fixed64Id() ?: return null
        val partner = fields[2].steamId64() ?: return null
        return SteamVoiceRealtimeEvent.DirectResponse(
            partnerSteamId = partner,
            voiceChatId = voiceId,
            accepted = fields[3]?.asBool == true
        )
    }

    private fun parseUserPresence(
        payload: ByteArray,
        joined: Boolean
    ): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val voiceId = fields[1].fixed64Id() ?: return null
        val steamId = fields[2].steamId64() ?: return null
        val chatId = fields[3].unsignedVarintId()
        val groupId = fields[6].unsignedVarintId()
        return if (joined) {
            SteamVoiceRealtimeEvent.UserJoined(voiceId, steamId, groupId, chatId)
        } else {
            SteamVoiceRealtimeEvent.UserLeft(voiceId, steamId, groupId, chatId)
        }
    }

    private fun parseUserStatusEvent(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val voiceId = fields[1].fixed64Id() ?: return null
        val participant = parseParticipant(fields) ?: return null
        return SteamVoiceRealtimeEvent.UserStatus(voiceId, participant)
    }

    private fun parseAllUsersStatus(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parseAll()
        val voiceId = fields.firstOrNull { it.number == 1 }.fixed64Id() ?: return null
        val participants = fields.filter { it.number == 2 && it.bytes != null }
            .mapNotNull { field ->
                SteamProtoReader(field.bytes!!).parse().let(::parseParticipant)
            }
            .distinctBy(SteamVoiceParticipant::steamId)
        return SteamVoiceRealtimeEvent.AllUsersStatus(voiceId, participants)
    }

    private fun parseParticipant(fields: Map<Int, SteamProtoField>): SteamVoiceParticipant? {
        val steamId = fields[2].steamId64() ?: return null
        return SteamVoiceParticipant(
            steamId = steamId,
            micMuted = fields[3]?.asBool == true,
            outputMuted = fields[4]?.asBool == true,
            hasNoMic = fields[5]?.asBool == true
        )
    }

    private fun parseVoiceEnded(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val voiceId = fields[1].fixed64Id() ?: return null
        val lower = fields[2].steamId64()
        val higher = fields[3].steamId64()
        return SteamVoiceRealtimeEvent.VoiceEnded(
            voiceChatId = voiceId,
            groupId = fields[5].unsignedVarintId(),
            chatId = fields[4].unsignedVarintId(),
            partnerSteamId = listOfNotNull(lower, higher).firstOrNull()
        )
    }

    private fun parseRejoin(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val chatId = fields[1].unsignedVarintId() ?: return null
        val groupId = fields[2].unsignedVarintId() ?: return null
        return SteamVoiceRealtimeEvent.RejoinRequired(groupId, chatId)
    }

    private fun parseWebRtcConnected(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parse()
        val session = SteamVoiceWebRtcSession(
            ssrc = fields[1]?.asLong?.and(UINT32_MASK) ?: return null,
            clientIp = fields[2]?.asLong?.and(UINT32_MASK) ?: return null,
            clientPort = fields[3]?.asInt?.takeIf { it > 0 } ?: return null,
            serverIp = fields[4]?.asLong?.and(UINT32_MASK) ?: return null,
            serverPort = fields[5]?.asInt?.takeIf { it > 0 } ?: return null
        )
        return SteamVoiceRealtimeEvent.WebRtcConnected(session)
    }

    private fun parseRemoteDescription(payload: ByteArray): SteamVoiceRealtimeEvent? {
        val fields = SteamProtoReader(payload).parseAll()
        val description = fields.firstOrNull { it.number == 1 }?.asString
            ?.takeIf(String::isNotBlank) ?: return null
        val version = fields.firstOrNull { it.number == 2 }
            ?.let { java.lang.Long.toUnsignedString(it.asLong) }
            .orEmpty()
            .ifBlank { "0" }
        val mappings = fields.filter { it.number == 3 && it.bytes != null }
            .mapNotNull { field ->
                val mapping = SteamProtoReader(field.bytes!!).parse()
                val ssrc = mapping[1]?.asLong?.and(UINT32_MASK) ?: return@mapNotNull null
                val accountId = mapping[2]?.asLong?.and(UINT32_MASK) ?: return@mapNotNull null
                ssrc to steamId64FromAccountId(accountId)
            }
            .toMap()
        return SteamVoiceRealtimeEvent.RemoteDescriptionUpdated(
            SteamVoiceRemoteDescription(description, version, mappings)
        )
    }

    private fun SteamProtoField?.fixed64Id(): String? = this
        ?.takeIf { it.wireType == WIRE_TYPE_FIXED64 }
        ?.asFixed64UnsignedString
        ?.takeIf { it != "0" }

    private fun SteamProtoField?.steamId64(): String? = fixed64Id()
        ?.takeIf { it.matches(STEAM_ID_PATTERN) }

    private fun SteamProtoField?.unsignedVarintId(): String? = this
        ?.takeIf { it.wireType == WIRE_TYPE_VARINT }
        ?.let { java.lang.Long.toUnsignedString(it.asLong) }
        ?.takeIf { it != "0" }

    private const val INCOMING_DIRECT_METHOD = "VoiceChatClient.NotifyOneOnOneChatRequested"
    private const val DIRECT_RESPONSE_METHOD = "VoiceChatClient.NotifyOneOnOneChatResponse"
    private const val USER_JOINED_METHOD = "VoiceChatClient.NotifyUserJoinedVoiceChat"
    private const val USER_LEFT_METHOD = "VoiceChatClient.NotifyUserLeftVoiceChat"
    private const val USER_STATUS_METHOD = "VoiceChatClient.NotifyUserVoiceStatus"
    private const val ALL_USERS_STATUS_METHOD = "VoiceChatClient.NotifyAllUsersVoiceStatus"
    private const val VOICE_ENDED_METHOD = "VoiceChatClient.NotifyVoiceChatEnded"
    private const val REJOIN_METHOD = "ChatRoomClient.NotifyShouldRejoinChatRoomVoiceChat"
    private const val WEBRTC_CONNECTED_METHOD =
        "WebRTCClientNotifications.NotifyWebRTCSessionConnected"
    private const val REMOTE_DESCRIPTION_METHOD =
        "WebRTCClientNotifications.NotifyWebRTCUpdateRemoteDescription"
    private const val WIRE_TYPE_VARINT = 0
    private const val WIRE_TYPE_FIXED64 = 1
    private const val UINT32_MASK = 0xffff_ffffL
    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
    private val SUPPORTED_SERVICE_MESSAGES = setOf(
        SteamCmProtocol.EMSG_SERVICE_METHOD,
        SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT
    )
}
