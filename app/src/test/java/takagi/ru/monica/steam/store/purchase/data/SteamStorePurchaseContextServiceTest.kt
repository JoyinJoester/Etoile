package takagi.ru.monica.steam.store.purchase.data

import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure

class SteamStorePurchaseContextServiceTest {
    @Test
    fun storeInterestStateMarksOwnedDlcMissingFromOwnedGames() {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when {
                request.url.encodedPath.contains("GetOwnedGames") -> ownedResponse()
                request.url.encodedPath.contains("GetUserGameInterestState") ->
                    userGameInterestStateResponse(owned = true)
                else -> error("Family API should not be called for an owned DLC")
            }
        }

        val context = SteamStorePurchaseContextService(
            api = SteamApiClient(client),
            nowMillis = { 42L }
        ).fetch(account(), appId = 2896770, language = "schinese")

        assertEquals(SteamStoreOwnershipStatus.OWNED, context.ownership)
        assertTrue(requests.any { it.url.encodedPath.contains("GetUserGameInterestState") })
    }

    @Test
    fun filteredOwnedGamesMarksTheAppOwnedWithoutLoadingFamilyData() {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            when {
                request.url.encodedPath.contains("GetOwnedGames") -> ownedResponse(620)
                else -> error("Family API should not be called for an owned app")
            }
        }

        val context = SteamStorePurchaseContextService(
            api = SteamApiClient(client),
            nowMillis = { 42L }
        ).fetch(account(), appId = 620, language = "schinese")

        assertEquals(SteamStoreOwnershipStatus.OWNED, context.ownership)
        assertEquals(42L, context.fetchedAt)
        val request = requests.single()
        assertEquals("access-token", request.url.queryParameter("access_token"))
        val requestFields = SteamProtoReader(
            Base64.getDecoder().decode(request.url.queryParameter("input_protobuf_encoded"))
        ).parseAll()
        assertTrue(requestFields.any { it.number == 5 && it.asInt == 620 })
    }

    @Test
    fun familySharedAppKeepsOwnerIdsAndCachesTheFamilySnapshot() {
        var familyGroupCalls = 0
        var sharedAppsCalls = 0
        val client = client { request ->
            when {
                request.url.encodedPath.contains("GetOwnedGames") -> ownedResponse()
                request.url.encodedPath.contains("GetUserGameInterestState") ->
                    userGameInterestStateResponse(owned = false)
                request.url.encodedPath.contains("GetFamilyGroupForUser") -> {
                    familyGroupCalls++
                    SteamProtoWriter().apply { writeVarint(1, 42L) }.toByteArray()
                }
                request.url.encodedPath.contains("GetSharedLibraryApps") -> {
                    sharedAppsCalls++
                    SteamProtoWriter().apply {
                        writeMessage(1, sharedApp(620, FAMILY_OWNER))
                    }.toByteArray()
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val service = SteamStorePurchaseContextService(
            api = SteamApiClient(client),
            nowMillis = { 100L }
        )

        val shared = service.fetch(account(), appId = 620, language = "schinese")
        val notOwned = service.fetch(account(), appId = 730, language = "schinese")

        assertEquals(SteamStoreOwnershipStatus.FAMILY_SHARED, shared.ownership)
        assertEquals(listOf(FAMILY_OWNER.toString()), shared.ownerSteamIds)
        assertEquals(42L, shared.familyGroupId)
        assertEquals(SteamStoreOwnershipStatus.NOT_OWNED, notOwned.ownership)
        assertEquals(1, familyGroupCalls)
        assertEquals(1, sharedAppsCalls)
    }

    @Test
    fun failedFamilySnapshotIsRetriedAfterTheSessionIsRefreshed() {
        var familyGroupCalls = 0
        val client = client { request ->
            when {
                request.url.encodedPath.contains("GetOwnedGames") -> ownedResponse()
                request.url.encodedPath.contains("GetUserGameInterestState") ->
                    userGameInterestStateResponse(owned = false)
                request.url.encodedPath.contains("GetFamilyGroupForUser") -> {
                    familyGroupCalls++
                    if (familyGroupCalls == 1) {
                        throw SteamApiException(
                            message = "Session expired",
                            eResult = 5
                        )
                    }
                    SteamProtoWriter().apply { writeVarint(1, 42L) }.toByteArray()
                }
                request.url.encodedPath.contains("GetSharedLibraryApps") ->
                    SteamProtoWriter().toByteArray()
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val service = SteamStorePurchaseContextService(
            api = SteamApiClient(client),
            nowMillis = { 100L }
        )

        val expired = service.fetch(account(), appId = 620, language = "schinese")
        val refreshed = service.fetch(account(), appId = 620, language = "schinese")

        assertEquals(SteamStoreOwnershipStatus.UNKNOWN, expired.ownership)
        assertEquals(SteamStorePurchaseContextFailure.SESSION_REQUIRED, expired.failure)
        assertEquals(SteamStoreOwnershipStatus.NOT_OWNED, refreshed.ownership)
        assertEquals(42L, refreshed.familyGroupId)
        assertEquals(2, familyGroupCalls)
    }

    @Test
    fun parserRejectsMismatchedOwnedGameCounts() {
        val invalid = SteamProtoWriter().apply {
            writeVarint(1, 2L)
            writeMessage(2, SteamProtoWriter().apply { writeVarint(1, 620L) })
        }.toByteArray()

        assertTrue(
            runCatching {
                SteamStorePurchaseContextService.parseOwnedAppIds(invalid)
            }.isFailure
        )
    }

    private fun client(body: (Request) -> ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body(request).toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
        .build()

    private fun ownedResponse(vararg appIds: Int): ByteArray = SteamProtoWriter().apply {
        writeVarint(1, appIds.size.toLong())
        appIds.forEach { appId ->
            writeMessage(2, SteamProtoWriter().apply { writeVarint(1, appId.toLong()) })
        }
    }.toByteArray()

    private fun userGameInterestStateResponse(owned: Boolean): ByteArray =
        SteamProtoWriter().apply { writeBool(1, owned) }.toByteArray()

    private fun sharedApp(appId: Int, ownerSteamId: Long): SteamProtoWriter =
        SteamProtoWriter().apply {
            writeVarint(1, appId.toLong())
            writeFixed64(2, ownerSteamId)
            writeString(6, "Shared app")
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

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
        const val FAMILY_OWNER = 76561198000000002L
    }
}
