package takagi.ru.monica.steam.friends.nickname.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamFriendNicknameServiceTest {
    @Test
    fun parsesOfficialAccountIdsIntoSteamIds() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, nickname(accountId = 39_734_274L, value = "Alyx note"))
            writeMessage(1, nickname(accountId = 39_734_275L, value = "Gordon note"))
            writeMessage(1, nickname(accountId = 39_734_276L, value = "   "))
        }.toByteArray()

        assertEquals(
            linkedMapOf(
                "76561198000000002" to "Alyx note",
                "76561198000000003" to "Gordon note"
            ),
            SteamFriendNicknameParser.parse(response)
        )
    }

    @Test
    fun usesAuthenticatedWebApiBeforeCm() {
        val requests = mutableListOf<Request>()
        val response = nicknameResponse("Official note")
        val api = SteamApiClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(response.toResponseBody(PROTOBUF_MEDIA_TYPE))
                    .build()
            }.build()
        )
        val cm = RecordingNicknameCm(response)

        val result = SteamFriendNicknameService(cm = cm, api = api).fetch(account())

        assertEquals(1, requests.size)
        assertEquals("/IPlayerService/GetNicknameList/v1/", requests.single().url.encodedPath)
        assertEquals("access-token", requests.single().url.queryParameter("access_token"))
        assertEquals(0, cm.callCount)
        assertEquals("Official note", result["76561198000000002"])
    }

    @Test
    fun fallsBackToCmWhenWebApiIsUnavailable() {
        val requests = mutableListOf<Request>()
        val api = SteamApiClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Unavailable")
                    .body(ByteArray(0).toResponseBody(PROTOBUF_MEDIA_TYPE))
                    .build()
            }.build()
        )
        val cm = RecordingNicknameCm(nicknameResponse("CM note"))

        val result = SteamFriendNicknameService(cm = cm, api = api).fetch(account())

        assertEquals(1, requests.size)
        assertEquals(1, cm.callCount)
        assertEquals("Player.GetNicknameList#1", cm.method)
        assertEquals(0, cm.request.size)
        assertEquals("CM note", result["76561198000000002"])
    }

    private fun nickname(accountId: Long, value: String) = SteamProtoWriter().apply {
        writeFixed32(1, accountId)
        writeString(2, value)
    }

    private fun nicknameResponse(value: String) = SteamProtoWriter().apply {
        writeMessage(1, nickname(accountId = 39_734_274L, value = value))
    }.toByteArray()

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
        val PROTOBUF_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

private class RecordingNicknameCm(private val response: ByteArray) : SteamCmGateway {
    var callCount: Int = 0
    var method: String = ""
    var request: ByteArray = ByteArray(0)

    override fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray {
        callCount++
        this.method = method
        this.request = request
        return response
    }

    override fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray
    ): ByteArray = error("Unexpected client message")
}
