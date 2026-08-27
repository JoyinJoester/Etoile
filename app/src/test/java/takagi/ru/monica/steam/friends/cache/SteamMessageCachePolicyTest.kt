package takagi.ru.monica.steam.friends.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamMessageCachePolicyTest {
    @Test
    fun keepsRecentWindowAndOnlyTheLatestOlderUnconfirmedMessages() {
        val messages = (0 until 700).map { index ->
            CachedMessage(index, unconfirmed = index < 100)
        }

        val bounded = boundedSteamMessageCache(
            messages = messages,
            maximumRecentMessages = 500,
            maximumRetainedUnconfirmed = 64,
            retainOutsideRecentWindow = CachedMessage::unconfirmed
        )

        assertEquals((36 until 100).toList() + (200 until 700).toList(), bounded.map { it.id })
    }

    @Test
    fun shortConversationIsReturnedWithoutCopyingOrTrimming() {
        val messages = listOf(CachedMessage(1, false), CachedMessage(2, true))

        val bounded = boundedSteamMessageCache(messages) { it.unconfirmed }

        assertEquals(messages, bounded)
    }

    private data class CachedMessage(val id: Int, val unconfirmed: Boolean)
}
