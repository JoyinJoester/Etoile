package takagi.ru.monica.steam.notifications.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import takagi.ru.monica.steam.notifications.domain.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamNotificationService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(
        account: SteamAccount,
        fetchedAt: Long = System.currentTimeMillis()
    ): SteamNotificationSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val payload = api.steamApiGetJson(
            path = "/ISteamNotificationService/GetSteamNotifications/v1/",
            query = linkedMapOf(
                "include_hidden" to "false",
                "include_confirmation_count" to "true",
                "include_pinned_counts" to "true",
                "include_read" to "true",
                "count_only" to "false"
            ),
            accessToken = accessToken
        )
        return SteamNotificationParser.parse(payload, fetchedAt)
    }

    fun markRead(
        account: SteamAccount,
        notificationIds: Collection<String>
    ) {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val ids = notificationIds.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .onEach { id ->
                id.toBigIntegerOrNull()?.takeIf { it.signum() > 0 }
                    ?: throw IllegalArgumentException("Invalid Steam notification ID")
            }
            .toList()
        if (ids.isEmpty()) return

        api.callProtobuf(
            iface = "ISteamNotificationService",
            method = "MarkNotificationsRead",
            request = SteamProtoWriter().apply {
                ids.forEach { id -> writeUint64(field = 3, value = id) }
            },
            accessToken = accessToken
        )
    }
}

object SteamNotificationParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, fetchedAt: Long = System.currentTimeMillis()): SteamNotificationSnapshot {
        val payload = json.parseToJsonElement(raw).jsonObject
        return parse(payload, fetchedAt)
    }

    fun parse(payload: JsonObject, fetchedAt: Long = System.currentTimeMillis()): SteamNotificationSnapshot {
        val response = payload.obj("response") ?: payload
        val notifications = response.array("notifications")
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::parseNotification)
            .filterNot(SteamNotification::hidden)
            .sortedByDescending(SteamNotification::timestamp)
        return SteamNotificationSnapshot(
            notifications = notifications,
            confirmationCount = response.int("confirmation_count"),
            pendingGiftCount = response.int("pending_gift_count"),
            pendingFriendCount = response.int("pending_friend_count"),
            unreadCount = response.int("unread_count"),
            pendingFamilyInviteCount = response.int("pending_family_invite_count"),
            fetchedAt = fetchedAt
        )
    }

    private fun parseNotification(raw: JsonObject): SteamNotification? {
        val id = raw.string("notification_id")
        if (id.isBlank()) return null
        val type = raw.int("notification_type")
        val kind = SteamNotificationKind.fromType(type)
        val bodyData = raw.string("body_data")
        val bodyFields = bodyData.toBodyFields()
        val title = bodyFields.firstText(
            "title",
            "app_name",
            "game_name",
            "item_name",
            "package_name",
            "display_name",
            "event_name",
            "clip_name",
            "name"
        ).orEmpty().ifBlank { defaultTitle(kind) }
        val summary = bodyFields.firstText(
            "gifter_name",
            "sender_name",
            "sender",
            "persona_name",
            "requestor_name",
            "actor_name",
            "body",
            "message",
            "text",
            "comment",
            "description",
            "notification_body",
            "notification_text"
        ).orEmpty().ifBlank {
            bodyData.takeUnless { bodyFields.isNotEmpty() }.orEmpty()
        }
        val relatedId = bodyFields.firstText(
            "giftid",
            "gift_id",
            "tradeofferid",
            "trade_offer_id",
            "appid",
            "app_id",
            "source_appid",
            "familyid",
            "family_id",
            "requestid",
            "request_id",
            "eventid",
            "event_id",
            "clipid",
            "clip_id"
        )?.takeIf(String::isNotBlank)
        return SteamNotification(
            id = id,
            type = type,
            kind = kind,
            title = title,
            summary = summary,
            relatedId = relatedId,
            bodyData = bodyData,
            read = raw.bool("read"),
            timestamp = raw.long("timestamp"),
            hidden = raw.bool("hidden"),
            expiry = raw.long("expiry"),
            viewed = raw.long("viewed")
        )
    }

    private fun defaultTitle(kind: SteamNotificationKind): String = when (kind) {
        SteamNotificationKind.GIFT -> "Steam gift"
        SteamNotificationKind.COMMENT -> "New comment"
        SteamNotificationKind.ITEM -> "New item"
        SteamNotificationKind.FRIEND_INVITE -> "Friend invitation"
        SteamNotificationKind.SALE -> "Steam sale"
        SteamNotificationKind.PRELOAD -> "Preload available"
        SteamNotificationKind.WISHLIST -> "Wishlist update"
        SteamNotificationKind.TRADE_OFFER -> "Trade offer"
        SteamNotificationKind.GENERAL -> "Steam notification"
        SteamNotificationKind.HELP_REQUEST -> "Steam Support"
        SteamNotificationKind.ASYNC_GAME -> "Game update"
        SteamNotificationKind.CHAT_MESSAGE -> "Chat message"
        SteamNotificationKind.MODERATOR_MESSAGE -> "Moderator message"
        SteamNotificationKind.PARENTAL_FEATURE_REQUEST -> "Parental feature request"
        SteamNotificationKind.FAMILY_INVITE -> "Steam Family invitation"
        SteamNotificationKind.FAMILY_PURCHASE_REQUEST -> "Family purchase request"
        SteamNotificationKind.PARENTAL_PLAYTIME_REQUEST -> "Playtime request"
        SteamNotificationKind.FAMILY_PURCHASE_RESPONSE -> "Family purchase response"
        SteamNotificationKind.PARENTAL_FEATURE_RESPONSE -> "Parental feature response"
        SteamNotificationKind.PARENTAL_PLAYTIME_RESPONSE -> "Playtime request update"
        SteamNotificationKind.REQUESTED_GAME_ADDED -> "Requested game added"
        SteamNotificationKind.SEND_TO_PHONE -> "Sent to phone"
        SteamNotificationKind.CLIP_DOWNLOADED -> "Clip downloaded"
        SteamNotificationKind.TWO_FACTOR_PROMPT -> "Steam sign-in request"
        SteamNotificationKind.MOBILE_CONFIRMATION -> "Mobile confirmation"
        SteamNotificationKind.PARTNER_EVENT -> "Steam event"
        SteamNotificationKind.PLAYTEST_INVITE -> "Playtest invitation"
        SteamNotificationKind.TRADE_REVERSAL -> "Trade reversed"
        SteamNotificationKind.REPORTED_CONTENT_ACTION -> "Reported content update"
        SteamNotificationKind.FAMILY -> "Steam Family"
        SteamNotificationKind.PARENTAL -> "Parental controls"
        SteamNotificationKind.GAME_INVITE -> "Game invitation"
        SteamNotificationKind.TRADE_REVERSED -> "Trade update"
        SteamNotificationKind.UNKNOWN -> "Steam notification"
    }

    private data class BodyField(val key: String, val value: String)

    private fun String.toBodyFields(): List<BodyField> {
        val element = trim()
            .takeIf { it.startsWith('{') || it.startsWith('[') }
            ?.let { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
            ?: return emptyList()
        return buildList { flattenBody(element, output = this) }
    }

    private fun flattenBody(
        element: JsonElement,
        output: MutableList<BodyField>,
        depth: Int = 0
    ) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                when (value) {
                    is JsonPrimitive -> {
                        val content = value.contentOrNull.orEmpty().trim()
                        val embedded = content.takeIf {
                            depth < MAX_EMBEDDED_JSON_DEPTH &&
                                (it.startsWith('{') || it.startsWith('['))
                        }?.let { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
                        if (embedded != null && embedded !is JsonPrimitive) {
                            flattenBody(embedded, output, depth + 1)
                        } else if (content.isNotBlank()) {
                            output += BodyField(key.normalizedKey(), content)
                        }
                    }
                    else -> flattenBody(value, output, depth)
                }
            }
            is JsonArray -> element.forEach { child -> flattenBody(child, output, depth) }
            is JsonPrimitive -> Unit
        }
    }

    private fun List<BodyField>.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            firstOrNull { field -> field.key == key.normalizedKey() }
                ?.value
                ?.takeIf(String::isNotBlank)
        }

    private fun String.normalizedKey(): String =
        lowercase().filter(Char::isLetterOrDigit)

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: return ""
        return primitive.contentOrNull.orEmpty()
    }

    private fun JsonObject.int(key: String): Int {
        val primitive = this[key] as? JsonPrimitive ?: return 0
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun JsonObject.long(key: String): Long {
        val primitive = this[key] as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }

    private fun JsonObject.bool(key: String): Boolean {
        val primitive = this[key] as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: when (primitive.contentOrNull?.lowercase()) {
            "1", "true" -> true
            else -> false
        }
    }

    private const val MAX_EMBEDDED_JSON_DEPTH = 3
}
