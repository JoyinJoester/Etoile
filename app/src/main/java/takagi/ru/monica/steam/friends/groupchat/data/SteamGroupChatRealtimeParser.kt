package takagi.ru.monica.steam.friends.groupchat.data

import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessageModification
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupAvatarUrl
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupEventText
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

/** Parses unsolicited ChatRoomClient service envelopes from the shared CM socket. */
internal object SteamGroupChatRealtimeParser {
    fun parse(
        envelope: SteamCmEnvelope
    ): SteamGroupChatRealtimeEvent? {
        if (envelope.eMsg !in SUPPORTED_SERVICE_MESSAGES) return null
        return when (envelope.header.targetJobName?.substringBefore('#')) {
            INCOMING_MESSAGE_METHOD -> parseIncoming(envelope)
            MESSAGE_MODIFIED_METHOD -> parseModified(envelope)
            ACK_METHOD -> parseAck(envelope)
            ROOMS_CHANGED_METHOD -> parseRoomsChanged(envelope)
            DISCONNECT_METHOD -> parseDisconnected(envelope)
            HEADER_STATE_CHANGED_METHOD -> parseHeaderChanged(envelope)
            USER_STATE_CHANGED_METHOD,
            MEMBER_STATE_CHANGED_METHOD,
            REACTION_METHOD -> parseRoomChanged(envelope)
            else -> null
        }
    }

