package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockTabTest {
    @Test
    fun defaultOrderContainsChatWithTheSortableContentTabs() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT
            ),
            SteamDockTab.DEFAULT_ORDER
        )
    }

    @Test
    fun sanitizeKeepsOnlyEnabledContentTabs() {
        assertEquals(
            emptyList<SteamDockTab>(),
            SteamDockTab.sanitizeOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.TOKEN, SteamDockTab.SETTINGS)
            )
        )
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT
            ),
            SteamDockTab.completeOrder(listOf(SteamDockTab.SETTINGS))
        )
    }

    @Test
    fun liquidGlassOrderAlwaysContainsAllFiveDestinations() {
        assertEquals(
            listOf(
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.completeLiquidGlassOrder(
                listOf(
                    SteamDockTab.CHAT,
                    SteamDockTab.TOKEN,
                    SteamDockTab.CHAT,
                    SteamDockTab.STORE
                )
            )
        )
    }

    @Test
    fun liquidGlassOrderCanMoveEveryDestination() {
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN
            ),
            reorderLiquidGlassDockOrder(
                SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
                fromIndex = 4,
                toIndex = 0
            )
        )
    }

    @Test
    fun fixedOrderAlwaysContainsAllFiveDestinationsAndCanBeReordered() {
        assertEquals(
            listOf(
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.completeFixedOrder(
                listOf(SteamDockTab.CHAT, SteamDockTab.TOKEN, SteamDockTab.CHAT)
            )
        )
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN
            ),
            reorderFixedDockOrder(
                SteamDockTab.FIXED_DEFAULT_ORDER,
                fromIndex = 4,
                toIndex = 0
            )
        )
    }

    @Test
    fun legacyDefaultOrderMigratesButCustomOrderIsPreserved() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            resolveStoredDockOrder(
                listOf(SteamDockTab.LIBRARY, SteamDockTab.STORE, SteamDockTab.SETTINGS)
            )
        )
        assertEquals(
            listOf(SteamDockTab.STORE, SteamDockTab.LIBRARY, SteamDockTab.CHAT),
            resolveStoredDockOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE, SteamDockTab.LIBRARY)
            )
        )
        assertEquals(
            listOf(SteamDockTab.STORE),
            resolveStoredDockOrder(
                stored = listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE),
                chatMigrationComplete = true
            )
        )
    }

    @Test
    fun reorderHandlesFirstAndLastItemsWithoutIndexErrors() {
        assertEquals(
            listOf(
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.STORE
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 0, toIndex = 2)
        )
        assertEquals(
            listOf(
                SteamDockTab.CHAT,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 2, toIndex = 0)
        )
    }

    @Test
    fun reorderIgnoresLazyListHeaderIndicesInsteadOfThrowing() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 3, toIndex = 1)
        )
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 1, toIndex = 3)
        )
    }

    @Test
    fun dockSwipeMovesOnlyToAdjacentContentTab() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.LIBRARY,
            dockSwipeTarget(order, SteamDockTab.STORE, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 80f, thresholdPx = 56f)
        )
        assertEquals(
            null,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 20f, thresholdPx = 56f)
        )
    }

    @Test
    fun tokenSwipeEntersTheNearestEdgeOfTheContentDock() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.CHAT,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = 80f, thresholdPx = 56f)
        )
    }
}
