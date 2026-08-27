package takagi.ru.monica.steam.alerts.domain

import takagi.ru.monica.steam.store.domain.SteamWishlistItem

object SteamWishlistDiscountPolicy {
    fun newlyDiscounted(
        previous: List<SteamWishlistItem>?,
        current: List<SteamWishlistItem>
    ): List<SteamWishlistItem> {
        if (previous == null) return emptyList()
        val previousDiscounts = previous.associate { it.appId to it.discountPercent }
        return current.filter { item ->
            item.discountPercent > 0 &&
                item.discountPercent > (previousDiscounts[item.appId] ?: 0)
        }.distinctBy(SteamWishlistItem::appId)
    }
}
