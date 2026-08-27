package takagi.ru.monica.steam.friends.groupchat.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamGroupChatSummary(
    val groupId: String,
    val name: String,
    val tagline: String = "",
    val ownerAccountId: Long = 0L,
    val activeMemberCount: Int = 0,
    /** Official CChatRoom_GetChatRoomGroupSummary_Response.active_voice_member_count. */
    val activeVoiceMemberCount: Int = 0,
    val defaultChatId: String,
    val rooms: List<SteamGroupChatRoom> = emptyList(),
    val rank: Int = 0,
    val avatarUrl: String = "",
    val unreadCount: Int = 0,
    val topMemberSteamIds: List<String> = emptyList()
) {
    /** Steam's default room is the entry point for a group with multiple channels. */
    val preferredChatId: String
        get() = when {
            rooms.size == 1 -> rooms.single().chatId
            defaultChatId.isNotBlank() -> defaultChatId
            else -> rooms.firstOrNull()?.chatId.orEmpty()
        }

    val isVoiceActive: Boolean
        get() = activeVoiceMemberCount > 0 || rooms.any(SteamGroupChatRoom::isVoiceActive)
}

enum class SteamGroupChatRoomType {
    TEXT,
    VOICE
}

@Serializable
data class SteamGroupChatRoom(
    val chatId: String,
    val name: String,
    val sortOrder: Int = 0,
    val lastMessageTimestamp: Long = 0L,
    val lastMessage: String = "",
    val lastSenderSteamId: String = "",
    val lastAcknowledgedTimestamp: Long = 0L,
    val unread: Boolean = false,
    /** Official CChatRoomState.voice_allowed field. */
    val voiceAllowed: Boolean = false,
    /** Account IDs reported by the official CChatRoomState.members_in_voice field. */
    val voiceMemberSteamIds: List<String> = emptyList()
) {
    val type: SteamGroupChatRoomType
        get() = if (voiceAllowed) SteamGroupChatRoomType.VOICE else SteamGroupChatRoomType.TEXT

    val isVoiceActive: Boolean
        get() = voiceMemberSteamIds.isNotEmpty()
}

@Serializable
data class SteamGroupChatMessage(
    val groupId: String,
    val chatId: String,
    val senderSteamId: String,
    val timestamp: Long,
    val ordinal: Int,
    val body: String,
    val deleted: Boolean = false,
    val serverEventType: Int = 0,
    val clientMessageId: String = "",
    val localCreatedAtMillis: Long = 0L,
    val deliveryState: SteamGroupChatDeliveryState = SteamGroupChatDeliveryState.SENT,
    val reactions: List<SteamGroupChatReaction> = emptyList()
) {
    val stableId: String get() = if (clientMessageId.isNotBlank()) {
        "client:$clientMessageId"
    } else "$groupId:$chatId:$timestamp:$ordinal:$senderSteamId"
}

@Serializable
enum class SteamGroupChatDeliveryState {
    QUEUED,
    SENDING,
    VERIFYING,
    SENT,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    /** Compatibility value for thread snapshots written by earlier test builds. */
    FAILED
}

@Serializable
enum class SteamGroupChatReactionType { EMOTICON, STICKER }

@Serializable
data class SteamGroupChatReaction(
    val type: SteamGroupChatReactionType,
    val name: String,
    val count: Int,
    val hasUserReacted: Boolean
)

@Serializable
data class SteamGroupChatGroupsSnapshot(
    val accountSteamId: String,
    val groups: List<SteamGroupChatSummary>,
    val fetchedAt: Long
)

@Serializable
data class SteamGroupChatThreadSnapshot(
    val accountSteamId: String,
    val groupId: String,
    val chatId: String,
    val messages: List<SteamGroupChatMessage>,
    val moreAvailable: Boolean,
    val fetchedAt: Long
)

data class SteamGroupChatMessagePage(
    val messages: List<SteamGroupChatMessage>,
    val moreAvailable: Boolean
)

data class SteamGroupChatCreateRequest(
    val name: String,
    val inviteeSteamIds: List<String>
)

data class SteamGroupChatHistoryBoundary(val timestamp: Long, val ordinal: Int)

/**
 * Converts the avatar values used by Steam ChatRoom into a loadable URL.
 * Steam has returned both raw 20-byte SHA values and their 40-character hex
 * representation over time; accepting both keeps cached group summaries
 * forward compatible with the current client protocol.
 */
