package takagi.ru.monica.steam.friends.chat.background

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatNotificationPresentationGuardTest {
    @Test
    fun foregroundServiceNotificationIsSilentAndDoesNotAdvertiseListeningState() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationPublisher.kt"
        ).readText()
        val foreground = publisher
            .substringAfter("fun foregroundNotification(")
            .substringBefore("fun updateForeground(")

        assertFalse(foreground.contains("steam_chat_background_connected"))
        assertTrue(foreground.contains("FOREGROUND_SERVICE_DEFERRED"))
        assertTrue(foreground.contains("setShowWhen(false)"))
        assertTrue(publisher.contains("steam_chat_background_runtime_v2"))
        assertTrue(publisher.contains("setShowBadge(false)"))
    }

    @Test
    fun incomingMessagesUseASeparateHighPriorityMessagingNotification() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationPublisher.kt"
        ).readText()
        val incoming = publisher
            .substringAfter("fun publishIncomingMessage(")
            .substringBefore("fun cancelConversation(")

        assertTrue(incoming.contains("NotificationCompat.MessagingStyle"))
        assertTrue(incoming.contains("MESSAGE_CHANNEL_ID"))
        assertTrue(incoming.contains("NotificationCompat.PRIORITY_HIGH"))
        assertTrue(incoming.contains("notificationManager.notify(address.tag, address.id"))
    }

    @Test
    fun failedNotificationPostsReleaseTheirDeduplicationClaim() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundService.kt"
        ).readText()
        val processMessage = service
            .substringAfter("private suspend fun processMessage(")
            .substringBefore("private fun startForegroundCompat(")
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundPreferences.kt"
        ).readText()

        assertTrue(processMessage.contains("val published ="))
        assertTrue(processMessage.contains("preferences.releaseNotification(decision.identity)"))
        assertTrue(processMessage.contains("chat_background_notify failed"))
        assertTrue(preferences.contains("suspend fun releaseNotification("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
