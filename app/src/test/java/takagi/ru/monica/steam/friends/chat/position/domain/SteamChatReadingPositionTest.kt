package takagi.ru.monica.steam.friends.chat.position.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamChatReadingPositionTest {
    @Test
    fun requestedMessageWinsThenSavedPositionThenLatest() {
        val ids = listOf("a", "b", "c")

        assertEquals(0, resolveSteamChatReadingIndex(ids, "a", "b"))
        assertEquals(1, resolveSteamChatReadingIndex(ids, null, "b"))
        assertEquals(2, resolveSteamChatReadingIndex(ids, null, "missing"))
    }

    @Test
    fun countsOnlyMessagesBelowTheVisibleViewport() {
        val ids = listOf("a", "b", "c", "d")

        assertEquals(2, steamChatMessagesBelow(ids, "b"))
        assertEquals(0, steamChatMessagesBelow(ids, "d"))
        assertEquals(0, steamChatMessagesBelow(ids, null))
    }
}
