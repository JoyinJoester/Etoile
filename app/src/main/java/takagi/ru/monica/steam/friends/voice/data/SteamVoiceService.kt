package takagi.ru.monica.steam.friends.voice.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceGateway
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceWebRtcSession
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

/** Official CM/WebRTC signaling boundary. Audio transport lives in the voice runtime. */
class SteamVoiceService(
    private val cm: SteamCmGateway = SteamCmClient()
) : SteamVoiceGateway {
    override fun initiateWebRtc(
        account: SteamAccount,
        localDescriptionJson: String,
        clientName: String,
        clientVersion: String
    ): String = call(
        account,
        "WebRTCClient.InitiateWebRTCConnection",
        SteamProtoWriter().apply {
            writeString(1, localDescriptionJson)
            writeString(2, clientName)
            writeString(3, clientVersion)
        }
    ).let { response ->
        SteamProtoReader(response).parse()[1]?.asString.orEmpty()
            .takeIf(String::isNotBlank)
            ?: error("Steam did not return a WebRTC remote description")
    }

    override fun updateWebRtc(
        account: SteamAccount,
        session: SteamVoiceWebRtcSession,
        localDescriptionJson: String
    ): String = call(
        account,
        "WebRTCClient.UpdateWebRTCConnection",
        SteamProtoWriter().apply {
            writeVarint(1, session.serverIp)
            writeVarint(2, session.serverPort.toLong())
            writeVarint(3, session.clientIp)
            writeVarint(4, session.clientPort.toLong())
            writeString(5, localDescriptionJson)
        }
    ).let { response ->
        SteamProtoReader(response).parse()[1]?.asString.orEmpty()
            .takeIf(String::isNotBlank)
            ?: error("Steam did not return an updated WebRTC description")
    }

    override fun acknowledgeRemoteDescription(
        account: SteamAccount,
        session: SteamVoiceWebRtcSession,
        version: String
    ) {
        call(
            account,
            "WebRTCClient.AcknowledgeUpdatedRemoteDescription",
            SteamProtoWriter().apply {
                writeVarint(1, session.serverIp)
                writeVarint(2, session.serverPort.toLong())
                writeVarint(3, session.clientIp)
                writeVarint(4, session.clientPort.toLong())
                writeUint64(5, version)
            }
        )
    }

    override fun joinGroupVoice(
        account: SteamAccount,
        groupId: String,
        chatId: String
    ): String = call(
        account,
        "ChatRoom.JoinVoiceChat",
        SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
        }
    ).let { response ->
        SteamProtoReader(response).parse()[1]?.let { field ->
            when (field.wireType) {
                0 -> java.lang.Long.toUnsignedString(field.asLong)
                1 -> field.asFixed64UnsignedString
                else -> ""
            }
        }.orEmpty().takeIf(String::isNotBlank)
            ?: error("Steam did not return a group voice chat ID")
    }

    override fun leaveGroupVoice(account: SteamAccount, groupId: String, chatId: String) {
        call(
            account,
            "ChatRoom.LeaveVoiceChat",
            SteamProtoWriter().apply {
                writeUint64(1, groupId.requireUnsignedId("group"))
                writeUint64(2, chatId.requireUnsignedId("chat"))
            }
        )
    }

    override fun requestDirectVoice(account: SteamAccount, partnerSteamId: String): String = call(
        account,
        "VoiceChat.RequestOneOnOneChat",
        SteamProtoWriter().apply { writeFixed64(1, partnerSteamId.requireSteamId64()) }
    ).let { response ->
        SteamProtoReader(response).parse()[1]?.let { field ->
            when (field.wireType) {
                0 -> java.lang.Long.toUnsignedString(field.asLong)
                1 -> field.asFixed64UnsignedString
                else -> ""
            }
        }.orEmpty().takeIf(String::isNotBlank)
            ?: error("Steam did not return a direct voice chat ID")
    }

    override fun answerDirectVoice(
        account: SteamAccount,
        partnerSteamId: String,
        voiceChatId: String,
        accepted: Boolean
    ) {
        call(
            account,
            "VoiceChat.AnswerOneOnOneChat",
            SteamProtoWriter().apply {
                writeFixed64(1, voiceChatId.requireFixed64Id("voice chat"))
                writeFixed64(2, partnerSteamId.requireSteamId64())
                writeBool(3, accepted)
            }
        )
    }

    override fun leaveDirectVoice(
        account: SteamAccount,
        partnerSteamId: String,
        voiceChatId: String
    ) {
        call(
            account,
            "VoiceChat.LeaveOneOnOneChat",
            SteamProtoWriter().apply {
                writeFixed64(1, partnerSteamId.requireSteamId64())
                writeFixed64(2, voiceChatId.requireFixed64Id("voice chat"))
            }
        )
    }

    override fun updateVoiceWebRtcData(
        account: SteamAccount,
        voiceChatId: String,
        session: SteamVoiceWebRtcSession,
        userAgent: String
    ) {
        call(
            account,
            "VoiceChat.UpdateVoiceChatWebRTCData",
            SteamProtoWriter().apply {
                writeFixed64(1, voiceChatId.requireFixed64Id("voice chat"))
                writeVarint(2, session.serverIp)
                writeVarint(3, session.serverPort.toLong())
                writeVarint(4, session.clientIp)
                writeVarint(5, session.clientPort.toLong())
                writeVarint(6, session.ssrc)
                writeString(7, userAgent)
                writeBool(8, false)
                writeBool(9, false)
                writeBool(10, false)
                writeBool(11, false)
            }
        )
    }

    override fun notifyVoiceStatus(
        account: SteamAccount,
        voiceChatId: String,
        microphoneMuted: Boolean,
        outputMuted: Boolean,
        hasNoMicrophone: Boolean
    ) {
        cm.sendServiceNotification(
            account,
            "VoiceChat.NotifyUserVoiceStatus#1",
            SteamProtoWriter().apply {
                writeFixed64(1, voiceChatId.requireFixed64Id("voice chat"))
                writeFixed64(2, account.steamId.requireSteamId64())
                writeBool(3, microphoneMuted)
                writeBool(4, outputMuted)
                writeBool(5, hasNoMicrophone)
                // Opus/WebRTC uses a 48 kHz RTP clock. Steam's official client
                // reports the active WebAudio rate instead of leaving it zero.
                writeVarint(6, WEBRTC_AUDIO_SAMPLE_RATE.toLong())
                writeBool(7, false)
            }.toByteArray()
        )
    }

    private fun call(account: SteamAccount, method: String, request: SteamProtoWriter): ByteArray {
        require(account.hasRealSteamId) { "Real Steam ID required for voice chat" }
        require(!account.accessToken.isNullOrBlank()) { "Steam access token required for voice chat" }
        return cm.callService(account, "$method#1", request.toByteArray())
    }

    private fun String.requireSteamId64(): Long {
        require(matches(STEAM_ID_PATTERN)) { "Valid Steam ID required" }
        return toLong()
    }

    private fun String.requireUnsignedId(label: String): String = apply {
        require(toBigIntegerOrNull()?.signum()?.let { it >= 0 } == true) {
            "Valid Steam $label ID required"
        }
    }

    private fun String.requireFixed64Id(label: String): Long = toBigIntegerOrNull()
        ?.takeIf { it.signum() >= 0 && it <= UNSIGNED_LONG_MAX }
        ?.toLong()
        ?: throw IllegalArgumentException("Valid Steam $label ID required")

    private companion object {
        const val WEBRTC_AUDIO_SAMPLE_RATE = 48_000
        val UNSIGNED_LONG_MAX = java.math.BigInteger.ONE.shiftLeft(64).subtract(java.math.BigInteger.ONE)
        val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
    }
}
