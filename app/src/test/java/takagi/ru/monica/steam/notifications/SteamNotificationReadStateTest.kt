package takagi.ru.monica.steam.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.notifications.domain.SteamNotification
import takagi.ru.monica.steam.notifications.domain.SteamNotificationKind
import takagi.ru.monica.steam.notifications.domain.SteamNotificationSnapshot
import takagi.ru.monica.steam.notifications.domain.markSteamNotificationsRead

class SteamNotificationReadStateTest {
    @Test
    fun openingUnreadNotificationUpdatesItemAndBadgeExactlyOnce() {
        val snapshot = SteamNotificationSnapshot(
            notifications = listOf(
                notification("1", read = false),
                notification("2", read = false),
                notification("3", read = true)
            ),
            unreadCount = 2
        )

        val first = markSteamNotificationsRead(snapshot, setOf("1"))
        val second = markSteamNotificationsRead(first, setOf("1"))

        assertTrue(first.notifications.first { it.id == "1" }.read)
        assertFalse(first.notifications.first { it.id == "2" }.read)
        assertEquals(1, first.unreadCount)
        assertEquals(first, second)
    }

    private fun notification(id: String, read: Boolean) = SteamNotification(
        id = id,
        type = 10,
        kind = SteamNotificationKind.GENERAL,
        title = "Notification $id",
        summary = "",
        read = read
    )
}
