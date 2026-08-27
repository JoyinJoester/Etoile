package takagi.ru.monica.steam.friends.groupchat.presentation

import kotlin.math.abs
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage

/** Completes a durable group write when Steam sends the local message echo. */
internal suspend fun completeMatchingRealtimeGroupOutboxEcho(
    outbox: SteamGroupChatOutbox,
    account: SteamAccount,
    accountKey: String,
    message: SteamGroupChatMessage
): String? {
    if (message.senderSteamId != account.steamId) return null
    val match = outbox.recover(account, message.groupId, message.chatId, accountKey)
        .filter { pending ->
            pending.message.body.trim() == message.body.trim() &&
                abs(
                    message.timestamp -
                        (pending.message.localCreatedAtMillis / 1_000L)
                ) <= GROUP_ECHO_MATCH_WINDOW_SECONDS
        }
        .minByOrNull { pending ->
            abs(
                message.timestamp -
                    (pending.message.localCreatedAtMillis / 1_000L)
            )
        } ?: return null
    outbox.complete(match.message.clientMessageId)
    return match.message.clientMessageId
}

private const val GROUP_ECHO_MATCH_WINDOW_SECONDS = 45L
