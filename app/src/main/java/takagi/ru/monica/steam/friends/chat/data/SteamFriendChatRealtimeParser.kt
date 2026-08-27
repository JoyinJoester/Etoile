package takagi.ru.monica.steam.friends.chat.data

import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReactionType
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

internal object SteamFriendChatRealtimeParser {
    fun parse(
        envelope: SteamCmEnvelope,
        accountSteamId: String
    ): SteamChatRealtimeEvent? {
        if (envelope.eMsg !in SUPPORTED_SERVICE_MESSAGES) return null
        return when (envelope.header.targetJobName?.substringBefore('#')) {
            INCOMING_MESSAGE_METHOD -> parseIncoming(envelope, accountSteamId)
            ACK_MESSAGE_METHOD -> parseAcknowledged(envelope)
            REACTION_METHOD -> parseReaction(envelope)
            else -> null
        }
    }

    private fun parseIncoming(
        envelope: SteamCmEnvelope,
        accountSteamId: String
    ): SteamChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val partnerSteamId = fields[1]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val localEcho = fields[7]?.asBool == true
        val entryType = fields[2]?.asInt ?: CHAT_ENTRY_TYPE_INVALID
        return when (entryType) {
            in MESSAGE_ENTRY_TYPES -> {
                val rawBody = fields[4]?.asString
                    ?.takeIf(String::isNotBlank)
                    ?: fields[8]?.asString?.takeIf(String::isNotBlank)
                    ?: return null
                val body = if (entryType == CHAT_ENTRY_TYPE_EMOTE) {
                    rawBody.takeIf { it.startsWith("/me ", ignoreCase = true) }
                        ?: "/me $rawBody"
                } else {
                    rawBody
                }
                SteamChatRealtimeEvent.Message(
                    SteamChatMessage(
                        partnerSteamId = partnerSteamId,
                        senderSteamId = if (localEcho) accountSteamId else partnerSteamId,
                        timestamp = fields[5]?.asFixed32UnsignedLong ?: 0L,
                        ordinal = fields[6]?.asInt ?: 0,
                        body = body,
                        deliveryState = SteamChatDeliveryState.SENT
                    )
                )
            }
            CHAT_ENTRY_TYPE_TYPING -> SteamChatRealtimeEvent.Typing(partnerSteamId, localEcho)
            CHAT_ENTRY_TYPE_LEFT_CONVERSATION ->
                SteamChatRealtimeEvent.ConversationLeft(partnerSteamId, localEcho)
            else -> null
        }
    }

    private fun parseAcknowledged(envelope: SteamCmEnvelope): SteamChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val partnerSteamId = fields[1]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val timestamp = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L
        return SteamChatRealtimeEvent.Acknowledged(partnerSteamId, timestamp)
    }

    private fun parseReaction(envelope: SteamCmEnvelope): SteamChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val partnerSteamId = fields[1]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val reactorSteamId = fields[4]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val reactionType = when (fields[5]?.asInt) {
            1 -> SteamChatReactionType.EMOTICON
            2 -> SteamChatReactionType.STICKER
            else -> return null
        }
        val reactionName = fields[6]?.asString.orEmpty().trim().trim(':')
            .takeIf(String::isNotBlank) ?: return null
        return SteamChatRealtimeEvent.ReactionChanged(
            partnerSteamId = partnerSteamId,
            timestamp = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
            ordinal = fields[3]?.asInt?.coerceAtLeast(0) ?: 0,
            reactorSteamId = reactorSteamId,
            reactionType = reactionType,
            reactionName = reactionName,
            isAdd = fields[7]?.asBool == true
        )
    }

    private fun isSteamId64(value: String): Boolean = value.matches(STEAM_ID_PATTERN)

    private const val INCOMING_MESSAGE_METHOD = "FriendMessagesClient.IncomingMessage"
    private const val ACK_MESSAGE_METHOD = "FriendMessagesClient.NotifyAckMessageEcho"
    private const val REACTION_METHOD = "FriendMessagesClient.MessageReaction"
    private const val CHAT_ENTRY_TYPE_INVALID = 0
    private const val CHAT_ENTRY_TYPE_MESSAGE = 1
    private const val CHAT_ENTRY_TYPE_TYPING = 2
    private const val CHAT_ENTRY_TYPE_INVITE_GAME = 3
    private const val CHAT_ENTRY_TYPE_EMOTE = 4
    private const val CHAT_ENTRY_TYPE_LEFT_CONVERSATION = 6
    private const val CHAT_ENTRY_TYPE_HISTORICAL_CHAT = 11
    private const val CHAT_ENTRY_TYPE_LINK_BLOCKED = 14
    private val MESSAGE_ENTRY_TYPES = setOf(
        CHAT_ENTRY_TYPE_MESSAGE,
        CHAT_ENTRY_TYPE_INVITE_GAME,
        CHAT_ENTRY_TYPE_EMOTE,
        CHAT_ENTRY_TYPE_HISTORICAL_CHAT,
        CHAT_ENTRY_TYPE_LINK_BLOCKED
    )
    private val SUPPORTED_SERVICE_MESSAGES = setOf(
        SteamCmProtocol.EMSG_SERVICE_METHOD,
        SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT
    )
    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
}
