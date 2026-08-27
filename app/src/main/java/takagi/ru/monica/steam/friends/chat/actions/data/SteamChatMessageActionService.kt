package takagi.ru.monica.steam.friends.chat.actions.data

import java.util.Locale
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatMessageActionGateway
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamChatMessageActionService(
    private val cm: SteamCmGateway = SteamCmClient()
) : SteamChatMessageActionGateway {
    override fun addEmoticonReaction(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage,
        emoticonName: String
    ) {
        val normalized = emoticonName.trim().trim(':')
        require(normalized.isNotBlank()) { "Steam emoticon is required" }
        requireServerMessage(message)
        cm.callService(
            account = account,
            method = "FriendMessages.UpdateMessageReaction#1",
            request = SteamProtoWriter().apply {
                writeFixed64(1, partnerSteamId.requireSteamId64())
                writeVarint(2, message.timestamp)
                writeVarint(3, message.ordinal.toLong())
                writeVarint(4, REACTION_TYPE_EMOTICON)
                writeString(5, ":$normalized:")
                writeBool(6, true)
            }.toByteArray()
        )
    }

    override fun reportMessage(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage,
        reason: SteamChatReportReason
    ) {
        requireServerMessage(message)
        require(!message.isOutgoing(account.steamId)) { "Own Steam messages cannot be reported" }
        cm.callService(
            account = account,
            method = "FriendMessages.ReportMessage#1",
            request = SteamProtoWriter().apply {
                writeFixed64(1, message.senderSteamId.requireSteamId64())
                writeFixed64(2, account.steamId.requireSteamId64())
                writeVarint(3, message.timestamp)
                writeVarint(4, message.ordinal.toLong())
                writeVarint(5, reason.steamValue.toLong())
                writeString(6, message.body)
                writeString(7, Locale.getDefault().language.ifBlank { "en" })
            }.toByteArray()
        )
    }

    private fun requireServerMessage(message: SteamChatMessage) {
        require(message.timestamp > 0L && message.ordinal != Int.MAX_VALUE) {
            "Steam message has not been confirmed"
        }
    }

    private fun String.requireSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "Valid Steam ID required" }
        return toLong()
    }

    private companion object {
        const val REACTION_TYPE_EMOTICON = 1L
    }
}
