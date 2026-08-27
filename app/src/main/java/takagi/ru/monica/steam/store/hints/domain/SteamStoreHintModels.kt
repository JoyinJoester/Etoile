package takagi.ru.monica.steam.store.hints.domain

import takagi.ru.monica.steam.store.domain.SteamStoreDetail

data class SteamStoreHintSettings(
    val ownershipHintsEnabled: Boolean = true,
    val familySharingHintsEnabled: Boolean = true,
    val wishlistHintsEnabled: Boolean = true,
    val storeTagsEnabled: Boolean = true
)

enum class SteamStoreHintKind {
    OWNED,
    FAMILY_SHARED,
    WISHLIST,
    SUPPORTS_FAMILY_SHARING
}

fun resolveSteamStoreItemHints(
    appId: Int,
    settings: SteamStoreHintSettings,
    ownedAppIds: Set<Int>,
    familySharedAppIds: Set<Int>,
    wishlistAppIds: Set<Int>
): List<SteamStoreHintKind> = buildList {
    when {
        settings.ownershipHintsEnabled && appId in ownedAppIds ->
            add(SteamStoreHintKind.OWNED)
        settings.familySharingHintsEnabled && appId in familySharedAppIds ->
            add(SteamStoreHintKind.FAMILY_SHARED)
    }
    if (settings.wishlistHintsEnabled && appId in wishlistAppIds) {
        add(SteamStoreHintKind.WISHLIST)
    }
}

fun resolveSteamStoreDetailHints(
    detail: SteamStoreDetail,
    settings: SteamStoreHintSettings,
    owned: Boolean,
    familyShared: Boolean,
    inWishlist: Boolean
): List<SteamStoreHintKind> = buildList {
    when {
        settings.ownershipHintsEnabled && owned -> add(SteamStoreHintKind.OWNED)
        settings.familySharingHintsEnabled && familyShared ->
            add(SteamStoreHintKind.FAMILY_SHARED)
    }
    if (settings.wishlistHintsEnabled && inWishlist) {
        add(SteamStoreHintKind.WISHLIST)
    }
    if (settings.familySharingHintsEnabled && detail.supportsSteamFamilySharing()) {
        add(SteamStoreHintKind.SUPPORTS_FAMILY_SHARING)
    }
}

internal fun SteamStoreDetail.supportsSteamFamilySharing(): Boolean = categories.any { category ->
    val normalized = category.trim().lowercase()
    normalized == "家庭共享" ||
        normalized == "steam 家庭共享" ||
        normalized == "family sharing" ||
        normalized == "steam family sharing"
}
