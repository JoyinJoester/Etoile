package takagi.ru.monica.steam.friends.chat.background.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatNotificationHistoryTest {
    @Test
    fun claimsEachNotificationKeyOnce() {
        val first = SteamChatNotificationHistory.claim(null, key(1))
        val duplicate = SteamChatNotificationHistory.claim(first.encodedHistory, key(1))

        assertTrue(first.claimed)
        assertFalse(duplicate.claimed)
        assertEquals(listOf(key(1)), SteamChatNotificationHistory.decode(duplicate.encodedHistory))
    }

    @Test
    fun keepsOnlyTheNewestBoundedEntriesAndDropsCorruptRows() {
        val first = SteamChatNotificationHistory.claim("corrupt\n${key(1)}", key(2), 2)
        val second = SteamChatNotificationHistory.claim(first.encodedHistory, key(3), 2)

        assertEquals(
            listOf(key(2), key(3)),
            SteamChatNotificationHistory.decode(second.encodedHistory)
        )
    }

    @Test
    fun releasesAFailedNotificationSoItCanBeClaimedAgain() {
        val first = SteamChatNotificationHistory.claim(null, key(1))
        val released = SteamChatNotificationHistory.release(first.encodedHistory, key(1))
        val retried = SteamChatNotificationHistory.claim(released, key(1))

        assertTrue(retried.claimed)
        assertEquals(listOf(key(1)), SteamChatNotificationHistory.decode(retried.encodedHistory))
    }

    private fun key(value: Int): String = value.toString(16).padStart(64, '0')
}
