package takagi.ru.monica.steam.friends.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.nickname.domain.SteamFriendNicknameGateway
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamFriendsServiceTest {
    @Test
    fun fetchesOAuthFriendsAndMergesProfiles() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val payload = when {
                    request.url.host == "steamcommunity.com" -> "<html><body></body></html>"
                    request.url.encodedPath.contains("GetFriendList") -> """{
                        "friendslist":{"friends":[
                          {"steamid":"76561198000000002","relationship":"friend","friend_since":100},
                          {"steamid":"76561198000000003","relationship":"requestinitiator","friend_since":200}
                        ]}
                    }"""
                    request.url.encodedPath.contains("GetUserSummaries") -> """{
                        "response":{"players":[
                          {"steamid":"76561198000000002","personaname":"Alyx","avatarfull":"https://avatars.cloudflare.steamstatic.com/a.jpg","personastate":1,"gameid":"730","gameextrainfo":"Counter-Strike 2"},
                          {"steamid":"76561198000000003","personaname":"Gordon","personastate":0,"lastlogoff":123}
                        ]}
                    }"""
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamFriendsService(
            api = SteamApiClient(client),
            nicknameGateway = SteamFriendNicknameGateway {
                mapOf("76561198000000002" to "Official Alyx note")
            }
        )
            .fetch(account(), fetchedAt = 42L)

        assertEquals(42L, snapshot.fetchedAt)
        assertEquals(2, snapshot.friends.size)
        assertEquals(1, snapshot.acceptedFriends.size)
        assertEquals(1, snapshot.incomingRequests.size)
        assertEquals(1, snapshot.onlineCount)
        val playing = snapshot.acceptedFriends.single()
        assertEquals("Official Alyx note", playing.displayName)
        assertEquals("Alyx", playing.personaName)
        assertEquals("Counter-Strike 2", playing.gameName)
        assertTrue(playing.isPlaying)
        assertEquals(
            listOf(
                "/ISteamUserOAuth/GetFriendList/v1/",
                "/my/friends/pending",
                "/ISteamUserOAuth/GetUserSummaries/v1/"
            ),
            requests.map { it.url.encodedPath }
        )
        assertTrue(
            requests.filter { it.url.host == "api.steampowered.com" }
                .all { it.url.queryParameter("access_token") == "access-token" }
        )
        assertEquals("all", requests.first().url.queryParameter("relationship"))
    }

    @Test
    fun nicknameFailureKeepsTheOAuthFriendListAvailable() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val payload = when {
                    request.url.host == "steamcommunity.com" -> "<html><body></body></html>"
                    request.url.encodedPath.contains("GetFriendList") -> """{
                        "friendslist":{"friends":[
                          {"steamid":"76561198000000002","relationship":"friend","nickname":"OAuth note"}
                        ]}
                    }"""
                    request.url.encodedPath.contains("GetUserSummaries") -> """{
                        "response":{"players":[
                          {"steamid":"76561198000000002","personaname":"Alyx","personastate":1}
                        ]}
                    }"""
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamFriendsService(
            api = SteamApiClient(client),
            nicknameGateway = SteamFriendNicknameGateway { error("CM unavailable") }
        ).fetch(account(), fetchedAt = 43L)

        assertEquals("OAuth note", snapshot.acceptedFriends.single().displayName)
        assertEquals(43L, snapshot.fetchedAt)
    }

    @Test
    fun pendingCommunityInvitesAreAddedToTheFriendSnapshot() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val payload = when {
                    request.url.host == "steamcommunity.com" -> """
                        <div class="invite_row">
                          <div class="invite_row_content" data-miniprofile="43147274"></div>
                        </div>
                    """.trimIndent()
                    request.url.encodedPath.contains("GetFriendList") ->
                        """{"friendslist":{"friends":[]}}"""
                    request.url.encodedPath.contains("GetUserSummaries") -> """{
                        "response":{"players":[{
                          "steamid":"76561198003413002",
                          "personaname":"Pending Decks",
                          "avatarfull":"https://avatars.fastly.steamstatic.com/pending.jpg"
                        }]}
                    }"""
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamFriendsService(
            api = SteamApiClient(client),
            nicknameGateway = SteamFriendNicknameGateway { emptyMap() }
        ).fetch(account(), fetchedAt = 44L)

        val request = snapshot.incomingRequests.single()
        assertEquals("76561198003413002", request.steamId)
        assertEquals("Pending Decks", request.displayName)
        val pendingRequest = requests.single { it.url.encodedPath == "/my/friends/pending" }
        assertTrue(pendingRequest.header("Cookie").orEmpty().contains("steamLoginSecure="))
    }

    @Test
    fun pendingCommunityFailureKeepsOAuthFriendsAvailable() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val code = if (request.url.host == "steamcommunity.com") 503 else 200
                val payload = when {
                    request.url.host == "steamcommunity.com" -> "Unavailable"
                    request.url.encodedPath.contains("GetFriendList") -> """{
                        "friendslist":{"friends":[
                          {"steamid":"76561198000000002","relationship":"friend"}
                        ]}
                    }"""
                    request.url.encodedPath.contains("GetUserSummaries") -> """{
                        "response":{"players":[{
                          "steamid":"76561198000000002",
                          "personaname":"Alyx"
                        }]}
                    }"""
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Unavailable")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamFriendsService(
            api = SteamApiClient(client),
            nicknameGateway = SteamFriendNicknameGateway { emptyMap() }
        ).fetch(account(), fetchedAt = 45L)

        assertEquals("Alyx", snapshot.acceptedFriends.single().displayName)
        assertTrue(snapshot.incomingRequests.isEmpty())
    }

    @Test
    fun successfulCommunityFriendInviteActionsDoNotRequireAJsonBody() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val service = SteamFriendsService(api = SteamApiClient(client))

        val accepted = service.respondToInvite(account(), FRIEND_STEAM_ID, accept = true)
        val ignored = service.respondToInvite(account(), FRIEND_STEAM_ID, accept = false)

        assertTrue(accepted.success)
        assertTrue(ignored.success)
        assertEquals(
            listOf("/actions/AddFriendAjax", "/actions/IgnoreFriendInviteAjax"),
            requests.map { it.url.encodedPath }
        )
        val acceptForm = requests[0].body as FormBody
        assertEquals(FRIEND_STEAM_ID, acceptForm.value("steamid"))
        assertEquals("1", acceptForm.value("accept_invite"))
        assertTrue(acceptForm.value("sessionID").orEmpty().isNotBlank())
        val ignoreForm = requests[1].body as FormBody
        assertEquals(FRIEND_STEAM_ID, ignoreForm.value("steamid"))
        assertTrue(ignoreForm.value("accept_invite") == null)
    }

    @Test
    fun parserAcceptsNumericRelationshipCodesAndMissingProfiles() {
        val relationships = SteamFriendsParser.parseRelationships(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"response":{"friends":[{"ulfriendid":"76561198000000004","efriendrelationship":2,"time_created":9}]}}"""
            ).jsonObject
        )

        val friend = SteamFriendsParser.merge(relationships, emptyMap()).single()

        assertEquals(SteamFriendRelationship.REQUEST_OUTGOING, friend.relationship)
        assertEquals(9L, friend.friendSince)
        assertEquals("76561198000000004", friend.displayName)
    }

    @Test
    fun addFriendUsesTheOfficialCmRequestAndResult() {
        val cm = FakeFriendsCm(
            SteamProtoWriter().apply { writeVarint(1, 1L) }.toByteArray()
        )
        val result = SteamFriendsService(cm = cm).changeRelationship(
            account(),
            FRIEND_STEAM_ID,
            SteamFriendRelationshipAction.ADD
        )

        assertTrue(result.success)
        assertEquals(SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND, cm.requestEMsg)
        assertEquals(SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND_RESPONSE, cm.responseEMsg)
        assertEquals(FRIEND_STEAM_ID, SteamProtoReader(cm.request).parse()[1]?.asFixed64UnsignedString)
    }

    @Test
    fun friendCodeLookupLoadsTheCalculatedSteamProfile() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{
                          "response":{"players":[{
                            "steamid":"76561198003413002",
                            "personaname":"Decks",
                            "avatarfull":"https://avatars.fastly.steamstatic.com/decks.jpg"
                          }]}
                        }""".toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

        val candidates = SteamFriendsService(
            api = SteamApiClient(client),
            inviteLinkResolver = SteamFriendInviteLinkResolver { error("Unexpected invite link") }
        ).findCandidates(account(), "43147274")

        assertEquals(1, candidates.size)
        assertEquals("76561198003413002", candidates.single().steamId)
        assertEquals("Decks", candidates.single().personaName)
        assertEquals(1, requests.size)
        assertEquals(
            "76561198003413002",
            requests.single().url.queryParameter("steamids")
        )
    }

    @Test
    fun nameLookupUsesTheAuthenticatedCommunitySearch() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val payload = if (request.url.host == "steamcommunity.com") {
                    """{
                      "success":1,
                      "html":"<div class='search_row'><div data-miniprofile='43147274'><div class='avatarMedium'><img src='https://avatars.fastly.steamstatic.com/fallback.jpg'></div></div><a class='searchPersonaName' href='https://steamcommunity.com/profiles/76561198003413002'>Decks</a></div>"
                    }"""
                } else {
                    """{
                      "response":{"players":[{
                        "steamid":"76561198003413002",
                        "personaname":"Decks",
                        "avatarfull":"https://avatars.fastly.steamstatic.com/decks.jpg"
                      }]}
                    }"""
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val candidates = SteamFriendsService(
            api = SteamApiClient(client),
            inviteLinkResolver = SteamFriendInviteLinkResolver { error("Unexpected invite link") }
        ).findCandidates(account(), "Decks")

        assertEquals("Decks", candidates.single().displayName)
        val communityRequest = requests.first { it.url.host == "steamcommunity.com" }
        assertEquals("/search/SearchCommunityAjax", communityRequest.url.encodedPath)
        assertEquals("Decks", communityRequest.url.queryParameter("text"))
        assertEquals(account().steamId, communityRequest.url.queryParameter("steamid_user"))
        assertTrue(communityRequest.header("Cookie").orEmpty().contains("steamLoginSecure="))
    }

    @Test
    fun removeBlockAndUnblockWaitForTheFriendsListStateEcho() {
        SteamFriendRelationshipAction.entries
            .filter { it != SteamFriendRelationshipAction.ADD }
            .forEach { action ->
                val cm = FakeFriendsCm(SteamProtoWriter().apply { writeBool(1, false) }.toByteArray())
                val result = SteamFriendsService(cm = cm).changeRelationship(account(), FRIEND_STEAM_ID, action)

                assertTrue(result.success)
                assertEquals(SteamCmProtocol.EMSG_CLIENT_FRIENDS_LIST, cm.responseEMsg)
                assertEquals(
                    if (action == SteamFriendRelationshipAction.REMOVE) {
                        SteamCmProtocol.EMSG_CLIENT_REMOVE_FRIEND
                    } else {
                        SteamCmProtocol.EMSG_CLIENT_HIDE_FRIEND
                    },
                    cm.requestEMsg
                )
                val fields = SteamProtoReader(cm.request).parse()
                assertEquals(FRIEND_STEAM_ID, fields[1]?.asFixed64UnsignedString)
                if (action != SteamFriendRelationshipAction.REMOVE) {
                    assertEquals(action == SteamFriendRelationshipAction.BLOCK, fields[2]?.asBool)
                }
            }
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    private companion object {
        const val FRIEND_STEAM_ID = "76561198000000002"
    }
}

private fun FormBody.value(name: String): String? =
    (0 until size).firstOrNull { encodedName(it) == name }?.let(::value)

private class FakeFriendsCm(private val response: ByteArray) : SteamCmGateway {
    var requestEMsg: Int = 0
    var responseEMsg: Int = 0
    var request: ByteArray = ByteArray(0)

    override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray =
        error("Unexpected service call")

    override fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray
    ): ByteArray {
        this.requestEMsg = requestEMsg
        this.responseEMsg = responseEMsg
        this.request = request
        return response
    }
}
