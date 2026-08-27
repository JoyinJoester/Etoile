package takagi.ru.monica.steam.notifications

import takagi.ru.monica.steam.notifications.data.*
import takagi.ru.monica.steam.notifications.domain.*

import java.util.Base64
import okhttp3.FormBody
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
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader

class SteamNotificationServiceTest {
    @Test
    fun fetchesOfficialNotificationEndpointAndParsesCounters() {
        lateinit var captured: Request
        val payload = """
            {
              "response": {
                "notifications": [{
                  "notification_id": "1234567890123456789",
                  "notification_type": 2,
                  "body_data": "{\"title\":\"Portal 2\",\"gifter_name\":\"Alice\",\"giftid\":\"987\"}",
                  "read": false,
                  "timestamp": 1700000000,
                  "hidden": false,
                  "expiry": 1800000000,
                  "viewed": 0
                }],
                "confirmation_count": 3,
                "pending_gift_count": 1,
                "pending_friend_count": 2,
                "unread_count": 4,
                "pending_family_invite_count": 1
              }
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamNotificationService(SteamApiClient(client))
            .fetch(account(), fetchedAt = 42L)

        assertEquals(
            "/ISteamNotificationService/GetSteamNotifications/v1/",
            captured.url.encodedPath
        )
        assertEquals("access-token", captured.url.queryParameter("access_token"))
        assertEquals("false", captured.url.queryParameter("include_hidden"))
        assertEquals("true", captured.url.queryParameter("include_read"))
        assertEquals("false", captured.url.queryParameter("count_only"))
        assertEquals(4, snapshot.unreadCount)
        assertEquals(1, snapshot.pendingGiftCount)
        assertEquals(3, snapshot.confirmationCount)
        assertEquals(42L, snapshot.fetchedAt)
        val notification = snapshot.notifications.single()
        assertEquals("1234567890123456789", notification.id)
        assertEquals(SteamNotificationKind.GIFT, notification.kind)
        assertEquals("Portal 2", notification.title)
        assertEquals("Alice", notification.summary)
        assertEquals("987", notification.relatedId)
        assertFalse(notification.read)
    }

    @Test
    fun parserKeepsUnknownNotificationsAndMalformedBodyData() {
        val snapshot = SteamNotificationParser.parse(
            """{
              "response": {
                "notifications": [{
                  "notification_id": "9",
                  "notification_type": 99,
                  "body_data": "not-json",
                  "read": true,
                  "timestamp": 12
                }]
              }
            }""",
            fetchedAt = 20L
        )

        val notification = snapshot.notifications.single()
        assertEquals(SteamNotificationKind.UNKNOWN, notification.kind)
        assertEquals("Steam notification", notification.title)
        assertEquals("not-json", notification.summary)
        assertTrue(notification.read)
    }

    @Test
    fun parserMapsEveryOfficialNotificationTypeWithoutUnrelatedFallbackBuckets() {
        val expected = mapOf(
            15 to SteamNotificationKind.PARENTAL_FEATURE_REQUEST,
            16 to SteamNotificationKind.FAMILY_INVITE,
            17 to SteamNotificationKind.FAMILY_PURCHASE_REQUEST,
            18 to SteamNotificationKind.PARENTAL_PLAYTIME_REQUEST,
            19 to SteamNotificationKind.FAMILY_PURCHASE_RESPONSE,
            20 to SteamNotificationKind.PARENTAL_FEATURE_RESPONSE,
            21 to SteamNotificationKind.PARENTAL_PLAYTIME_RESPONSE,
            22 to SteamNotificationKind.REQUESTED_GAME_ADDED,
            23 to SteamNotificationKind.SEND_TO_PHONE,
            24 to SteamNotificationKind.CLIP_DOWNLOADED,
            25 to SteamNotificationKind.TWO_FACTOR_PROMPT,
            26 to SteamNotificationKind.MOBILE_CONFIRMATION,
            27 to SteamNotificationKind.PARTNER_EVENT,
            28 to SteamNotificationKind.PLAYTEST_INVITE,
            29 to SteamNotificationKind.TRADE_REVERSAL,
            30 to SteamNotificationKind.REPORTED_CONTENT_ACTION
        )

        expected.forEach { (type, kind) ->
            assertEquals(kind, SteamNotificationKind.fromType(type))
        }
    }

    @Test
    fun parsesNestedOfficialNotificationText() {
        val snapshot = SteamNotificationParser.parse(
            """{
              "response": {
                "notifications": [{
                  "notification_id": "27",
                  "notification_type": 27,
                  "body_data": "{\"payload\":{\"title\":\"Steam Next Fest\",\"message\":\"A new event is live\",\"appid\":570}}",
                  "read": false,
                  "timestamp": 12
                }]
              }
            }"""
        )

        val notification = snapshot.notifications.single()
        assertEquals(SteamNotificationKind.PARTNER_EVENT, notification.kind)
        assertEquals("Steam Next Fest", notification.title)
        assertEquals("A new event is live", notification.summary)
        assertEquals("570", notification.relatedId)
    }

    @Test
    fun marksNotificationsReadThroughOfficialProtobufEndpoint() {
        lateinit var captured: Request
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("x-eresult", "1")
                    .body(ByteArray(0).toResponseBody())
                    .build()
            }
            .build()

        SteamNotificationService(SteamApiClient(client)).markRead(
            account = account(),
            notificationIds = listOf("123456789", "987654321")
        )

        assertEquals("POST", captured.method)
        assertEquals(
            "/ISteamNotificationService/MarkNotificationsRead/v1/",
            captured.url.encodedPath
        )
        assertEquals("access-token", captured.url.queryParameter("access_token"))
        val form = captured.body as FormBody
        val encoded = (0 until form.size)
            .first { form.name(it) == "input_protobuf_encoded" }
            .let(form::value)
        val ids = SteamProtoReader(Base64.getDecoder().decode(encoded))
            .parseAll()
            .filter { it.number == 3 }
            .map { it.asLong }
        assertEquals(listOf(123456789L, 987654321L), ids)
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
