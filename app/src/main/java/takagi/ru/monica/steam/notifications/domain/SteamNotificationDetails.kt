package takagi.ru.monica.steam.notifications.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SteamNotificationDetailField(
    val key: String,
    val value: String
)

data class SteamNotificationDetails(
    val message: String? = null,
    val fields: List<SteamNotificationDetailField> = emptyList(),
    val appIds: List<Int> = emptyList(),
    val actorSteamId: String? = null,
    val inventoryReference: SteamNotificationInventoryReference? = null
)

data class SteamNotificationInventoryReference(
    val appId: Int,
    val contextId: String,
    val assetId: String
)

object SteamNotificationDetailParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        bodyData: String,
        title: String,
        summary: String,
        kind: SteamNotificationKind = SteamNotificationKind.UNKNOWN
    ): SteamNotificationDetails {
        val rawBody = bodyData.trim()
        if (rawBody.isBlank()) return SteamNotificationDetails()

        val element = runCatching { json.parseToJsonElement(rawBody) }.getOrNull()
            ?: return SteamNotificationDetails(
                message = rawBody.takeIf {
                    !it.looksLikeStructuredData() && it.isDistinctText(title, summary)
                }
            )
        if (element is JsonPrimitive) {
            val value = element.contentOrNull.orEmpty().trim()
            return SteamNotificationDetails(
                message = value.takeIf { it.isDistinctText(title, summary) }
            )
        }

        val flattened = buildList { flatten(element, path = "", output = this) }
        val message = flattened
            .firstOrNull { field -> field.key.normalizedLeafKey() in MESSAGE_KEYS }
            ?.value
            ?.takeIf { it.isDistinctText(title, summary) }
        val actorSteamId = if (kind == SteamNotificationKind.FRIEND_INVITE) {
            flattened.firstOrNull { field -> field.key.normalizedLeafKey() in ACTOR_ID_KEYS }
                ?.value
                ?.toSteamId64()
        } else {
            null
        }
        val inventoryReference = if (kind == SteamNotificationKind.ITEM) {
            val appId = flattened.firstValueForKeys(ITEM_APP_ID_KEYS)
                ?.toIntOrNull() ?: 0
            val contextId = flattened.firstValueForKeys(ITEM_CONTEXT_ID_KEYS).orEmpty()
            val assetId = flattened.firstValueForKeys(ITEM_ASSET_ID_KEYS).orEmpty()
            SteamNotificationInventoryReference(appId, contextId, assetId)
                .takeIf { it.appId > 0 && it.contextId.isNotBlank() && it.assetId.isNotBlank() }
        } else {
            null
        }
        val appIds = flattened
            .asSequence()
            .filter { field -> field.key.normalizedLeafKey() in APP_ID_KEYS }
            .filter { field ->
                kind != SteamNotificationKind.ITEM ||
                    field.key.normalizedLeafKey() in ITEM_SOURCE_APP_ID_KEYS
            }
            .flatMap { field -> APP_ID_PATTERN.findAll(field.value).map { it.value.toIntOrNull() } }
            .filterNotNull()
            .filter { it > 0 }
            .distinct()
            .take(MAX_APP_IDS)
            .toList()
        val fields = flattened
            .asSequence()
            .filter { field ->
                val leafKey = field.key.normalizedLeafKey()
                leafKey !in TITLE_KEYS &&
                    leafKey !in MESSAGE_KEYS &&
                    (leafKey !in TECHNICAL_KEYS ||
                        kind == SteamNotificationKind.ITEM && leafKey in ITEM_COUNT_KEYS) &&
                    !(kind == SteamNotificationKind.FRIEND_INVITE && leafKey in ACTOR_ID_KEYS) &&
                    !(kind == SteamNotificationKind.ITEM && leafKey in ITEM_REFERENCE_KEYS)
            }
            .filter { field -> field.value.isDistinctText(title, summary, message.orEmpty()) }
            .map { field ->
                if (kind == SteamNotificationKind.FRIEND_INVITE && field.key.normalizedLeafKey() == "state") {
                    field.copy(
                        key = "friend_invite_state",
                        value = field.value.toFriendInviteState()
                    )
                } else {
                    field
                }
            }
            .distinctBy { field -> field.key.lowercase() to field.value }
            .take(MAX_DETAIL_FIELDS)
            .toList()

        return SteamNotificationDetails(
            message = message,
            fields = fields,
            appIds = appIds,
            actorSteamId = actorSteamId,
            inventoryReference = inventoryReference
        )
    }

    private fun flatten(
        element: JsonElement,
        path: String,
        output: MutableList<SteamNotificationDetailField>,
        depth: Int = 0
    ) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                flatten(value, path.childPath(key), output, depth)
            }

            is JsonArray -> {
                val primitiveValues = element.mapNotNull { child ->
                    (child as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
                }
                if (primitiveValues.size == element.size && primitiveValues.isNotEmpty()) {
                    output += SteamNotificationDetailField(path, primitiveValues.joinToString())
                } else {
                    element.forEachIndexed { index, child ->
                        flatten(child, "$path[$index]", output, depth)
                    }
                }
            }

            is JsonPrimitive -> element.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { value ->
                    val embedded = value.takeIf {
                        depth < MAX_EMBEDDED_JSON_DEPTH && it.looksLikeStructuredData()
                    }?.let { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
                    if (embedded != null && embedded !is JsonPrimitive) {
                        flatten(embedded, path, output, depth + 1)
                    } else {
                        output += SteamNotificationDetailField(path, value)
                    }
                }
        }
    }

    private fun List<SteamNotificationDetailField>.firstValueForKeys(
        keys: List<String>
    ): String? = keys.firstNotNullOfOrNull { key ->
        firstOrNull { field -> field.key.normalizedLeafKey() == key }?.value
    }

    private fun String.childPath(child: String): String =
        if (isBlank()) child else "$this.$child"

    private fun String.normalizedLeafKey(): String =
        substringAfterLast('.')
            .substringBefore('[')
            .lowercase()
            .filter(Char::isLetterOrDigit)

    private fun String.isDistinctText(vararg existing: String): Boolean {
        val candidate = trim()
        return candidate.isNotBlank() && existing.none { value ->
            value.isNotBlank() && candidate.equals(value.trim(), ignoreCase = true)
        }
    }

    private fun String.looksLikeStructuredData(): Boolean =
        startsWith('{') || startsWith('[')

    private fun String.toSteamId64(): String? {
        val value = trim().toLongOrNull() ?: return null
        return when {
            value >= STEAM_ID64_BASE -> value.toString()
            value > 0L -> (STEAM_ID64_BASE + value).toString()
            else -> null
        }
    }

    private fun String.toFriendInviteState(): String = when (toIntOrNull()) {
        1, 2 -> "pending"
        3 -> "accepted"
        4 -> "ignored"
        else -> this
    }

    private val TITLE_KEYS = setOf(
        "title",
        "appname",
        "gamename",
        "itemname",
        "packagename",
        "displayname",
        "eventname",
        "clipname",
        "name"
    )
    private val MESSAGE_KEYS = setOf(
        "body",
        "message",
        "text",
        "comment",
        "description",
        "detail",
        "notificationbody",
        "notificationtext"
    )
    private val APP_ID_KEYS = setOf(
        "appid",
        "appids",
        "sourceappid",
        "sourceappids",
        "requestedappid"
    )
    private val ACTOR_ID_KEYS = setOf("requestorid", "steamid")
    private val ITEM_APP_ID_KEYS = listOf("appid", "sourceappid", "inventoryappid")
    private val ITEM_SOURCE_APP_ID_KEYS = setOf("sourceappid")
    private val ITEM_CONTEXT_ID_KEYS = listOf("contextid", "inventorycontextid")
    private val ITEM_ASSET_ID_KEYS = listOf("assetid", "itemassetid")
    private val ITEM_COUNT_KEYS = setOf("count", "quantity", "itemcount", "newitemcount")
    private val ITEM_REFERENCE_KEYS = (
        ITEM_APP_ID_KEYS + ITEM_CONTEXT_ID_KEYS + ITEM_ASSET_ID_KEYS
    ).toSet()
    private val TECHNICAL_KEYS = APP_ID_KEYS + setOf(
        "count",
        "quantity",
        "itemcount",
        "packageid",
        "bundleid"
    )
    private val APP_ID_PATTERN = Regex("\\d+")
    private const val MAX_APP_IDS = 12
    private const val MAX_DETAIL_FIELDS = 24
    private const val MAX_EMBEDDED_JSON_DEPTH = 3
    private const val STEAM_ID64_BASE = 76561197960265728L
}
