package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmHeader
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamGroupChatRealtimeParserTest {
    @Test
    fun parsesVersionIndependentIncomingMessages() {
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyIncomingChatMessage#9",
                body = SteamProtoWriter().apply {
                    writeUint64(1, GROUP_ID)
                    writeUint64(2, CHAT_ID)
                    writeFixed64(3, SENDER_STEAM_ID.toLong())
                    writeString(4, "[gameinvite appid=570]join[/gameinvite]")
                    writeVarint(5, 1_722_222_222L)
                    writeVarint(7, 11L)
                }.toByteArray()
            )
        )

        assertTrue(event is SteamGroupChatRealtimeEvent.Message)
        val message = (event as SteamGroupChatRealtimeEvent.Message).message
        assertEquals(GROUP_ID, message.groupId)
        assertEquals(CHAT_ID, message.chatId)
        assertEquals(SENDER_STEAM_ID, message.senderSteamId)
        assertEquals(1_722_222_222L, message.timestamp)
        assertEquals(11, message.ordinal)
        assertEquals("[gameinvite appid=570]join[/gameinvite]", message.body)
    }

    @Test
    fun preservesEveryMessageModificationInOneNotification() {
        val first = SteamProtoWriter().apply {
            writeVarint(1, 1_722_222_220L)
            writeVarint(2, 7L)
            writeBool(3, true)
        }
        val second = SteamProtoWriter().apply {
            writeVarint(1, 1_722_222_221L)
            writeVarint(2, 8L)
            writeBool(3, false)
        }
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyChatMessageModified#2",
                body = SteamProtoWriter().apply {
                    writeUint64(1, GROUP_ID)
                    writeUint64(2, CHAT_ID)
                    writeMessage(3, first)
                    writeMessage(3, second)
                }.toByteArray()
            )
        ) as SteamGroupChatRealtimeEvent.MessageModified

        assertEquals(2, event.changes.size)
        assertEquals(listOf(7, 8), event.changes.map { it.ordinal })
        assertEquals(listOf(true, false), event.changes.map { it.deleted })
    }

    @Test
    fun parsesAcknowledgementRoomChangesAndDisconnects() {
        val acknowledgement = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyAckChatMessageEcho#1",
                body = SteamProtoWriter().apply {
                    writeUint64(1, GROUP_ID)
                    writeUint64(2, CHAT_ID)
                    writeVarint(3, 1_722_222_222L)
                }.toByteArray()
            )
        )
        val memberChanged = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyMemberStateChange#1",
                body = SteamProtoWriter().apply { writeUint64(1, GROUP_ID) }.toByteArray()
            )
        )
        val disconnected = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyChatRoomDisconnect#1",
                body = SteamProtoWriter().apply {
                    writeUint64(1, GROUP_ID)
                    writeUint64(1, SECOND_GROUP_ID)
                }.toByteArray()
            )
        )

        assertEquals(
            SteamGroupChatRealtimeEvent.Acknowledged(GROUP_ID, CHAT_ID, 1_722_222_222L),
            acknowledgement
        )
        assertEquals(SteamGroupChatRealtimeEvent.RoomChanged(GROUP_ID), memberChanged)
        assertEquals(
            SteamGroupChatRealtimeEvent.Disconnected(setOf(GROUP_ID, SECOND_GROUP_ID)),
            disconnected
        )
    }

    @Test
    fun readsHeaderStateGroupIdFromNestedHeaderInsteadOfBytesAsZero() {
        val header = SteamProtoWriter().apply {
            writeUint64(1, GROUP_ID)
            writeUint64(2, CHAT_ID)
        }
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyChatRoomHeaderStateChange#1",
                body = SteamProtoWriter().apply {
                    writeMessage(1, header)
                }.toByteArray()
            )
        )

        assertEquals(
            SteamGroupChatRealtimeEvent.HeaderChanged(groupId = GROUP_ID),
            event
        )
    }

    @Test
    fun parsesAvatarNameAndTaglineFromOfficialHeaderNotification() {
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyChatRoomHeaderStateChange#1",
                body = SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeUint64(1, GROUP_ID)
                        writeString(2, "Voice group")
                        writeString(15, "Join the call")
                        writeString(25, "https://steamusercontent-a.akamaihd.net/ugc/123/avatar.png")
                    })
                }.toByteArray()
            )
        )

        assertEquals(
            SteamGroupChatRealtimeEvent.HeaderChanged(
                groupId = GROUP_ID,
                name = "Voice group",
                tagline = "Join the call",
                avatarUrl = "https://steamusercontent-a.akamaihd.net/ugc/123/avatar.png"
            ),
            event
        )
    }

    @Test
    fun realtimeHeaderPrefersOfficialUgcImage() {
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyChatRoomHeaderStateChange#1",
                body = SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeUint64(1, GROUP_ID)
                        writeBytes(16, ByteArray(20) { it.toByte() })
                        writeString(25, "https://steamusercontent-a.akamaihd.net/ugc/123/original.png")
                    })
                }.toByteArray()
            )
        ) as SteamGroupChatRealtimeEvent.HeaderChanged

        assertEquals(
            "https://steamusercontent-a.akamaihd.net/ugc/123/original.png",
            event.avatarUrl
        )
    }

    @Test
    fun ignoresUnsupportedAndMalformedNotifications() {
        val unsupported = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "PlayerClient.NotifyLastPlayedTimes#1",
                body = byteArrayOf()
            )
        )
        val malformed = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyIncomingChatMessage#1",
                body = byteArrayOf(0x0f)
            )
        )

        assertNull(unsupported)
        assertNull(malformed)
    }

    @Test
    fun rendersKnownRealtimeGroupEvent() {
        val serverMessage = SteamProtoWriter().apply { writeVarint(1, 10L) }
        val event = SteamGroupChatRealtimeParser.parse(
            envelope(
                method = "ChatRoomClient.NotifyIncomingChatMessage#1",
                body = SteamProtoWriter().apply {
                    writeUint64(1, GROUP_ID)
                    writeUint64(2, CHAT_ID)
                    writeFixed64(3, SENDER_STEAM_ID.toLong())
                    writeVarint(5, 1_722_222_222L)
                    writeVarint(7, 12L)
                    writeMessage(8, serverMessage)
                }.toByteArray()
            )
        ) as SteamGroupChatRealtimeEvent.Message

        assertEquals("修改了群头像", event.message.body)
    }

    private fun envelope(method: String, body: ByteArray) = SteamCmEnvelope(
        eMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT,
        header = SteamCmHeader(targetJobName = method),
        body = body
    )

    private companion object {
        const val GROUP_ID = "18446744073709551610"
        const val SECOND_GROUP_ID = "8002"
        const val CHAT_ID = "9001"
        const val SENDER_STEAM_ID = "76561198000000003"
    }
}
