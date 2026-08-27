package takagi.ru.monica.steam.notifications.domain

fun markSteamNotificationsRead(
    snapshot: SteamNotificationSnapshot,
    notificationIds: Set<String>
): SteamNotificationSnapshot {
    val ids = notificationIds.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
    if (ids.isEmpty()) return snapshot

    var newlyRead = 0
    val notifications = snapshot.notifications.map { notification ->
        if (notification.id in ids && !notification.read) {
            newlyRead++
            notification.copy(read = true)
        } else {
            notification
        }
    }
    if (newlyRead == 0) return snapshot

    return snapshot.copy(
        notifications = notifications,
        unreadCount = (snapshot.unreadCount - newlyRead).coerceAtLeast(0)
    )
}
