package takagi.ru.monica.steam.friends.chat.info.domain

import kotlinx.serialization.Serializable

enum class SteamChatConversationType { DIRECT, GROUP }

data class SteamChatConversationId(
    val accountSteamId: String,
    val type: SteamChatConversationType,
    val peerOrGroupId: String,
    val chatId: String = ""
) {
    val storageIdentity: String
        get() = listOf(accountSteamId, type.name, peerOrGroupId, chatId).joinToString("|")
}

@Serializable
data class SteamChatConversationPreferences(
    val muted: Boolean = false,
    val pinned: Boolean = false
)

data class SteamChatHistoryItem(
    val id: String,
    val senderName: String,
    val body: String,
    val timestamp: Long
)
