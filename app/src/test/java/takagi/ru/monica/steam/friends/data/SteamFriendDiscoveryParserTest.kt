package takagi.ru.monica.steam.friends.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFriendDiscoveryParserTest {
    @Test
    fun friendCodeIsTheSteamAccountId() {
        val lookup = SteamFriendDiscoveryParser.classify("43147274")

        assertEquals(
            SteamFriendLookup.SteamId("76561198003413002"),
            lookup
        )
    }

    @Test
    fun profileVanityAndQuickInviteLinksUseDedicatedLookups() {
        assertEquals(
            SteamFriendLookup.SteamId("76561197968052866"),
            SteamFriendDiscoveryParser.classify(
                "https://steamcommunity.com/profiles/76561197968052866/"
            )
        )
        assertEquals(
            SteamFriendLookup.VanityName("gaben"),
            SteamFriendDiscoveryParser.classify("steamcommunity.com/id/gaben/")
        )
        assertTrue(
            SteamFriendDiscoveryParser.classify("https://s.team/p/abcde/token")
                is SteamFriendLookup.QuickInvite
        )
    }

    @Test
    fun communitySearchHtmlUsesOfficialMiniProfileAccountIds() {
        val results = SteamFriendDiscoveryParser.parseSearchHtml(
            """
            <div class="search_row">
              <div data-miniprofile="43147274">
                <div class="avatarMedium">
                  <a href="https://steamcommunity.com/profiles/76561198003413002">
                    <img src="https://avatars.fastly.steamstatic.com/a_medium.jpg">
                  </a>
                </div>
              </div>
              <div class="searchPersonaInfo">
                <a class="searchPersonaName"
                   href="https://steamcommunity.com/profiles/76561198003413002">Decks</a>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, results.size)
        assertEquals("76561198003413002", results.single().steamId)
        assertEquals("Decks", results.single().personaName)
        assertEquals(
            "https://avatars.fastly.steamstatic.com/a_medium.jpg",
            results.single().avatarUrl
        )
    }

    @Test
    fun profileXmlAndInviteHtmlResolveSteamIds() {
        assertEquals(
            "76561197968052866",
            SteamFriendDiscoveryParser.parseProfileSteamId(
                "<profile><steamID64>76561197968052866</steamID64></profile>"
            )
        )
        assertEquals(
            "76561198003413002",
            SteamFriendDiscoveryParser.parseProfileSteamId(
                payload = """
                    <div data-miniprofile="1"></div>
                    <a href="https://steamcommunity.com/profiles/76561198003413002/">User</a>
                """.trimIndent(),
                excludedSteamId = "76561197960265729"
            )
        )
    }

    @Test
    fun shortLinksOnlyRedirectToSteamCommunity() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://steamcommunity.com/user/abcde/token/")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val resolved = SteamFriendInviteLinkRedirectResolver(client)
            .resolve("https://s.team/p/abcde/token")

        assertEquals("steamcommunity.com", resolved.host)
        assertEquals("/user/abcde/token/", resolved.encodedPath)
    }
}
