package takagi.ru.monica.steam.friends.groupchat.domain

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.steam.data.SteamAccount

data class SteamGroupChatMessageModification(
    val timestamp: Long,
    val ordinal: Int,
    val deleted: Boolean
)

sealed interface SteamGroupChatRealtimeEvent {
    data class ConnectionChanged(val connected: Boolean) : SteamGroupChatRealtimeEvent

    data class Message(val message: SteamGroupChatMessage) : SteamGroupChatRealtimeEvent

    data class MessageModified(
        val groupId: String,
        val chatId: String,
        val changes: List<SteamGroupChatMessageModification>
    ) : SteamGroupChatRealtimeEvent

    data class Acknowledged(
        val groupId: String,
        val chatId: String,
        val timestamp: Long
    ) : SteamGroupChatRealtimeEvent

    data class RoomChanged(val groupId: String) : SteamGroupChatRealtimeEvent

    data class HeaderChanged(
        val groupId: String,
        val name: String? = null,
        val tagline: String? = null,
        val avatarUrl: String? = null
    ) : SteamGroupChatRealtimeEvent

    data class Disconnected(val groupIds: Set<String>) : SteamGroupChatRealtimeEvent
}

/** Foreground seam for unsolicited ChatRoomClient events. */
fun interface SteamGroupChatRealtimeGateway {
    fun events(account: SteamAccount): Flow<SteamGroupChatRealtimeEvent>
}
