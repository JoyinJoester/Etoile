package takagi.ru.monica.steam.friends.chat.background.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SteamChatNotificationAddressTest {
    @Test
    fun accountSourceIsolatedTagsPreventCrossAccountReplacement() {
        val local = steamChatNotificationAddress(
            "room|1|76561198000000001",
            "76561198000000002"
        )
        val mdbx = steamChatNotificationAddress(
            "mdbx:9:entry|1|76561198000000001",
            "76561198000000002"
        )

        assertNotEquals(local.tag, mdbx.tag)
        assertEquals(local.id, mdbx.id)
        assertNotEquals(local.groupKey, mdbx.groupKey)
        assertFalse(local.tag.contains("76561198000000001"))
        assertFalse(mdbx.tag.contains("entry"))
    }

    @Test
    fun sameConversationProducesAStableAddress() {
        val first = steamChatNotificationAddress("account-key", "76561198000000002")
        val second = steamChatNotificationAddress("account-key", "76561198000000002")

        assertEquals(first, second)
    }
}
