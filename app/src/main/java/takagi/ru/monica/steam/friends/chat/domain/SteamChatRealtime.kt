package takagi.ru.monica.steam.friends.chat.domain

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.steam.data.SteamAccount

sealed interface SteamChatRealtimeEvent {
    data class ConnectionChanged(val connected: Boolean) : SteamChatRealtimeEvent

    data class Message(val message: SteamChatMessage) : SteamChatRealtimeEvent

    data class Acknowledged(
        val partnerSteamId: String,
        val timestamp: Long
    ) : SteamChatRealtimeEvent

    data class ReactionChanged(
        val partnerSteamId: String,
        val timestamp: Long,
        val ordinal: Int,
        val reactorSteamId: String,
        val reactionType: SteamChatReactionType,
        val reactionName: String,
        val isAdd: Boolean
    ) : SteamChatRealtimeEvent

    data class Typing(
        val partnerSteamId: String,
        val localEcho: Boolean
    ) : SteamChatRealtimeEvent

    data class ConversationLeft(
        val partnerSteamId: String,
        val localEcho: Boolean
    ) : SteamChatRealtimeEvent
}

/** Realtime seam used by the presentation module and its deterministic fake adapter. */
fun interface SteamChatRealtimeGateway {
    fun events(account: SteamAccount): Flow<SteamChatRealtimeEvent>
}
