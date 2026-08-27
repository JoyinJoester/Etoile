package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.presentation.SteamStoreUiState
import takagi.ru.monica.steam.store.presentation.withIgnoredGameState

class SteamStoreIgnoredUiStateTest {
    @Test
    fun ignoringCurrentGameRemovesItFromEveryVisibleStoreList() {
        val state = SteamStoreUiState(
            home = SteamStoreHome(
                specials = listOf(item(1), item(2)),
                topSellers = listOf(item(2), item(3))
            ),
            catalogPage = SteamStoreCatalogPage(
                filter = SteamStoreBrowseFilter.TOP_SELLERS,
                items = listOf(item(2), item(3)),
                start = 0,
                totalCount = 10
            ),
            searchResults = listOf(item(1), item(2)),
            detail = SteamStoreDetail(appId = 2, name = "Ignored game")
        )

        val updated = state.withIgnoredGameState(appId = 2, ignored = true)

        assertFalse(updated.home.orEmptyAppIds().contains(2))
        assertFalse(updated.catalogPage?.items.orEmpty().any { it.appId == 2 })
        assertFalse(updated.searchResults.any { it.appId == 2 })
        assertTrue(updated.detail?.ignored == true)
        assertEquals(2, updated.catalogPage?.nextStart)
    }

    @Test
    fun undoingIgnoreUpdatesDetailWithoutInventingMissingListData() {
        val state = SteamStoreUiState(
            home = SteamStoreHome(specials = listOf(item(1))),
            searchResults = listOf(item(1)),
            detail = SteamStoreDetail(appId = 2, name = "Ignored game", ignored = true)
        )

        val updated = state.withIgnoredGameState(appId = 2, ignored = false)

        assertFalse(updated.detail?.ignored == true)
        assertFalse(updated.home.orEmptyAppIds().contains(2))
        assertFalse(updated.searchResults.any { it.appId == 2 })
    }

    @Test
    fun localMutationPublishesItsBackgroundSyncState() {
        val state = SteamStoreUiState(
            detail = SteamStoreDetail(appId = 2, name = "Ignored game")
        )

        val updated = state.withIgnoredGameState(
            appId = 2,
            ignored = true,
            syncState = SteamStoreIgnoreSyncState.PENDING
        )

        assertTrue(updated.detail?.ignored == true)
        assertEquals(
            SteamStoreIgnoreSyncState.PENDING,
            updated.ignoredSyncStates[2]
        )
    }

    private fun item(appId: Int) = SteamStoreItem(appId = appId, name = "Game $appId")

    private fun SteamStoreHome?.orEmptyAppIds(): Set<Int> = this?.let {
        (it.specials + it.topSellers + it.newReleases + it.comingSoon)
            .mapTo(linkedSetOf(), SteamStoreItem::appId)
    }.orEmpty()
}
