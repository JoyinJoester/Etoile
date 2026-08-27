package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatChannelCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoleActions
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReactionType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReportReason
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamGroupChatServiceTest {
    @Test
    fun resolvesMissingAvatarFromOfficialFullGroupState() {
        val sha = ByteArray(20) { it.toByte() }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeUint64(1, "8001")
                    writeBytes(16, sha)
                })
            })
        }.toByteArray()
        val cm = RecordingCmGateway(response)

        val url = SteamGroupChatService(cm).getGroupAvatarUrl(account(), "8001")

        assertEquals("ChatRoom.GetChatRoomGroupState#1", cm.method)
        assertTrue(url.endsWith("000102030405060708090a0b0c0d0e0f10111213_256.jpg"))
    }

    @Test
    fun updatesOfficialGroupAvatarWithGroupIdAndSha() {
        val cm = RecordingCmGateway()
        val sha = ByteArray(20) { it.toByte() }

        SteamGroupChatService(cm).updateGroupAvatar(account(), "8001", sha)

        assertEquals("ChatRoom.SetChatRoomGroupAvatar#1", cm.method)
        val fields = SteamProtoReader(cm.request).parse()
        assertEquals("8001", java.lang.Long.toUnsignedString(fields.getValue(1).asLong))
        assertArrayEquals(sha, fields.getValue(2).bytes)
    }

    @Test
    fun createsTextAndVoiceChannelsWithOfficialFields() {
        val cm = RecordingCmGateway(
            response = SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeUint64(1, "9002")
                    writeString(2, "Voice")
                    writeBool(3, true)
                    writeVarint(6, 2L)
                })
            }.toByteArray()
        )

        val room = SteamGroupChatService(cm).createChannel(
            account(),
            "8001",
            SteamGroupChatChannelCreateRequest("Voice", allowVoice = true)
        )

        assertEquals("ChatRoom.CreateChatRoom#1", cm.method)
        val fields = SteamProtoReader(cm.request).parse()
        assertEquals("8001", java.lang.Long.toUnsignedString(fields.getValue(1).asLong))
        assertEquals("Voice", fields.getValue(2).asString)
        assertEquals(true, fields.getValue(3).asBool)
        assertEquals("9002", room.chatId)
        assertTrue(room.voiceAllowed)
    }

    @Test
    fun renamesReordersAndDeletesChannelWithOfficialMethods() {
        val cm = RecordingCmGateway()
        val service = SteamGroupChatService(cm)

        service.renameChannel(account(), "8001", "9001", "Lobby")
        assertEquals("ChatRoom.RenameChatRoom#1", cm.method)
        assertEquals("Lobby", SteamProtoReader(cm.request).parse().getValue(3).asString)

        service.reorderChannel(account(), "8001", "9001", "9002")
        assertEquals("ChatRoom.ReorderChatRoom#1", cm.method)
        assertEquals("9002", java.lang.Long.toUnsignedString(SteamProtoReader(cm.request).parse().getValue(3).asLong))

        service.deleteChannel(account(), "8001", "9001")
        assertEquals("ChatRoom.DeleteChatRoom#1", cm.method)
    }

    @Test
    fun createsAndDeletesOfficialInviteLinks() {
        val cm = RecordingCmGateway(
            response = SteamProtoWriter().apply {
                writeString(1, "invite-code")
                writeVarint(2, 3_600L)
            }.toByteArray()
        )
        val service = SteamGroupChatService(cm, nowMillis = { 1_000L })

        val link = service.createInviteLink(account(), "8001", 3_600L, "9001")
        assertEquals("ChatRoom.CreateInviteLink#1", cm.method)
        assertEquals("https://s.team/chat/invite-code", link.shareUrl)
        assertEquals(3_601_000L, link.expiresAt)

        service.deleteInviteLink(account(), "8001", "invite-code")
        assertEquals("ChatRoom.DeleteInviteLink#1", cm.method)
        assertEquals("invite-code", SteamProtoReader(cm.request).parse().getValue(2).asString)
    }

    @Test
    fun sendsOfficialRolePermissionsAndMemberModerationFields() {
        val cm = RecordingCmGateway()
        val service = SteamGroupChatService(cm)

        service.replaceRoleActions(
            account(),
            "8001",
            SteamGroupChatRoleActions(
                roleId = "6001",
                canCreateRenameDeleteChannel = true,
                canKick = true,
                canBan = true,
                canInvite = true,
                canChat = true,
                canViewHistory = true
            )
        )
        assertEquals("ChatRoom.ReplaceRoleActions#1", cm.method)
        val permissionFields = SteamProtoReader(
            SteamProtoReader(cm.request).parse().getValue(4).bytes!!
        ).parse()
        assertTrue(permissionFields.getValue(2).asBool)
        assertTrue(permissionFields.getValue(4).asBool)

        service.setUserBanState(account(), "8001", "76561198000000002", true)
        assertEquals("ChatRoom.SetUserBanState#1", cm.method)
        assertTrue(SteamProtoReader(cm.request).parse().getValue(3).asBool)

        service.kickUser(account(), "8001", "76561198000000002", 3_600)
        assertEquals("ChatRoom.KickUserFromGroup#1", cm.method)
        assertEquals(3_600L, SteamProtoReader(cm.request).parse().getValue(3).asLong)
    }

    @Test
    fun reactsReportsAndDeletesOfficialGroupMessages() {
        val cm = RecordingCmGateway()
        val service = SteamGroupChatService(cm)
        val message = SteamGroupChatMessage(
            groupId = "8001",
            chatId = "9001",
            senderSteamId = "76561198000000002",
            timestamp = 500L,
            ordinal = 3,
            body = "spam"
        )

        service.updateMessageReaction(
            account(), message, SteamGroupChatReactionType.EMOTICON, "heart", true
        )
        assertEquals("ChatRoom.UpdateMessageReaction#1", cm.method)
        assertEquals(":heart:", SteamProtoReader(cm.request).parse().getValue(6).asString)

        service.reportMessage(account(), message, SteamGroupChatReportReason.SPAM)
        assertEquals("ChatRoom.ReportMessage#1", cm.method)
        assertEquals(5L, SteamProtoReader(cm.request).parse().getValue(9).asLong)

        service.deleteMessage(account(), message.copy(senderSteamId = account().steamId))
        assertEquals("ChatRoom.DeleteChatMessages#1", cm.method)
        val nested = SteamProtoReader(cm.request).parse().getValue(3).bytes!!
        assertEquals(3L, SteamProtoReader(nested).parse().getValue(2).asLong)
    }

    private class RecordingCmGateway(
        private val response: ByteArray = byteArrayOf()
    ) : SteamCmGateway {
        var method = ""
        var request = byteArrayOf()
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
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
}
