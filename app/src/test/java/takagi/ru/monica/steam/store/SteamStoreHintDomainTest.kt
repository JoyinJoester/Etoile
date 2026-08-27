package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintKind
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintSettings
import takagi.ru.monica.steam.store.hints.domain.resolveSteamStoreDetailHints
import takagi.ru.monica.steam.store.hints.domain.resolveSteamStoreItemHints

class SteamStoreHintDomainTest {
    @Test
    fun ownedStateTakesPriorityOverFamilyLibraryAndCombinesWithWishlist() {
        val hints = resolveSteamStoreItemHints(
            appId = 620,
            settings = SteamStoreHintSettings(),
            ownedAppIds = setOf(620),
            familySharedAppIds = setOf(620),
            wishlistAppIds = setOf(620)
        )

        assertEquals(
            listOf(SteamStoreHintKind.OWNED, SteamStoreHintKind.WISHLIST),
            hints
        )
    }

    @Test
    fun switchesRemoveOnlyTheirRelatedHints() {
        val hints = resolveSteamStoreItemHints(
            appId = 570,
            settings = SteamStoreHintSettings(
                ownershipHintsEnabled = false,
                familySharingHintsEnabled = true,
                wishlistHintsEnabled = false
            ),
            ownedAppIds = setOf(570),
            familySharedAppIds = setOf(570),
            wishlistAppIds = setOf(570)
        )

        assertEquals(listOf(SteamStoreHintKind.FAMILY_SHARED), hints)
    }

    @Test
    fun detailRecognizesOfficialLocalizedFamilySharingCategories() {
        val hints = resolveSteamStoreDetailHints(
            detail = SteamStoreDetail(
                appId = 730,
                name = "Counter-Strike 2",
                categories = listOf("Steam Family Sharing")
            ),
            settings = SteamStoreHintSettings(),
            owned = false,
            familyShared = false,
            inWishlist = false
        )

        assertTrue(SteamStoreHintKind.SUPPORTS_FAMILY_SHARING in hints)
        assertFalse(SteamStoreHintKind.FAMILY_SHARED in hints)
    }
}
