package takagi.ru.monica.steam.store.freebie.domain

import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus

@Serializable
enum class SteamFreebieOfferKind {
    KEEP_FOREVER,
    FREE_WEEKEND
}

@Serializable
enum class SteamFreebieProductType {
    GAME,
    DLC,
    OTHER
}

@Serializable
enum class SteamFreebieClaimMethod {
    FREE_LICENSE,
    OFFICIAL_CHECKOUT,
    NONE
}

@Serializable
data class SteamFreebieItem(
    val appId: Int,
    val packageId: Int? = null,
    val name: String,
    val imageUrl: String = "",
    val storeUrl: String = "https://store.steampowered.com/",
    val offerKind: SteamFreebieOfferKind,
    val productType: SteamFreebieProductType = SteamFreebieProductType.GAME,
    val claimMethod: SteamFreebieClaimMethod = SteamFreebieClaimMethod.NONE,
    val originalPriceText: String = "",
    val finalPriceText: String = "",
    val discountPercent: Int = 0,
    val endsAtEpochMillis: Long? = null,
    val baseGameAppId: Int? = null,
    val accountCountryCode: String? = null,
    val ownership: SteamStoreOwnershipStatus = SteamStoreOwnershipStatus.UNKNOWN,
    val baseGameOwnership: SteamStoreOwnershipStatus = SteamStoreOwnershipStatus.UNKNOWN
) {
    val isPermanentlyClaimable: Boolean
        get() = offerKind == SteamFreebieOfferKind.KEEP_FOREVER &&
            claimMethod == SteamFreebieClaimMethod.FREE_LICENSE &&
            packageId != null

    val isOwned: Boolean get() = ownership == SteamStoreOwnershipStatus.OWNED

    val needsBaseGame: Boolean
        get() = productType == SteamFreebieProductType.DLC &&
            baseGameAppId != null &&
            baseGameOwnership == SteamStoreOwnershipStatus.NOT_OWNED
}

@Serializable
data class SteamFreebieCatalog(
    val items: List<SteamFreebieItem> = emptyList(),
    val accountCountryCode: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)

enum class SteamFreebieFilter {
    ALL,
    KEEP_FOREVER,
    FREE_WEEKEND,
    DLC
}

enum class SteamFreebieClaimStatus {
    CLAIMED,
    ALREADY_OWNED,
    PENDING_VERIFICATION,
    SESSION_REQUIRED,
    RATE_LIMITED,
    REGION_RESTRICTED,
    NEEDS_BASE_GAME,
    FAILED
}

enum class SteamFreebieLoadFailure {
    SESSION_REQUIRED,
    RATE_LIMITED,
    NETWORK,
    INVALID_RESPONSE
}

data class SteamFreebieClaimResult(
    val status: SteamFreebieClaimStatus,
    val detail: String? = null
)

fun SteamFreebieCatalog.filtered(filter: SteamFreebieFilter): List<SteamFreebieItem> =
    when (filter) {
        SteamFreebieFilter.ALL -> items
        SteamFreebieFilter.KEEP_FOREVER -> items.filter {
            it.offerKind == SteamFreebieOfferKind.KEEP_FOREVER
        }
        SteamFreebieFilter.FREE_WEEKEND -> items.filter {
            it.offerKind == SteamFreebieOfferKind.FREE_WEEKEND
        }
        SteamFreebieFilter.DLC -> items.filter {
            it.productType == SteamFreebieProductType.DLC
        }
    }
