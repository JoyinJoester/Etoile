package takagi.ru.monica.steam.friends.chat.richmedia.domain

enum class SteamChatOfficialMessageKind {
    TRADE_OFFER,
    BROADCAST_INVITE,
    BROADCAST_VIEW_REQUEST,
    PLAYTEST_INVITE,
    REMOTE_PLAY_INVITE,
    GIFT,
    INVENTORY_ITEM,
    FRIEND_REQUEST,
    GROUP_INVITE,
    EVENT,
    COMMENT,
    MARKET,
    ROOM_EFFECT,
    UNKNOWN
}

data class SteamChatOfficialMessage(
    val kind: SteamChatOfficialMessageKind,
    val title: String,
    val description: String = "",
    val url: String? = null,
    val appId: Int? = null,
    val senderSteamId: String? = null,
    val tradeOfferId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val rawBody: String
)

internal object SteamChatOfficialMessageParser {
    private val tradeOfferIdPattern = Regex("/tradeoffer/(\\d+)", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("(?:https?|steam)://[^\\s\\]]+", RegexOption.IGNORE_CASE)

    fun parse(tag: String, rawAttributes: String, innerText: String, rawBody: String): SteamChatOfficialMessage {
        val attributes = parseAttributes(rawAttributes)
        val kind = when (tag.lowercase()) {
            "tradeoffer", "trade_offer", "incomingtradeoffer" -> SteamChatOfficialMessageKind.TRADE_OFFER
            "broadcastinvite" -> SteamChatOfficialMessageKind.BROADCAST_INVITE
            "broadcastviewrequest" -> SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST
            "playtestinvite" -> SteamChatOfficialMessageKind.PLAYTEST_INVITE
            "remoteplayinvite" -> SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE
            "gift", "giftreceived", "giftnotification" -> SteamChatOfficialMessageKind.GIFT
            "inventoryitem", "itemnotification", "newitem" -> SteamChatOfficialMessageKind.INVENTORY_ITEM
            "friendinvite", "friendrequest" -> SteamChatOfficialMessageKind.FRIEND_REQUEST
            "claninvite", "groupinvite" -> SteamChatOfficialMessageKind.GROUP_INVITE
            "eventnotification" -> SteamChatOfficialMessageKind.EVENT
            "commentnotification" -> SteamChatOfficialMessageKind.COMMENT
            "marketnotification" -> SteamChatOfficialMessageKind.MARKET
            "roomeffect" -> SteamChatOfficialMessageKind.ROOM_EFFECT
            else -> SteamChatOfficialMessageKind.UNKNOWN
        }
        val url = urlPattern.find(innerText)?.value ?: attributes["url"] ?: attributes["link"]
        return SteamChatOfficialMessage(
            kind = kind,
            title = titleFor(kind),
            description = innerText.trim().ifBlank { attributes["name"] ?: attributes["title"].orEmpty() },
            url = url,
            appId = attributes["appid"]?.toIntOrNull(),
            senderSteamId = attributes["steamid"] ?: attributes["steamid64"],
            tradeOfferId = attributes["tradeofferid"] ?: attributes["trade_offer_id"]
                ?: url?.let { tradeOfferIdPattern.find(it)?.groupValues?.getOrNull(1) },
            attributes = attributes,
            rawBody = rawBody
        )
    }

    private fun parseAttributes(raw: String): Map<String, String> =
        Regex("""([A-Za-z0-9_]+)=(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            .findAll(raw)
            .associate { match ->
                val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
                match.groupValues[1].lowercase() to value
            }

    private fun titleFor(kind: SteamChatOfficialMessageKind): String = when (kind) {
        SteamChatOfficialMessageKind.TRADE_OFFER -> "Steam trade offer"
        SteamChatOfficialMessageKind.BROADCAST_INVITE -> "Steam broadcast invitation"
        SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST -> "Steam broadcast request"
        SteamChatOfficialMessageKind.PLAYTEST_INVITE -> "Steam Playtest invitation"
        SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE -> "Remote Play Together invitation"
        SteamChatOfficialMessageKind.GIFT -> "Steam gift"
        SteamChatOfficialMessageKind.INVENTORY_ITEM -> "New Steam inventory item"
        SteamChatOfficialMessageKind.FRIEND_REQUEST -> "Steam friend request"
        SteamChatOfficialMessageKind.GROUP_INVITE -> "Steam group invitation"
        SteamChatOfficialMessageKind.EVENT -> "Steam event notification"
        SteamChatOfficialMessageKind.COMMENT -> "Steam comment notification"
        SteamChatOfficialMessageKind.MARKET -> "Steam Market notification"
        SteamChatOfficialMessageKind.ROOM_EFFECT -> "Steam room effect"
        SteamChatOfficialMessageKind.UNKNOWN -> "Steam notification"
    }
}
