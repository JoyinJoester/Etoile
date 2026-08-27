package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatGameInviteParserTest {
    @Test
    fun parsesSelfClosingGameInviteWithSteamAttributeAliases() {
        val content = SteamChatRichContentParser.parse(
            "[gameinvite app_id=730 lobby_id=123456789 steam_id=76561198000000001]"
        )

        assertTrue(content is SteamChatRichContent.GameInvite)
        val invite = content as SteamChatRichContent.GameInvite
        assertEquals(730, invite.appId)
        assertEquals("123456789", invite.lobbyId)
        assertEquals("76561198000000001", invite.inviterSteamId)
        assertEquals(
            "steam://joinlobby/730/123456789/76561198000000001",
            invite.url
        )
    }

    @Test
    fun parsesSteamRunGameIdLinksAsGameInvites() {
        val content = SteamChatRichContentParser.parse("steam://rungameid/730")

        assertTrue(content is SteamChatRichContent.GameInvite)
        val invite = content as SteamChatRichContent.GameInvite
        assertEquals(730, invite.appId)
        assertEquals("steam://rungameid/730", invite.url)
    }
}
