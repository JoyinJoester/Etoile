package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamNativeCartTest {
    @Test
    fun checkoutUsesDistinctAvailablePackagesAndTotalUsesCurrentPrices() {
        val items = listOf(
            SteamCartItem(1, 100, "A", finalPriceCents = 1200),
            SteamCartItem(2, 100, "B", finalPriceCents = 800),
            SteamCartItem(3, null, "C", finalPriceCents = null)
        )
        assertEquals(listOf(100), steamCartCheckoutPackageIds(items))
        assertEquals(listOf(100), steamCartCheckoutLines(items).map { it.packageId })
        assertEquals(2000, steamCartTotalCents(items))
    }

    @Test
    fun duplicateCartAndWishlistRowsReceiveUniqueKeys() {
        val cart = SteamCartItem(730, 1, "Counter-Strike 2")
        val wishlist = SteamWishlistItem(730, "Counter-Strike 2")

        val cartKeys = listOf(cart, cart).mapIndexed(::steamCartLazyKey)
        val wishlistKeys = listOf(wishlist, wishlist).mapIndexed(::steamWishlistLazyKey)

        assertEquals(2, cartKeys.distinct().size)
        assertEquals(2, wishlistKeys.distinct().size)
    }

    @Test
    fun selectedPurchaseOptionControlsTheCheckoutPackageAndPrice() {
        val bundle = SteamStorePackageOption(
            packageId = 20,
            title = "Portal Bundle",
            priceCents = 3000,
            discountPercent = 25
        )
        val detail = SteamStoreDetail(
            appId = 620,
            name = "Portal 2",
            packageId = 10,
            initialPriceCents = 4200,
            finalPriceCents = 2100,
            packageOptions = listOf(
                SteamStorePackageOption(packageId = 10, priceCents = 2100),
                bundle
            )
        )

        val item = detail.toCartItem(bundle)

        assertEquals(20, item.packageId)
        assertEquals(3000, item.finalPriceCents)
        assertEquals(3000, item.initialPriceCents)
        assertEquals(25, item.discountPercent)
    }
}
