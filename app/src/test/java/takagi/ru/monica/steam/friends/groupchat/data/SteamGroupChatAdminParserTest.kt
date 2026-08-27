package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamGroupChatAdminParserTest {
    @Test
    fun parsesMembersRolesAndOfficialRoleActions() {
        val role = SteamProtoWriter().apply {
            writeUint64(1, "6001")
            writeString(2, "Moderator")
            writeVarint(3, 2L)
        }
        val actions = SteamProtoWriter().apply {
            writeUint64(1, "6001")
            writeBool(2, true)
            writeBool(3, true)
            writeBool(4, true)
            writeBool(5, true)
            writeBool(7, true)
            writeBool(8, true)
        }
        val header = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeMessage(18, role)
            writeMessage(19, actions)
        }
        val member = SteamProtoWriter().apply {
            writeVarint(1, 39_734_274L)
            writeVarint(3, 2L)
            writeVarint(4, 30L)
            writeUint64(7, "6001")
        }
        val state = SteamProtoWriter().apply {
            writeMessage(1, header)
            writeMessage(2, member)
        }
        val response = SteamProtoWriter().apply { writeMessage(1, state) }.toByteArray()

        val snapshot = SteamGroupChatAdminParser.parseGroupState(response, fetchedAt = 123L)

        assertEquals("8001", snapshot.groupId)
        assertEquals("76561198000000002", snapshot.members.single().steamId)
        assertEquals(listOf("6001"), snapshot.members.single().roleIds)
        assertEquals("Moderator", snapshot.roles.single().name)
        assertTrue(snapshot.roles.single().actions!!.canCreateRenameDeleteChannel)
        assertTrue(snapshot.roles.single().actions!!.canBan)
        assertFalse(snapshot.roles.single().actions!!.canMentionAll)
    }

    @Test
    fun parsesInviteBanAndShareLinkLists() {
        val invite = SteamProtoWriter().apply {
            writeVarint(1, 39_734_274L)
            writeVarint(2, 39_734_275L)
            writeVarint(3, 100L)
        }
        val ban = SteamProtoWriter().apply {
            writeVarint(1, 39_734_274L)
            writeVarint(2, 39_734_275L)
            writeVarint(3, 200L)
            writeString(4, "spam")
        }
        val link = SteamProtoWriter().apply {
            writeString(1, "invite-code")
            writeFixed64(2, 76_561_198_000_000_003L)
            writeVarint(3, 1_800_000_000L)
            writeUint64(4, "9001")
        }

        val invites = SteamGroupChatAdminParser.parseInviteList(
            SteamProtoWriter().apply { writeMessage(1, invite) }.toByteArray()
        )
        val bans = SteamGroupChatAdminParser.parseBanList(
            SteamProtoWriter().apply { writeMessage(1, ban) }.toByteArray()
        )
        val links = SteamGroupChatAdminParser.parseInviteLinks(
            SteamProtoWriter().apply { writeMessage(1, link) }.toByteArray()
        )

        assertEquals("76561198000000002", invites.single().steamId)
        assertEquals("spam", bans.single().reason)
        assertEquals("https://s.team/chat/invite-code", links.single().shareUrl)
        assertEquals(1_800_000_000_000L, links.single().expiresAt)
        assertEquals("9001", links.single().chatId)
    }
}
