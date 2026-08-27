package takagi.ru.monica.steam.store.navigation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamStoreDetailHistoryTest {
    @Test
    fun relatedDetailsReturnInLastOpenedOrder() {
        val history = SteamStoreDetailHistory()
        val game = SteamStoreDetailRoute(100, "CN")
        val dlc = SteamStoreDetailRoute(200)

        history.push(game)
        history.push(dlc)

        assertEquals(dlc, history.pop())
        assertEquals(game, history.pop())
        assertNull(history.pop())
    }

    @Test
    fun rootNavigationCanClearPreviousDetailHistory() {
        val history = SteamStoreDetailHistory()
        history.push(SteamStoreDetailRoute(100))

        history.clear()

        assertEquals(0, history.size)
        assertNull(history.pop())
    }

    @Test
    fun duplicateAdjacentRoutesAreIgnored() {
        val history = SteamStoreDetailHistory()
        val route = SteamStoreDetailRoute(100)

        history.push(route)
        history.push(route)

        assertEquals(1, history.size)
    }
}
