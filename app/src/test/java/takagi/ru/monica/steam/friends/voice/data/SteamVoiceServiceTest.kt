package takagi.ru.monica.steam.friends.voice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceWebRtcSession
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamVoiceServiceTest {
    @Test
    fun performsOfficialWebRtcAndGroupVoiceCalls() {
        val cm = RecordingCmGateway()
        val service = SteamVoiceService(cm)
        cm.response = SteamProtoWriter().apply {
            writeString(1, "{\"type\":\"answer\",\"sdp\":\"v=0\"}")
        }.toByteArray()

        val answer = service.initiateWebRtc(account(), "offer", "Chrome", "126")
        assertEquals("WebRTCClient.InitiateWebRTCConnection#1", cm.method)
        assertTrue(answer.contains("answer"))

        cm.response = SteamProtoWriter().apply { writeUint64(1, "7001") }.toByteArray()
        assertEquals("7001", service.joinGroupVoice(account(), "8001", "9002"))
        assertEquals("ChatRoom.JoinVoiceChat#1", cm.method)

        service.leaveGroupVoice(account(), "8001", "9002")
        assertEquals("ChatRoom.LeaveVoiceChat#1", cm.method)
    }

    @Test
    fun performsDirectVoiceAndStatusNotificationCalls() {
        val cm = RecordingCmGateway(
            response = SteamProtoWriter().apply { writeFixed64(1, 7001L) }.toByteArray()
        )
        val service = SteamVoiceService(cm)

        assertEquals("7001", service.requestDirectVoice(account(), PARTNER_ID))
        assertEquals("VoiceChat.RequestOneOnOneChat#1", cm.method)

        service.answerDirectVoice(account(), PARTNER_ID, "7001", true)
        assertEquals("VoiceChat.AnswerOneOnOneChat#1", cm.method)
        assertTrue(SteamProtoReader(cm.request).parse().getValue(3).asBool)

        service.notifyVoiceStatus(account(), "7001", true, false, false)
        assertEquals("VoiceChat.NotifyUserVoiceStatus#1", cm.notificationMethod)
        val status = SteamProtoReader(cm.notificationRequest).parse()
        assertTrue(status.getValue(3).asBool)
        assertEquals(48_000L, status.getValue(6).asLong)
    }

    @Test
    fun sendsOfficialVoiceWebRtcSessionCoordinates() {
        val cm = RecordingCmGateway()
        val service = SteamVoiceService(cm)
        val session = SteamVoiceWebRtcSession(33L, 11L, 22, 44L, 55)

        service.updateVoiceWebRtcData(account(), "7001", session, "Etoile")

        assertEquals("VoiceChat.UpdateVoiceChatWebRTCData#1", cm.method)
        val fields = SteamProtoReader(cm.request).parse()
        assertEquals(44L, fields.getValue(2).asLong)
        assertEquals(11L, fields.getValue(4).asLong)
        assertEquals(33L, fields.getValue(6).asLong)
    }

    private class RecordingCmGateway(
        var response: ByteArray = byteArrayOf()
    ) : SteamCmGateway {
        var method = ""
        var request = byteArrayOf()
        var notificationMethod = ""
        var notificationRequest = byteArrayOf()

        override fun callService(
            account: SteamAccount,
            method: String,
            request: ByteArray
        ): ByteArray {
            this.method = method
            this.request = request
            return response
        }

        override fun exchangeClientMessage(
            account: SteamAccount,
            requestEMsg: Int,
            responseEMsg: Int,
            request: ByteArray
        ) = byteArrayOf()

        override fun sendServiceNotification(
            account: SteamAccount,
            method: String,
            request: ByteArray
        ) {
            notificationMethod = method
            notificationRequest = request
        }
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "account",
        displayName = "Account",
        deviceId = "device",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = "secure",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 1,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val PARTNER_ID = "76561198000000002"
    }
}
