package takagi.ru.monica.steam.friends.chat.actions.domain

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage

enum class SteamChatReportReason(val steamValue: Int) {
    HARASSMENT(3),
    SCAM(13),
    SPAM(28),
    OTHER(2)
}

interface SteamChatMessageActionGateway {
    fun addEmoticonReaction(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage,
        emoticonName: String
    )

    fun reportMessage(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage,
        reason: SteamChatReportReason
    )
}