    private fun parseIncoming(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parseAll() }.getOrNull()
            ?: return null
        val groupId = fields.firstValue(1)?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        val chatId = fields.firstValue(2)?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        val sender = fields.firstValue(3)?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val serverMessage = fields.firstValue(8)?.bytes?.let {
            runCatching { SteamProtoReader(it).parse() }.getOrNull()
        }
        val eventType = serverMessage?.get(1)?.asInt ?: 0
        val eventText = serverMessage?.get(2)?.asString.orEmpty()
        val body = fields.firstValue(4)?.asString.orEmpty()
            .ifBlank { fields.firstValue(9)?.asString.orEmpty() }
            .ifBlank { if (eventType > 0) steamGroupEventText(eventType, eventText) else "" }
        if (body.isBlank()) return null
        return SteamGroupChatRealtimeEvent.Message(
            SteamGroupChatMessage(
                groupId = groupId,
                chatId = chatId,
                senderSteamId = sender,
                timestamp = fields.firstValue(5)?.asLong?.coerceAtLeast(0L) ?: 0L,
                ordinal = fields.firstValue(7)?.asLong?.coerceAtLeast(0L)?.toInt() ?: 0,
                body = body,
                serverEventType = eventType,
                deliveryState = SteamGroupChatDeliveryState.SENT
            )
        )
    }

    private fun parseModified(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val outer = runCatching { SteamProtoReader(envelope.body).parseAll() }.getOrNull()
            ?: return null
        val groupId = outer.firstValue(1)?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        val chatId = outer.firstValue(2)?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        val changes = outer.filter { it.number == 3 && it.bytes != null }
            .mapNotNull { field ->
                val message = runCatching {
                    SteamProtoReader(field.bytes!!).parse()
                }.getOrNull() ?: return@mapNotNull null
                val timestamp = message[1]?.asLong?.coerceAtLeast(0L)
                    ?.takeIf { it > 0L } ?: return@mapNotNull null
                SteamGroupChatMessageModification(
                    timestamp = timestamp,
                    ordinal = message[2]?.asLong?.coerceAtLeast(0L)?.toInt() ?: 0,
                    deleted = message[3]?.asBool == true
                )
            }
            .distinctBy { it.timestamp to it.ordinal }
        if (changes.isEmpty()) return null
        return SteamGroupChatRealtimeEvent.MessageModified(
            groupId = groupId,
            chatId = chatId,
            changes = changes
        )
    }

    private fun parseAck(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val groupId = fields[1]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank)
            ?: return null
        val chatId = fields[2]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank)
            ?: return null
        return SteamGroupChatRealtimeEvent.Acknowledged(
            groupId = groupId,
            chatId = chatId,
            timestamp = fields[3]?.asLong?.coerceAtLeast(0L) ?: 0L
        )
    }

    private fun parseRoomsChanged(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val groupId = fields[1]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank)
            ?: return null
        return SteamGroupChatRealtimeEvent.RoomChanged(groupId)
    }

    private fun parseRoomChanged(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parseAll() }.getOrNull()
            ?: return null
        val groupField = fields.firstValue(1)
        val directGroupId = groupField
            ?.takeIf { it.wireType == WIRE_TYPE_VARINT }
            ?.asUnsignedVarintString()
            ?.takeIf(String::isNotBlank)
        if (directGroupId != null) return SteamGroupChatRealtimeEvent.RoomChanged(directGroupId)
        val header = groupField
            ?.takeIf { it.wireType == WIRE_TYPE_LENGTH_DELIMITED }
            ?.bytes
            ?.let {
            runCatching { SteamProtoReader(it).parse() }.getOrNull()
        }
        val nestedGroupId = header?.get(1)?.asUnsignedVarintString()
            ?.takeIf(String::isNotBlank)
        return nestedGroupId?.let(SteamGroupChatRealtimeEvent::RoomChanged)
    }

    private fun parseHeaderChanged(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent? {
        val outer = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val header = outer[1]?.bytes?.let { payload ->
            runCatching { SteamProtoReader(payload).parse() }.getOrNull()
        } ?: return null
        val groupId = header[1]?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        val avatarField = header[25] ?: header[16]
        return SteamGroupChatRealtimeEvent.HeaderChanged(
            groupId = groupId,
            name = header[2]?.takeIf { it.wireType == WIRE_TYPE_LENGTH_DELIMITED }?.asString,
            tagline = header[15]?.takeIf { it.wireType == WIRE_TYPE_LENGTH_DELIMITED }?.asString,
            avatarUrl = avatarField
                ?.takeIf { it.wireType == WIRE_TYPE_LENGTH_DELIMITED }
                ?.bytes
                ?.let(::steamGroupAvatarUrl)
        )
    }

    private fun parseDisconnected(envelope: SteamCmEnvelope): SteamGroupChatRealtimeEvent {
        val fields = runCatching { SteamProtoReader(envelope.body).parseAll() }.getOrDefault(emptyList())
        return SteamGroupChatRealtimeEvent.Disconnected(
            fields.filter { it.number == 1 }
                .mapNotNull { it.asUnsignedVarintString().takeIf(String::isNotBlank) }
                .toSet()
        )
    }

    private fun List<SteamProtoField>.firstValue(
        number: Int
    ): SteamProtoField? = firstOrNull { it.number == number }

    private fun SteamProtoField.asUnsignedVarintString(): String =
        java.lang.Long.toUnsignedString(asLong)

    private fun isSteamId64(value: String): Boolean = value.matches(STEAM_ID_PATTERN)

    private const val INCOMING_MESSAGE_METHOD = "ChatRoomClient.NotifyIncomingChatMessage"
    private const val MESSAGE_MODIFIED_METHOD = "ChatRoomClient.NotifyChatMessageModified"
    private const val ACK_METHOD = "ChatRoomClient.NotifyAckChatMessageEcho"
    private const val ROOMS_CHANGED_METHOD = "ChatRoomClient.NotifyChatRoomGroupRoomsChange"
    private const val DISCONNECT_METHOD = "ChatRoomClient.NotifyChatRoomDisconnect"
    private const val USER_STATE_CHANGED_METHOD = "ChatRoomClient.NotifyChatGroupUserStateChanged"
    private const val HEADER_STATE_CHANGED_METHOD = "ChatRoomClient.NotifyChatRoomHeaderStateChange"
    private const val MEMBER_STATE_CHANGED_METHOD = "ChatRoomClient.NotifyMemberStateChange"
    private const val REACTION_METHOD = "ChatRoomClient.NotifyMessageReaction"
    private const val WIRE_TYPE_VARINT = 0
    private const val WIRE_TYPE_LENGTH_DELIMITED = 2
    private val SUPPORTED_SERVICE_MESSAGES = setOf(
        SteamCmProtocol.EMSG_SERVICE_METHOD,
        SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT
    )
    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
}
