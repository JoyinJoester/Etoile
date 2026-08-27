package takagi.ru.monica.steam.friends.chat.presentation

import kotlin.math.abs
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage

/** Completes the durable write when Steam's unsolicited local echo arrives. */
internal suspend fun completeMatchingRealtimeOutboxEcho(
    outbox: SteamChatOutbox,
    account: SteamAccount,
    accountKey: String,
    message: SteamChatMessage
): String? {
    val match = outbox.recover(account, message.partnerSteamId, accountKey)
        .firstOrNull { pending ->
            pending.message.contentSignature == message.contentSignature &&
                abs(
                    message.timestamp -
                        (pending.message.localCreatedAtMillis / 1_000L)
                ) <= ECHO_MATCH_WINDOW_SECONDS
        } ?: return null
    outbox.complete(match.message.clientMessageId)
    return match.message.clientMessageId
}

private const val ECHO_MATCH_WINDOW_SECONDS = 45L
