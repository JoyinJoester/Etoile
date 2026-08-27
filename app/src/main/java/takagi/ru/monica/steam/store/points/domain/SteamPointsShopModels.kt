package takagi.ru.monica.steam.store.points.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SteamPointsShopCategory(val communityItemClasses: List<Int>) {
    FEATURED(emptyList()),
    BACKGROUNDS(listOf(3, 12)),
    EMOTICONS(listOf(4)),
    STICKERS(listOf(10)),
    PROFILE(listOf(8, 13, 14)),
    CHAT_EFFECTS(listOf(11))
}

@Serializable
data class SteamPointsShopItem(
    val appId: Int,
    val definitionId: Int,
    val type: Int,
    val communityItemClass: Int,
    val pointCost: Long,
    val title: String,
    val description: String = "",
    val smallImageUrl: String = "",
    val largeImageUrl: String = "",
    val webmUrl: String = "",
    val mp4Url: String = "",
    val smallWebmUrl: String = "",
    val smallMp4Url: String = "",
    val animated: Boolean = false,
    val profileThemeId: String = "",
    val tiled: Boolean = false
) {
    val imageUrl: String
        get() = largeImageUrl.ifBlank { smallImageUrl }

    val previewVideoUrl: String
        get() = mp4Url.ifBlank { webmUrl }.ifBlank { smallMp4Url }.ifBlank { smallWebmUrl }

    val officialUrl: String
        get() = "https://store.steampowered.com/points/shop/reward/$definitionId"
}

@Serializable
data class SteamPointsShopPage(
    val category: SteamPointsShopCategory,
    val items: List<SteamPointsShopItem> = emptyList(),
    val totalCount: Int = 0,
    val nextCursor: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val hasMore: Boolean get() = !nextCursor.isNullOrBlank() && items.size < totalCount
}