fun steamGroupAvatarUrl(value: ByteArray): String {
    if (value.isEmpty()) return ""
    val text = value.toString(Charsets.UTF_8).trim()
    return when {
        text.looksLikeSteamAvatarUrl() -> normalizeSteamGroupAvatarUrl(text)
        text.matches(HEX_SHA_PATTERN) -> steamGroupAvatarUrlFromHex(text)
        value.size == AVATAR_SHA_BYTES -> steamGroupAvatarUrlFromHex(
            value.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        )
        else -> ""
    }
}

fun steamGroupAvatarUrl(value: String): String {
    val text = value.trim()
    return when {
        text.isEmpty() -> ""
        text.looksLikeSteamAvatarUrl() -> normalizeSteamGroupAvatarUrl(text)
        text.matches(HEX_SHA_PATTERN) -> steamGroupAvatarUrlFromHex(text)
        else -> ""
    }
}

private fun steamGroupAvatarUrlFromHex(hex: String): String {
    val normalized = hex.lowercase()
    return "https://community.akamai.steamstatic.com/images/chaticons/" +
        "${normalized.substring(0, 2)}/${normalized.substring(2, 4)}/" +
        "${normalized.substring(4, 6)}/${normalized}_256.jpg"
}

private fun String.looksLikeSteamAvatarUrl(): Boolean =
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("//") ||
        startsWith("/ugc/", ignoreCase = true) ||
        startsWith("/avatar", ignoreCase = true)

private fun normalizeSteamGroupAvatarUrl(raw: String): String = when {
    raw.startsWith("//") -> "https:${raw}"
    raw.startsWith("/") -> "https://steamcommunity.com$raw"
    raw.startsWith("http://", ignoreCase = true) ->
        "https://${raw.substringAfter("://", raw)}"
    else -> raw
}

private const val AVATAR_SHA_BYTES = 20
private val HEX_SHA_PATTERN = Regex("[0-9a-fA-F]{40}")

internal fun mergeSteamGroupMessages(
    current: List<SteamGroupChatMessage>,
    incoming: List<SteamGroupChatMessage>
): List<SteamGroupChatMessage> {
    val merged = linkedMapOf<String, SteamGroupChatMessage>()
    (current + incoming).forEach { message ->
        val serverKey = "${message.timestamp}:${message.ordinal}:${message.senderSteamId}"
        val existing = merged.values
            .filter { it.stableId == message.stableId || sameServerMessage(it, message) }
            .minByOrNull { candidate ->
                kotlin.math.abs(candidate.timestamp - message.timestamp)
            }
        if (existing != null) merged.remove(existing.stableId)
        val replacement = when {
            message.clientMessageId.isBlank() && existing?.clientMessageId?.isNotBlank() == true ->
                message.copy(
                    clientMessageId = existing.clientMessageId,
                    localCreatedAtMillis = existing.localCreatedAtMillis,
                    deliveryState = SteamGroupChatDeliveryState.SENT
                )
            message.clientMessageId.isNotBlank() && existing?.clientMessageId.isNullOrBlank() &&
                existing != null && sameServerMessage(existing, message) ->
                existing.copy(
                    clientMessageId = message.clientMessageId,
                    localCreatedAtMillis = message.localCreatedAtMillis,
                    deliveryState = message.deliveryState
                )
            else -> message
        }
        merged[replacement.stableId.ifBlank { serverKey }] = replacement
    }
    return merged.values.sortedWith(compareBy<SteamGroupChatMessage> { it.timestamp }.thenBy { it.ordinal })
}

private fun sameServerMessage(
    first: SteamGroupChatMessage,
    second: SteamGroupChatMessage
): Boolean {
    if (first.groupId != second.groupId || first.chatId != second.chatId ||
        first.senderSteamId != second.senderSteamId
    ) return false
    if (first.ordinal != Int.MAX_VALUE && second.ordinal != Int.MAX_VALUE) {
        return first.timestamp == second.timestamp && first.ordinal == second.ordinal
    }

    val local = if (first.ordinal == Int.MAX_VALUE) first else second
    val server = if (first.ordinal == Int.MAX_VALUE) second else first
    if (server.ordinal == Int.MAX_VALUE) return false
    // A server row already bound to another local send belongs to that send, so
    // repeating the same text within the echo window must stay two messages.
    if (server.clientMessageId.isNotBlank() &&
        server.clientMessageId != local.clientMessageId
    ) return false
    if (local.body.trim() != server.body.trim()) return false
    val localTimestamp = local.localCreatedAtMillis
        .takeIf { it > 0L }
        ?.div(1_000L)
        ?: local.timestamp
    if (localTimestamp <= 0L || server.timestamp <= 0L) return false
    return kotlin.math.abs(localTimestamp - server.timestamp) <= OPTIMISTIC_ECHO_WINDOW_SECONDS
}

private const val OPTIMISTIC_ECHO_WINDOW_SECONDS = 90L
