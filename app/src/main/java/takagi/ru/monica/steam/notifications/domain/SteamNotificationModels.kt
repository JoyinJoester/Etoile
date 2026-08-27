package takagi.ru.monica.steam.notifications.domain

import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.gifts.domain.SteamPendingGift

@Serializable
enum class SteamNotificationKind {
    GIFT,
    COMMENT,
    ITEM,
    FRIEND_INVITE,
    SALE,
    PRELOAD,
    WISHLIST,
    TRADE_OFFER,
    GENERAL,
    HELP_REQUEST,
    ASYNC_GAME,
    CHAT_MESSAGE,
    MODERATOR_MESSAGE,
    PARENTAL_FEATURE_REQUEST,
    FAMILY_INVITE,
    FAMILY_PURCHASE_REQUEST,
    PARENTAL_PLAYTIME_REQUEST,
    FAMILY_PURCHASE_RESPONSE,
    PARENTAL_FEATURE_RESPONSE,
    PARENTAL_PLAYTIME_RESPONSE,
    REQUESTED_GAME_ADDED,
    SEND_TO_PHONE,
    CLIP_DOWNLOADED,
    TWO_FACTOR_PROMPT,
    MOBILE_CONFIRMATION,
    PARTNER_EVENT,
    PLAYTEST_INVITE,
    TRADE_REVERSAL,
    REPORTED_CONTENT_ACTION,
    // Retained so previously cached snapshots remain decodable after the
    // notification kinds above were split into Steam's official type set.
    FAMILY,
    PARENTAL,
    GAME_INVITE,
    TRADE_REVERSED,
    UNKNOWN;

    companion object {
        fun fromType(type: Int): SteamNotificationKind = when (type) {
            2 -> GIFT
            3 -> COMMENT
            4 -> ITEM
            5 -> FRIEND_INVITE
            6 -> SALE
            7 -> PRELOAD
            8 -> WISHLIST
            9 -> TRADE_OFFER
            10 -> GENERAL
            11 -> HELP_REQUEST
            12 -> ASYNC_GAME
            13 -> CHAT_MESSAGE
            14 -> MODERATOR_MESSAGE
            15 -> PARENTAL_FEATURE_REQUEST
            16 -> FAMILY_INVITE
            17 -> FAMILY_PURCHASE_REQUEST
            18 -> PARENTAL_PLAYTIME_REQUEST
            19 -> FAMILY_PURCHASE_RESPONSE
            20 -> PARENTAL_FEATURE_RESPONSE
            21 -> PARENTAL_PLAYTIME_RESPONSE
            22 -> REQUESTED_GAME_ADDED
            23 -> SEND_TO_PHONE
            24 -> CLIP_DOWNLOADED
            25 -> TWO_FACTOR_PROMPT
            26 -> MOBILE_CONFIRMATION
            27 -> PARTNER_EVENT
            28 -> PLAYTEST_INVITE
            29 -> TRADE_REVERSAL
            30 -> REPORTED_CONTENT_ACTION
            else -> UNKNOWN
        }
    }
}

@Serializable
data class SteamNotification(
    val id: String,
    val type: Int,
    val kind: SteamNotificationKind,
    val title: String,
    val summary: String,
    val relatedId: String? = null,
    val bodyData: String = "",
    val read: Boolean = false,
    val timestamp: Long = 0L,
    val hidden: Boolean = false,
    val expiry: Long = 0L,
    val viewed: Long = 0L,
    val appContent: List<SteamNotificationAppContent> = emptyList(),
    val actorContent: SteamNotificationActorContent? = null,
    val itemContent: SteamNotificationItemContent? = null
)

@Serializable
data class SteamNotificationActorContent(
    val steamId: String,
    val displayName: String,
    val avatarUrl: String = "",
    val profileUrl: String = ""
)

@Serializable
data class SteamNotificationItemContent(
    val appId: Int,
    val contextId: String,
    val assetId: String,
    val name: String,
    val type: String = "",
    val iconUrl: String = "",
    val marketable: Boolean = false,
    val tradable: Boolean = false
)

@Serializable
data class SteamNotificationAppContent(
    val appId: Int,
    val name: String,
    val description: String = "",
    val imageUrl: String = "",
    val formattedInitialPrice: String = "",
    val formattedFinalPrice: String = "",
    val discountPercent: Int = 0,
    val availableInAccountRegion: Boolean? = null
)

@Serializable
data class SteamNotificationSnapshot(
    val notifications: List<SteamNotification> = emptyList(),
    val confirmationCount: Int = 0,
    val pendingGiftCount: Int = 0,
    val pendingFriendCount: Int = 0,
    val unreadCount: Int = 0,
    val pendingFamilyInviteCount: Int = 0,
    val pendingGifts: List<SteamPendingGift> = emptyList(),
    val fetchedAt: Long = 0L
)

data class SteamNotificationsUiState(
    val snapshot: SteamNotificationSnapshot? = null,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
    val actionGiftId: String? = null
)
