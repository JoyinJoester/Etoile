package takagi.ru.monica.steam.friends.voice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmHeader
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamVoiceRealtimeParserTest {
    @Test
    fun parsesIncomingDirectRequestAndResponse() {
        val incoming = SteamVoiceRealtimeParser.parse(
            envelope(
                "VoiceChatClient.NotifyOneOnOneChatRequested#1",
                SteamProtoWriter().apply {
                    writeFixed64(1, VOICE_CHAT_ID)
                    writeFixed64(2, PARTNER_STEAM_ID.toLong())
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.IncomingDirectRequest
        val response = SteamVoiceRealtimeParser.parse(
            envelope(
                "VoiceChatClient.NotifyOneOnOneChatResponse#1",
                SteamProtoWriter().apply {
                    writeFixed64(1, VOICE_CHAT_ID)
                    writeFixed64(2, PARTNER_STEAM_ID.toLong())
                    writeBool(3, true)
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.DirectResponse

        assertEquals(PARTNER_STEAM_ID, incoming.partnerSteamId)
        assertEquals(VOICE_CHAT_ID.toString(), incoming.voiceChatId)
        assertTrue(response.accepted)
    }

    @Test
    fun parsesVoiceMembersStatusAndWebRtcSession() {
        val status = SteamProtoWriter().apply {
            writeFixed64(1, VOICE_CHAT_ID)
            writeFixed64(2, PARTNER_STEAM_ID.toLong())
            writeBool(3, true)
            writeBool(4, false)
            writeBool(5, false)
        }
        val allStatus = SteamVoiceRealtimeParser.parse(
            envelope(
                "VoiceChatClient.NotifyAllUsersVoiceStatus#1",
                SteamProtoWriter().apply {
                    writeFixed64(1, VOICE_CHAT_ID)
                    writeMessage(2, status)
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.AllUsersStatus
        val connected = SteamVoiceRealtimeParser.parse(
            envelope(
                "WebRTCClientNotifications.NotifyWebRTCSessionConnected#1",
                SteamProtoWriter().apply {
                    writeVarint(1, 123L)
                    writeVarint(2, 0x7f000001L)
                    writeVarint(3, 27020L)
                    writeVarint(4, 0x7f000001L)
                    writeVarint(5, 27021L)
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.WebRtcConnected

        assertEquals(PARTNER_STEAM_ID, allStatus.participants.single().steamId)
        assertTrue(allStatus.participants.single().micMuted)
        assertEquals(123L, connected.session.ssrc)
        assertEquals(27021, connected.session.serverPort)
    }

    @Test
    fun parsesRemoteDescriptionMappingsAndGroupRejoin() {
        val mapping = SteamProtoWriter().apply {
            writeVarint(1, 88L)
            writeVarint(2, 39_734_274L)
        }
        val description = SteamVoiceRealtimeParser.parse(
            envelope(
                "WebRTCClientNotifications.NotifyWebRTCUpdateRemoteDescription#1",
                SteamProtoWriter().apply {
                    writeString(1, "{\"type\":\"offer\",\"sdp\":\"v=0\"}")
                    writeUint64(2, "7")
                    writeMessage(3, mapping)
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.RemoteDescriptionUpdated
        val rejoin = SteamVoiceRealtimeParser.parse(
            envelope(
                "ChatRoomClient.NotifyShouldRejoinChatRoomVoiceChat#1",
                SteamProtoWriter().apply {
                    writeUint64(1, "9002")
                    writeUint64(2, "8001")
                }.toByteArray()
            )
        ) as SteamVoiceRealtimeEvent.RejoinRequired

        assertEquals("7", description.description.version)
        assertEquals("76561198000000002", description.description.ssrcToSteamIds[88L])
        assertEquals("8001", rejoin.groupId)
        assertEquals("9002", rejoin.chatId)
    }

    private fun envelope(method: String, body: ByteArray) = SteamCmEnvelope(
        eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT,
        header = SteamCmHeader(targetJobName = method),
        body = body
    )

    private companion object {
        const val PARTNER_STEAM_ID = "76561198000000002"
        const val VOICE_CHAT_ID = 7_001L
    }
}
