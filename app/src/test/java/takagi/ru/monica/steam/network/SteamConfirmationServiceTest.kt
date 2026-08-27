package takagi.ru.monica.steam.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamConfirmationServiceTest {
    @Test
    fun fetchParsesSuccessfulEmptyConfirmationList() {
        val service = serviceFor("""{"success":true,"conf":[]}""")

        assertEquals(emptyList<SteamConfirmation>(), service.fetch(account(), nowSeconds = 1L))
    }

    @Test
    fun fetchExposesSteamFailureInsteadOfReturningEmptyList() {
        val service = serviceFor("""{"success":false,"message":"Session expired"}""")

        val error = assertThrows(SteamApiException::class.java) {
            service.fetch(account(), nowSeconds = 1L)
        }
        assertEquals("Session expired", error.message)
    }

    private fun serviceFor(payload: String): SteamConfirmationService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return SteamConfirmationService(SteamApiClient(client))
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
        identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
        revocationCode = "R12345",
        tokenGid = "token-gid",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
