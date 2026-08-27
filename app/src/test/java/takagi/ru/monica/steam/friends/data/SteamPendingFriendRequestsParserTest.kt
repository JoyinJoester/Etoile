package takagi.ru.monica.steam.friends.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamPendingFriendRequestsParserTest {
    @Test
    fun parsesSteamIdsFromPendingInviteRows() {
        val html = """
            <div class="invite_row" data-steamid="76561198000000002"></div>
            <div class="invite_row">
                <div class="invite_row_content" data-miniprofile="43147274"></div>
            </div>
            <div class="invite_row">
                <a href="https://steamcommunity.com/profiles/76561198000000004/">Profile</a>
            </div>
        """.trimIndent()

        assertEquals(
            listOf(
                "76561198000000002",
                "76561198003413002",
                "76561198000000004"
            ),
            SteamPendingFriendRequestsParser.parseSteamIds(html)
        )
    }

    @Test
    fun ignoresInvalidAndDuplicateInviteRows() {
        val html = """
            <div class="invite_row" data-steamid="76561198000000002"></div>
            <div class="invite_row"><span data-steamid="76561198000000002"></span></div>
            <div class="invite_row" data-miniprofile="not-a-number"></div>
            <div data-steamid="76561198000000003"></div>
        """.trimIndent()

        assertEquals(
            listOf("76561198000000002"),
            SteamPendingFriendRequestsParser.parseSteamIds(html)
        )
    }
}
