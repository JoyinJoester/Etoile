package takagi.ru.monica.steam.store.freebie.data

import okhttp3.FormBody
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
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimMethod
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextGateway

class SteamFreebieServiceTest {
    @Test
    fun claimPostsTheCurrentOfficialFormInsteadOfCachedOrGeneratedValues() =
        kotlinx.coroutines.test.runTest {
            val requests = mutableListOf<Request>()
            val claimClient = OkHttpClient.Builder()
                .followRedirects(false)
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val isClaim = request.url.encodedPath.contains("addfreelicense")
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (isClaim) 302 else 200)
                        .message(if (isClaim) "Found" else "OK")
                        .apply {
                            if (isClaim) {
                                header("Location", "https://store.steampowered.com/app/738520/")
                            }
                        }
                        .body(
                            if (isClaim) {
                                "".toResponseBody("text/html".toMediaType())
                            } else {
                                """
                                    <form action="/freelicense/addfreelicense/">
                                      <input name="snr" value="1_5_9__403">
                                      <input name="originating_snr" value="">
                                      <input name="action" value="add_to_cart">
                                      <input name="sessionid" value="a4d2cb9bee17a1711e355aa0">
                                      <input name="subid" value="1759598">
                                    </form>
                                """.trimIndent().toResponseBody("text/html".toMediaType())
                            }
                        )
                        .build()
                }
                .build()
            val service = SteamFreebieService(
                claimClient = claimClient,
                purchaseContextGateway = SteamStorePurchaseContextGateway { account, appId, _ ->
                    SteamStorePurchaseContext(
                        accountSteamId = account.steamId,
                        appId = appId,
                        ownership = SteamStoreOwnershipStatus.OWNED
                    )
                },
                verificationDelaysMillis = listOf(0L),
                delayForVerification = {}
            )

            val result = service.claim(account(), item())

            assertEquals(SteamFreebieClaimStatus.CLAIMED, result.status)
            val post = requests.single { it.method == "POST" }
            val body = post.body as FormBody
            assertEquals("a4d2cb9bee17a1711e355aa0", body.value(body.indexOf("sessionid")))
            assertEquals("1_5_9__403", body.value(body.indexOf("snr")))
            assertEquals("", body.value(body.indexOf("originating_snr")))
            assertEquals("1759598", body.value(body.indexOf("subid")))
            assertTrue(post.header("Cookie").orEmpty().contains("sessionid=a4d2cb9bee17a1711e355aa0"))
        }

    private fun FormBody.indexOf(name: String): Int =
        (0 until size).first { index -> this.name(index) == name }

    private fun item() = SteamFreebieItem(
        appId = 738520,
        packageId = 111,
        name = "呼吸边缘",
        storeUrl = "https://store.steampowered.com/app/738520/",
        offerKind = SteamFreebieOfferKind.KEEP_FOREVER,
        claimMethod = SteamFreebieClaimMethod.FREE_LICENSE
    )

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
    }
}
