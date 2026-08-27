package takagi.ru.monica.steam.profile.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamProfileViewerParserTest {
    @Test
    fun communityFriendRowsBecomeOpenableProfiles() {
        val friends = SteamProfileViewerParser.parseCommunityFriends(
            """
            <div class="friend_block_v2 persona online" data-steamid="76561199440036973"
                 data-search="nagisa ;  ; ">
              <a class="selectable_overlay" href="https://steamcommunity.com/profiles/76561199440036973"></a>
              <div class="player_avatar"><img src="https://avatars.steamstatic.com/avatar_medium.jpg"></div>
              <div class="friend_block_content">nagisa<br><span class="friend_small_text"></span></div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, friends.size)
        assertEquals("76561199440036973", friends.single().steamId)
        assertEquals("nagisa", friends.single().displayName)
        assertTrue(friends.single().personaState.isOnline)
        assertTrue(friends.single().profileUrl.endsWith("76561199440036973"))
    }

    @Test
    fun communityGroupRowsKeepImageCountsAndOfficialUrl() {
        val groups = SteamProfileViewerParser.parseCommunityGroups(
            """
            <div class="group_block invite_row" data-search="steam trading cards grouptradingcards">
              <div class="group_block_medium">
                <img src="https://avatars.steamstatic.com/group_medium.jpg">
              </div>
              <div class="group_block_details">
                <div class="groupTitle">
                  <a class="linkTitle" href="https://steamcommunity.com/groups/tradingcards">Steam Trading Cards Group</a>
                </div>
                <div class="memberRow">
                  <a href="https://steamcommunity.com/groups/tradingcards/members">2,308,609 Members</a>
                  <span class="membersInGame">67,266 In-Game</span>
                  <span class="membersOnline">424,012 Online</span>
                  <a href="javascript:OpenGroupChat( '103582791434277245' )">233365 In Group Chat</a>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, groups.size)
        assertEquals("103582791434277245", groups.single().groupId)
        assertEquals(2_308_609, groups.single().memberCount)
        assertEquals(424_012, groups.single().onlineCount)
        assertEquals("https://steamcommunity.com/groups/tradingcards", groups.single().profileUrl)
    }
}
