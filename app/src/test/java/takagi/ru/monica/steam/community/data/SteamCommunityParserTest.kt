package takagi.ru.monica.steam.community.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge

class SteamCommunityParserTest {
    @Test
    fun parsesProfileLevelBadgesAndRecentGames() {
        val profile = SteamCommunityParser.profile(jsonObject("""
            {"response":{"players":[{
              "steamid":"76561198000000001",
              "personaname":"Alyx",
              "realname":"Alyx Vance",
              "avatarfull":"https://avatars.example/alyx.jpg",
              "profileurl":"https://steamcommunity.com/id/alyx/",
              "loccountrycode":"CN",
              "locstatecode":"31",
              "loccityid":100,
              "summary":"Resistance member",
              "timecreated":10,
              "lastlogoff":20,
              "communityvisibilitystate":3
            }]}}
        """))!!
        val level = SteamCommunityParser.level(jsonObject(
            """{"response":{"player_level":42}}"""
        ))
        val badges = SteamCommunityParser.badges(jsonObject("""
            {"response":{
              "badges":[
                {"badgeid":1,"level":3,"xp":300,"completion_time":50,"scarcity":7},
                {"badgeid":0,"level":9,"xp":900}
              ],
              "player_xp":4200,
              "player_xp_needed_to_level_up":800
            }}
        """))
        val games = SteamCommunityParser.recentGames(jsonObject("""
            {"response":{"games":[{
              "appid":620,
              "name":"Portal 2",
              "img_icon_url":"iconhash",
              "playtime_forever":1200,
              "playtime_2weeks":90,
              "rtime_last_played":1234
            }]}}
        """))

        assertEquals("76561198000000001", profile.steamId)
        assertEquals("Alyx", profile.displayName)
        assertEquals("Resistance member", profile.summary)
        assertEquals(42, level)
        assertEquals(1, badges.badges.size)
        assertEquals(4200, badges.playerXp)
        assertEquals(800, badges.playerXpNeededToLevelUp)
        assertEquals("Portal 2", games.single().name)
        assertEquals(
            "https://media.steampowered.com/steamcommunity/public/images/apps/620/iconhash.jpg",
            games.single().iconUrl
        )
        assertEquals(90, games.single().playtimeTwoWeeksMinutes)
    }

    @Test
    fun invalidOrMissingProfileDoesNotProduceAPlaceholderPlayer() {
        assertNull(SteamCommunityParser.profile(jsonObject(
            """{"response":{"players":[]}}"""
        )))
        assertNull(SteamCommunityParser.profile(jsonObject(
            """{"response":{"players":[{"steamid":"not-a-steam-id"}]}}"""
        )))
    }

    @Test
    fun parsesAndMergesLiveBadgeTitleArtworkAndDetailLink() {
        val details = SteamCommunityParser.badgeDetails(
            """
            <div id="badge_badge_13" class="badge_row is_link">
              <a class="badge_row_overlay" href="https://steamcommunity.com/id/alyx/badges/13"></a>
              <div class="badge_title">Game Industry Guardian <span class="badge_view_details">View details</span></div>
              <div class="badge_info">
                <div class="badge_info_image">
                  <img data-delayed-image="https://cdn.example/badge13.png" src="https://cdn.example/trans.gif">
                </div>
                <div class="badge_info_title">Game Industry Guardian</div>
                <div>1,782 XP</div>
                <div class="badge_info_unlocked">Unlocked 4 Aug @ 12:16pm</div>
              </div>
            </div>
            """,
            steamId = "76561198000000001"
        )
        val merged = SteamCommunityParser.mergeBadgeDetails(
            badges = listOf(
                SteamCommunityBadge(
                    badgeId = 13,
                    level = 175,
                    xp = 1782,
                    completionTime = 1L,
                    scarcity = 2
                )
            ),
            details = details
        )

        assertEquals(1, details.size)
        assertEquals("Game Industry Guardian", merged.single().name)
        assertEquals("https://cdn.example/badge13.png", merged.single().iconUrl)
        assertEquals(
            "https://steamcommunity.com/id/alyx/badges/13",
            merged.single().detailUrl
        )
        assertEquals("Unlocked 4 Aug @ 12:16pm", merged.single().unlockedAt)
    }

    @Test
    fun matchesGameBadgeDetailsByAppBadgeAndBorderColor() {
        val details = SteamCommunityParser.badgeDetails(
            """
            <div id="badge_gamebadge_570_1_0" class="badge_row is_link">
              <a class="badge_row_overlay" href="/profiles/76561198000000001/gamecards/570/"></a>
              <div class="badge_title">Dota 2</div>
              <div class="badge_info">
                <div class="badge_info_image">
                  <img data-delayed-image="https://community.cloudflare.steamstatic.com/badge.png">
                </div>
                <div class="badge_info_title">Ganker</div>
                <div class="badge_info_description">Level 5, 500 XP</div>
              </div>
            </div>
            """,
            steamId = "76561198000000001"
        )
        val merged = SteamCommunityParser.mergeBadgeDetails(
            badges = listOf(
                SteamCommunityBadge(
                    badgeId = 1,
                    level = 5,
                    xp = 500,
                    completionTime = 1L,
                    scarcity = 3,
                    appId = 570,
                    borderColor = 0
                )
            ),
            details = details
        ).single()

        assertEquals(570, merged.appId)
        assertEquals(1, merged.badgeId)
        assertEquals(0, merged.borderColor)
        assertEquals("Ganker", merged.name)
        assertEquals("Dota 2", merged.gameName)
        assertEquals(5, merged.level)
        assertEquals(500, merged.xp)
        assertEquals(
            "https://steamcommunity.com/profiles/76561198000000001/gamecards/570/",
            merged.detailUrl
        )
    }

    @Test
    fun parsesBadgePageCountAndLockedState() {
        val html = """
            <div class="pageLinks">
              <span class="page_current">1</span>
              <a class="pagelink" href="?p=2&amp;l=english">2</a>
              <a class="pagelink" href="?p=7&amp;l=english">7</a>
            </div>
            <div id="badge_badge_1" class="badge_row is_link">
              <a class="badge_row_overlay" href="/profiles/76561198000000001/badges/1"></a>
              <div class="badge_info_title">Years of Service</div>
              <div class="badge_info_description">Level 4, 200 XP</div>
              <div class="badge_info_unlocked">Unlocked 1 Jan</div>
            </div>
            <div id="badge_gamebadge_620_0_0" class="badge_row is_link">
              <a class="badge_row_overlay" href="/profiles/76561198000000001/gamecards/620/"></a>
              <div class="badge_title">Portal 2</div>
              <div class="badge_info_title">Badge not crafted</div>
              <div class="badge_info_description">0 XP</div>
            </div>
        """.trimIndent()

        val badges = SteamCommunityParser.badgeDetails(
            html = html,
            steamId = "76561198000000001"
        )

        assertEquals(7, SteamCommunityParser.badgePageCount(html))
        assertEquals(2, badges.size)
        assertTrue(badges.first { it.badgeId == 1 }.isUnlocked)
        assertFalse(badges.first { it.appId == 620 }.isUnlocked)
    }

    private fun jsonObject(raw: String) = Json.parseToJsonElement(raw).jsonObject
}
