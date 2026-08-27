package takagi.ru.monica.steam.friends.chat.position.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamChatReadingPosition(
    val messageId: String,
    val scrollOffset: Int = 0
)

object SteamChatReadingConversationKey {
    fun direct(accountSteamId: String, partnerSteamId: String): String =
        "direct|$accountSteamId|$partnerSteamId"

    fun group(accountSteamId: String, groupId: String, chatId: String): String =
        "group|$accountSteamId|$groupId|$chatId"
}

internal fun resolveSteamChatReadingIndex(
    messageIds: List<String>,
    requestedMessageId: String?,
    savedMessageId: String?
): Int {
    if (messageIds.isEmpty()) return -1
    requestedMessageId?.let(messageIds::indexOf)?.takeIf { it >= 0 }?.let { return it }
    savedMessageId?.let(messageIds::indexOf)?.takeIf { it >= 0 }?.let { return it }
    return messageIds.lastIndex
}

internal fun steamChatMessagesBelow(
    messageIds: List<String>,
    lastVisibleMessageId: String?
): Int {
    val index = lastVisibleMessageId?.let(messageIds::indexOf) ?: return 0
    if (index < 0) return 0
    return (messageIds.lastIndex - index).coerceAtLeast(0)
}
