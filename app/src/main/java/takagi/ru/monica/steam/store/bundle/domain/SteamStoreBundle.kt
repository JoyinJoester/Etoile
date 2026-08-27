package takagi.ru.monica.steam.store.bundle.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreBundleItem(
    val appId: Int,
    val name: String = "",
    val imageUrl: String = ""
)

@Serializable
data class SteamStoreBundle(
    val bundleId: Int,
    val title: String,
    val storeUrl: String = "",
    val imageUrl: String = "",
    val finalPriceCents: Int? = null,
    val discountPercent: Int = 0,
    val items: List<SteamStoreBundleItem> = emptyList()
)
