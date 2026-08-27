package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoomType

class SteamGroupChatParserTest {
    @Test
    fun parsesGroupRoomsAndUnreadStateFromOfficialChatRoomSchema() {
        val room = SteamProtoWriter().apply {
            writeUint64(1, "9001")
            writeString(2, "General")
            writeVarint(5, 200L)
            writeVarint(6, 1L)
            writeString(7, "Hello group")
            writeVarint(8, 39_734_274L)
        }
        val roomUserState = SteamProtoWriter().apply {
            writeUint64(1, "9001")
            writeVarint(3, 150L)
        }
        val userState = SteamProtoWriter().apply { writeMessage(3, roomUserState) }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Monica testers")
            writeVarint(3, 12L)
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeString(8, "Play together")
            writeVarint(10, 39_734_274L)
            writeVarint(10, 39_734_275L)
            writeVarint(12, 50L)
        }
        val pair = SteamProtoWriter().apply {
            writeMessage(1, userState)
            writeMessage(2, summary)
        }
        val response = SteamProtoWriter().apply { writeMessage(1, pair) }.toByteArray()

        val group = SteamGroupChatParser.parseGroups(response).single()

        assertEquals("8001", group.groupId)
        assertEquals("Monica testers", group.name)
        assertEquals(12, group.activeMemberCount)
        assertEquals(1, group.unreadCount)
        assertEquals(
            listOf("76561198000000002", "76561198000000003"),
            group.topMemberSteamIds
        )
        assertTrue(group.rooms.single().unread)
        assertEquals("76561198000000002", group.rooms.single().lastSenderSteamId)
        assertEquals(SteamGroupChatRoomType.TEXT, group.rooms.single().type)
    }

    @Test
    fun parsesOfficialVoiceAllowedRoomAndUsesDefaultForMultiChannelEntry() {
        val voiceRoom = SteamProtoWriter().apply {
            writeUint64(1, "9002")
            writeString(2, "Voice")
            writeBool(3, true)
            writeVarint(6, 2L)
        }
        val textRoom = SteamProtoWriter().apply {
            writeUint64(1, "9001")
            writeString(2, "General")
            writeVarint(6, 1L)
        }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Channels")
            writeUint64(5, "9002")
            writeMessage(6, voiceRoom)
            writeMessage(6, textRoom)
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply { writeMessage(2, summary) })
        }.toByteArray()

        val group = SteamGroupChatParser.parseGroups(response).single()

        assertEquals("9002", group.preferredChatId)
        assertEquals(SteamGroupChatRoomType.VOICE, group.rooms.last().type)
        assertEquals(listOf("General", "Voice"), group.rooms.map { it.name })
    }

    @Test
    fun parsesMessagesDeletedStateAndServerEvents() {
        val reaction = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeString(2, ":heart:")
            writeVarint(3, 2L)
            writeBool(4, true)
        }
        val normal = SteamProtoWriter().apply {
            writeVarint(1, 39_734_274L)
            writeVarint(2, 300L)
            writeString(3, "Hello")
            writeVarint(4, 2L)
            writeMessage(7, reaction)
        }
        val event = SteamProtoWriter().apply {
            writeVarint(1, 2L)
            writeString(2, "A member joined")
        }
        val system = SteamProtoWriter().apply {
            writeVarint(2, 301L)
            writeVarint(4, 3L)
            writeMessage(5, event)
            writeBool(6, false)
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, normal)
            writeMessage(1, system)
            writeBool(4, true)
        }.toByteArray()

        val page = SteamGroupChatParser.parseHistory(response, "8001", "9001")

        assertEquals(listOf("Hello", "A member joined"), page.messages.map { it.body })
        assertEquals(2, page.messages.last().serverEventType)
        assertFalse(page.messages.last().deleted)
        assertTrue(page.moreAvailable)
        assertEquals(2, page.messages.first().reactions.single().count)
        assertTrue(page.messages.first().reactions.single().hasUserReacted)
    }

    @Test
    fun parsesCreateAndSendResponses() {
        val created = SteamProtoWriter().apply { writeUint64(1, "18446744073709551610") }.toByteArray()
        val sent = SteamProtoWriter().apply {
            writeString(1, "message")
            writeVarint(2, 500L)
            writeVarint(3, 9L)
        }.toByteArray()

        assertEquals("18446744073709551610", SteamGroupChatParser.parseCreatedGroupId(created))
        assertEquals(
            9,
            SteamGroupChatParser.parseSentMessage(sent, "8", "9", "76561198000000001", "message").ordinal
        )
    }

    @Test
    fun parsesOfficialGroupAvatarSha() {
        val sha = ByteArray(20) { it.toByte() }
        val room = SteamProtoWriter().apply { writeUint64(1, "9001") }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Avatar group")
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeBytes(11, sha)
        }
        val pair = SteamProtoWriter().apply { writeMessage(2, summary) }
        val response = SteamProtoWriter().apply { writeMessage(1, pair) }.toByteArray()

        assertEquals(
            "https://community.akamai.steamstatic.com/images/chaticons/00/01/02/" +
                "000102030405060708090a0b0c0d0e0f10111213_256.jpg",
            SteamGroupChatParser.parseGroups(response).single().avatarUrl
        )
    }

    @Test
    fun parsesOfficialGroupAvatarUgcUrlAndVoiceMembers() {
        val voiceRoom = SteamProtoWriter().apply {
            writeUint64(1, "9002")
            writeString(2, "Voice")
            writeBool(3, true)
            writeVarint(4, 39_734_274L)
            writeVarint(4, 39_734_275L)
        }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "UGC avatar group")
            writeVarint(3, 8L)
            writeVarint(4, 2L)
            writeUint64(5, "9002")
            writeMessage(6, voiceRoom)
            writeString(21, "https://steamusercontent-a.akamaihd.net/ugc/123/avatar.png")
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply { writeMessage(2, summary) })
        }.toByteArray()

        val group = SteamGroupChatParser.parseGroups(response).single()

        assertEquals(
            "https://steamusercontent-a.akamaihd.net/ugc/123/avatar.png",
            group.avatarUrl
        )
        assertEquals(2, group.activeVoiceMemberCount)
        assertEquals(
            listOf("76561198000000002", "76561198000000003"),
            group.rooms.single().voiceMemberSteamIds
        )
        assertTrue(group.rooms.single().isVoiceActive)
    }

    @Test
    fun prefersOfficialUgcImageWhenSteamReturnsBothAvatarSources() {
        val sha = ByteArray(20) { it.toByte() }
        val room = SteamProtoWriter().apply { writeUint64(1, "9001") }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Large avatar group")
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeBytes(11, sha)
            writeString(21, "https://steamusercontent-a.akamaihd.net/ugc/123/original.png")
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply { writeMessage(2, summary) })
        }.toByteArray()

        assertEquals(
            "https://steamusercontent-a.akamaihd.net/ugc/123/original.png",
            SteamGroupChatParser.parseGroups(response).single().avatarUrl
        )
    }

    @Test
    fun parsesHexEncodedAvatarShaReturnedAsString() {
        val room = SteamProtoWriter().apply { writeUint64(1, "9001") }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Hex avatar group")
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeString(11, "000102030405060708090a0b0c0d0e0f10111213")
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply { writeMessage(2, summary) })
        }.toByteArray()

        assertEquals(
            "https://community.akamai.steamstatic.com/images/chaticons/00/01/02/" +
                "000102030405060708090a0b0c0d0e0f10111213_256.jpg",
            SteamGroupChatParser.parseGroups(response).single().avatarUrl
        )
    }

    @Test
    fun parsesAvatarFromOfficialHeaderStateFields() {
        val header = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeBytes(16, ByteArray(20) { (19 - it).toByte() })
        }.toByteArray()

        assertEquals(
            "https://community.akamai.steamstatic.com/images/chaticons/13/12/11/" +
                "131211100f0e0d0c0b0a09080706050403020100_256.jpg",
            SteamGroupChatParser.parseGroupHeaderAvatarUrl(header)
        )
    }

    @Test
    fun fullHeaderAlsoPrefersOfficialUgcImage() {
        val header = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeBytes(16, ByteArray(20) { it.toByte() })
            writeString(25, "https://steamusercontent-a.akamaihd.net/ugc/123/original.png")
        }.toByteArray()

        assertEquals(
            "https://steamusercontent-a.akamaihd.net/ugc/123/original.png",
            SteamGroupChatParser.parseGroupHeaderAvatarUrl(header)
        )
    }

    @Test
    fun parsesAvatarFromFullGroupStateWhenSummaryOmitsIt() {
        val sha = ByteArray(20) { (19 - it).toByte() }
        val header = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeBytes(16, sha)
        }
        val state = SteamProtoWriter().apply { writeMessage(1, header) }
        val response = SteamProtoWriter().apply { writeMessage(1, state) }.toByteArray()

        assertEquals(
            "https://community.akamai.steamstatic.com/images/chaticons/13/12/11/" +
                "131211100f0e0d0c0b0a09080706050403020100_256.jpg",
            SteamGroupChatParser.parseGroupStateAvatarUrl(response)
        )
    }

    @Test
    fun rendersKnownSteamGroupEventsInsteadOfNumericFallbacks() {
        val invited = SteamProtoWriter().apply { writeVarint(1, 5L) }
        val avatarChanged = SteamProtoWriter().apply { writeVarint(1, 10L) }
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 39_734_274L)
                writeVarint(2, 300L)
                writeVarint(4, 1L)
                writeMessage(5, invited)
            })
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 39_734_274L)
                writeVarint(2, 301L)
                writeVarint(4, 2L)
                writeMessage(5, avatarChanged)
            })
        }.toByteArray()

        assertEquals(
            listOf("邀请了一位成员加入群聊", "修改了群头像"),
            SteamGroupChatParser.parseHistory(response, "8001", "9001").messages.map { it.body }
        )
    }
}
