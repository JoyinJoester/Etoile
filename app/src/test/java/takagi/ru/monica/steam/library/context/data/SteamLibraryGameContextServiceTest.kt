package takagi.ru.monica.steam.library.context.data

import java.util.Base64
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
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcOwnership
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLibraryGameContextServiceTest {
    @Test
    fun storeInterestStateMarksOwnedDlcMissingFromOwnedGames() {
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/appdetails" -> Stub.json(
                    """{"620":{"success":true,"data":{"dlc":[621],"categories":[]}}}"""
                )
                request.url.encodedPath.contains("GetItems") -> Stub.proto(
                    dlcStoreItems(621 to "Owned DLC")
                )
                request.url.encodedPath.contains("GetOwnedGames") -> Stub.proto(ownedGames())
                request.url.encodedPath.contains("GetUserGameInterestState") -> Stub.proto(
                    SteamProtoWriter().apply { writeBool(1, true) }.toByteArray()
                )
                else -> error("Family API should not be called for an owned DLC: ${request.url}")
            }
        }

        val result = SteamLibraryGameContextService(
            api = SteamApiClient(client)
        ).fetch(
            account = account(),
            game = SteamGame(620, "Portal 2", 120, 0),
            countryCode = "CN",
            language = "schinese"
        ) as SteamLibraryResult.Success

        assertEquals(SteamLibraryDlcOwnership.OWNED, result.value.dlc.single().ownership)
    }

    @Test
    fun fetchBuildsOwnedSharedDlcAndCloudSummary() {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when {
                request.url.encodedPath == "/api/appdetails" -> Stub.json(
                    """{"620":{"success":true,"data":{
                      "dlc":[621,622],"categories":[{"id":23}]
                    }}}"""
                )
                request.url.encodedPath.contains("GetItems") -> Stub.proto(
                    dlcStoreItems(
                        621 to "Owned DLC",
                        622 to "Shared DLC"
                    )
                )
                request.url.encodedPath.contains("GetOwnedGames") -> Stub.proto(
                    ownedGames(621)
                )
                request.url.encodedPath.contains("GetUserGameInterestState") -> Stub.proto(
                    SteamProtoWriter().apply { writeBool(1, false) }.toByteArray()
                )
                request.url.encodedPath.contains("GetFamilyGroupForUser") -> Stub.proto(
                    SteamProtoWriter().apply { writeVarint(1, 42L) }.toByteArray()
                )
                request.url.encodedPath.contains("GetSharedLibraryApps") -> Stub.proto(
                    SteamProtoWriter().apply {
                        writeMessage(1, sharedApp(622, FAMILY_OWNER))
                    }.toByteArray()
                )
                request.url.encodedPath.contains("GetAppFileChangelist") -> Stub.proto(
                    SteamProtoWriter().apply {
                        writeUint64(1, 88L)
                        writeMessage(2, SteamProtoWriter().apply {
                            writeString(1, "save.sav")
                            writeUint64(3, 1_700_000_000L)
                            writeVarint(4, 4_096L)
                        })
                    }.toByteArray()
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = SteamLibraryGameContextService(
            api = SteamApiClient(client),
            nowMillis = { 123L }
        ).fetch(
            account = account(),
            game = SteamGame(620, "Portal 2", 120, 0),
            countryCode = "CN",
            language = "schinese"
        ) as SteamLibraryResult.Success

        val context = result.value
        assertEquals(123L, context.fetchedAt)
        assertEquals(SteamLibraryCloudStatus.AVAILABLE, context.cloud.status)
        assertEquals(1, context.cloud.fileCount)
        assertEquals(4_096L, context.cloud.totalBytes)
        assertEquals(
            listOf(SteamLibraryDlcOwnership.OWNED, SteamLibraryDlcOwnership.FAMILY_SHARED),
            context.dlc.map { it.ownership }
        )
        assertEquals(listOf(FAMILY_OWNER.toString()), context.dlc.last().ownerSteamIds)

        val ownedRequest = requests.single {
            it.url.encodedPath.contains("GetOwnedGames")
        }
        val requestFields = SteamProtoReader(
            Base64.getDecoder().decode(
                ownedRequest.url.queryParameter("input_protobuf_encoded")
            )
        ).parseAll()
        val filteredIds = requestFields.first { it.number == 5 }.bytes
            ?.let(SteamProtoReader::decodePackedVarints)
            .orEmpty()
        assertEquals(listOf(621L, 622L), filteredIds)
    }

    @Test
    fun knownUnsupportedCloudSkipsTheCloudEndpoint() {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            if (request.url.encodedPath == "/api/appdetails") {
                Stub.json(
                    """{"620":{"success":true,"data":{"categories":[{"id":2}]}}}"""
                )
            } else {
                error("No protobuf endpoint should be called: ${request.url}")
            }
        }

        val result = SteamLibraryGameContextService(
            api = SteamApiClient(client)
        ).fetch(
            account = account(),
            game = SteamGame(620, "Portal 2", 120, 0),
            countryCode = "CN",
            language = "schinese"
        ) as SteamLibraryResult.Success

        assertEquals(SteamLibraryCloudStatus.NOT_SUPPORTED, result.value.cloud.status)
        assertFalse(requests.any { it.url.encodedPath.contains("GetAppFileChangelist") })
    }

    @Test
    fun expiredCloudSessionReturnsSessionRequiredForViewModelRetry() {
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/appdetails" -> Stub.json(
                    """{"620":{"success":true,"data":{"categories":[{"id":23}]}}}"""
                )
                request.url.encodedPath.contains("GetAppFileChangelist") -> Stub(
                    code = 401,
                    bytes = ByteArray(0),
                    contentType = "application/octet-stream",
                    eResult = 5
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = SteamLibraryGameContextService(
            api = SteamApiClient(client)
        ).fetch(
            account = account(),
            game = SteamGame(620, "Portal 2", 120, 0),
            countryCode = "CN",
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Failure)
        assertEquals(
            SteamLibraryFailureReason.SESSION_REQUIRED,
            (result as SteamLibraryResult.Failure).reason
        )
    }

    private fun client(responder: (Request) -> Stub): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val stub = responder(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(stub.code)
                .message(if (stub.code in 200..299) "OK" else "Error")
                .apply {
                    if (stub.eResult != null) header("x-eresult", stub.eResult.toString())
                }
                .body(stub.bytes.toResponseBody(stub.contentType.toMediaType()))
                .build()
        }
        .build()

    private fun ownedGames(vararg appIds: Int): ByteArray = SteamProtoWriter().apply {
        writeVarint(1, appIds.size.toLong())
        appIds.forEach { appId ->
            writeMessage(2, SteamProtoWriter().apply { writeVarint(1, appId.toLong()) })
        }
    }.toByteArray()

    private fun dlcStoreItems(vararg items: Pair<Int, String>): ByteArray =
        SteamProtoWriter().apply {
            items.forEach { (appId, name) ->
                writeMessage(1, SteamProtoWriter().apply {
                    writeString(6, name)
                    writeVarint(9, appId.toLong())
                    writeMessage(30, SteamProtoWriter().apply {
                        writeString(1, "steam/apps/$appId/\${FILENAME}")
                        writeString(4, "header.jpg")
                    })
                })
            }
        }.toByteArray()

    private fun sharedApp(appId: Int, ownerSteamId: Long): SteamProtoWriter =
        SteamProtoWriter().apply {
            writeVarint(1, appId.toLong())
            writeFixed64(2, ownerSteamId)
            writeString(6, "Shared DLC")
            writeVarint(10, 0L)
        }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_ID,
        accountName = "account",
        displayName = "Account",
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
        createdAt = 0L,
        updatedAt = 0L
    )

    private data class Stub(
        val code: Int,
        val bytes: ByteArray,
        val contentType: String,
        val eResult: Int? = null
    ) {
        companion object {
            fun json(value: String) = Stub(
                code = 200,
                bytes = value.toByteArray(),
                contentType = "application/json"
            )

            fun proto(value: ByteArray) = Stub(
                code = 200,
                bytes = value,
                contentType = "application/octet-stream"
            )
        }
    }

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
        const val FAMILY_OWNER = 76561198000000002L
    }
}
