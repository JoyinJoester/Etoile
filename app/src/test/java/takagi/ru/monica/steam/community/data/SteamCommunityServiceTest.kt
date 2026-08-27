package takagi.ru.monica.steam.community.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException

class SteamCommunityServiceTest {
    @Test
    fun requestsEverySectionWithTheCorrectPlayerParameter() {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            successPayload(request)
        }

        val snapshot = SteamCommunityService(
            api = SteamApiClient(client),
            nowMillis = { 42L }
        ).fetch(account())

        assertEquals(42L, snapshot.fetchedAt)
        assertEquals("Alyx", snapshot.profile?.displayName)
        assertEquals(42, snapshot.steamLevel)
        assertEquals("Portal 2", snapshot.recentGames.single().name)
        assertEquals("Community Ambassador", snapshot.badges.single().name)
        assertEquals("https://cdn.example/community.png", snapshot.badges.single().iconUrl)
        assertTrue(snapshot.unavailableSections.isEmpty())
        assertEquals(5, requests.size)
        val profileRequest = requests.single {
            it.url.encodedPath.contains("GetUserSummaries")
        }
        val badgePageRequest = requests.single {
            it.url.encodedPath.endsWith("/badges/")
        }
        assertEquals("1", badgePageRequest.url.queryParameter("p"))
        assertTrue(badgePageRequest.header("Cookie").orEmpty().contains("steamLoginSecure="))
        assertTrue(requests.filterNot { it === badgePageRequest }.all {
            it.url.queryParameter("access_token") == "access-token"
        })
        assertEquals(ACCOUNT_ID, profileRequest.url.queryParameter("steamids"))
        assertEquals(null, profileRequest.url.queryParameter("steamid"))
        requests.filterNot { it === profileRequest || it === badgePageRequest }.forEach { request ->
            assertEquals(ACCOUNT_ID, request.url.queryParameter("steamid"))
        }
    }

    @Test
    fun oneFailedSectionDoesNotDiscardTheOtherSections() {
        val client = client { request ->
            if (request.url.encodedPath.contains("GetBadges")) {
                HttpPayload(code = 500, body = "{}")
            } else {
                successPayload(request)
            }
        }

        val snapshot = SteamCommunityService(SteamApiClient(client)).fetch(account())

        assertEquals("Alyx", snapshot.profile?.displayName)
        assertEquals(42, snapshot.steamLevel)
        assertEquals("Portal 2", snapshot.recentGames.single().name)
        assertTrue(SteamCommunitySection.BADGES in snapshot.unavailableSections)
        assertFalse(SteamCommunitySection.PROFILE in snapshot.unavailableSections)
    }

    @Test
    fun totalAuthenticationFailureEscapesForOneSessionRefreshRetry() {
        val client = client { HttpPayload(code = 403, body = "{}") }

        val error = runCatching {
            SteamCommunityService(SteamApiClient(client)).fetch(account())
        }.exceptionOrNull()

        assertTrue(error is SteamApiException)
        assertEquals(403, (error as SteamApiException).httpStatusCode)
    }

    private fun client(payload: (Request) -> HttpPayload): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = payload(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(response.code)
                    .message(if (response.code in 200..299) "OK" else "Failed")
                    .body(response.body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    private fun successPayload(request: Request): HttpPayload = HttpPayload(
        code = 200,
        body = when {
            request.url.encodedPath.contains("GetUserSummaries") ->
                """{"response":{"players":[{"steamid":"$ACCOUNT_ID","personaname":"Alyx"}]}}"""
            request.url.encodedPath.contains("GetSteamLevel") ->
                """{"response":{"player_level":42}}"""
            request.url.encodedPath.contains("GetBadges") ->
                """{"response":{"badges":[{"badgeid":1,"level":2,"xp":200}],"player_xp":4200,"player_xp_needed_to_level_up":800}}"""
            request.url.encodedPath.endsWith("/badges/") ->
                """
                <div id="badge_badge_1" class="badge_row is_link">
                  <a class="badge_row_overlay" href="https://steamcommunity.com/profiles/$ACCOUNT_ID/badges/1"></a>
                  <div class="badge_title">Community Ambassador</div>
                  <div class="badge_info">
                    <img class="badge_icon" data-delayed-image="https://cdn.example/community.png">
                    <div class="badge_info_title">Community Ambassador</div>
                    <div>200 XP</div>
                  </div>
                </div>
                """
            request.url.encodedPath.contains("GetRecentlyPlayedGames") ->
                """{"response":{"games":[{"appid":620,"name":"Portal 2"}]}}"""
            else -> error("Unexpected request: ${request.url}")
        }
    )

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_ID,
        accountName = "steam_user",
        displayName = "Alyx",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$ACCOUNT_ID||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    private data class HttpPayload(val code: Int, val body: String)

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
    }
}
