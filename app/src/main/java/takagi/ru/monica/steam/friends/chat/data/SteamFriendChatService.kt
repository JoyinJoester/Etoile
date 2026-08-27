package takagi.ru.monica.steam.friends.chat.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamFriendChatService(
    private val api: SteamApiClient = SteamApiClient(),
    private val cm: SteamCmGateway = SteamCmClient()
) : SteamChatGateway {
    override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot {
        val accessToken = account.requireChatAccessToken()
        val response = api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "GetActiveMessageSessions",
            request = SteamProtoWriter().apply {
                writeVarint(1, 0L)
                writeBool(2, true)
            },
            accessToken = accessToken,
            useGet = true
        )
        return SteamChatSessionsSnapshot(
            accountSteamId = account.steamId,
            sessions = SteamFriendChatParser.parseSessions(response),
            fetchedAt = System.currentTimeMillis()
        )
    }

    override fun fetchMessages(
        account: SteamAccount,
        partnerSteamId: String,
        before: SteamChatHistoryBoundary?
    ): SteamChatPage {
        val accessToken = account.requireChatAccessToken()
        val accountSteamId = account.requireChatSteamId()
        val partner = partnerSteamId.requireSteamId64()
        val response = api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "GetRecentMessages",
            request = SteamProtoWriter().apply {
                writeFixed64(1, accountSteamId)
                writeFixed64(2, partner)
                writeVarint(3, PAGE_SIZE.toLong())
                writeBool(4, before == null)
                before?.let { boundary ->
                    writeFixed32(5, boundary.timestamp)
                    writeVarint(7, boundary.ordinal.toLong())
                }
                // Request the original BBCode so Steam invites, stickers,
                // media links and other structured chat entries are not
                // flattened into an unparseable plain-text surrogate.
                writeBool(6, true)
            },
            accessToken = accessToken,
            useGet = true
        )
        return SteamFriendChatParser.parseMessages(response, partnerSteamId)
    }

    override fun sendMessage(
        account: SteamAccount,
        partnerSteamId: String,
        body: String,
        clientMessageId: String
    ): SteamChatMessage {
        account.requireChatAccessToken()
        val partner = partnerSteamId.requireSteamId64()
        val normalizedBody = body.trim()
        require(normalizedBody.isNotBlank()) { "Steam chat message is empty" }
        val steamBody = normalizedBody.replace("[", "\\[")
        val response = cm.callService(
            account = account,
            method = "FriendMessages.SendMessage#1",
            request = SteamProtoWriter().apply {
                writeFixed64(1, partner)
                writeVarint(2, CHAT_ENTRY_TYPE_MESSAGE)
                // Steam's current clients send chat as BBCode-aware text and
                // escape literal opening brackets before it reaches the CM.
                writeString(3, steamBody)
                writeBool(4, true)
            }.toByteArray()
        )
        return SteamFriendChatParser.parseSentMessage(
            response = response,
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            requestedBody = normalizedBody
        ).copy(clientMessageId = clientMessageId)
    }

    override fun acknowledge(
        account: SteamAccount,
        partnerSteamId: String,
        timestamp: Long
    ) {
        if (timestamp <= 0L) return
        val accessToken = account.requireChatAccessToken()
        val partner = partnerSteamId.requireSteamId64()
        api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "AckMessage",
            request = SteamProtoWriter().apply {
                writeFixed64(1, partner)
                writeVarint(2, timestamp)
            },
            accessToken = accessToken
        )
    }

    private fun SteamAccount.requireChatAccessToken(): String {
        require(hasRealSteamId) { "Real Steam ID required for chat" }
        return accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required for chat")
    }

    private fun SteamAccount.requireChatSteamId(): Long =
        steamId.requireSteamId64()

    private fun String.requireSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "Valid Steam ID required for chat" }
        return toLong()
    }

    private companion object {
        const val FRIEND_MESSAGES_INTERFACE = "IFriendMessagesService"
        const val PAGE_SIZE = 50
        const val CHAT_ENTRY_TYPE_MESSAGE = 1L
    }
}
