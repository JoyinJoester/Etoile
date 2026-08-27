package takagi.ru.monica.steam.friends.chat.background

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatBackgroundIntegrationGuardTest {
    @Test
    fun manifestDeclaresTheAndroid14SpecialUseForegroundService() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.contains(".steam.friends.chat.background.data.SteamChatBackgroundService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertTrue(manifest.contains("persistent Steam CM connection"))
        assertTrue(manifest.contains(".steam.friends.chat.background.data.SteamChatBackgroundReceiver"))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
    }

    @Test
    fun serviceUsesTheSelectedSourceHandleAndPersistentRealtimeGateway() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundService.kt"
        ).readText()
        val sources = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt"
        ).readText()

        assertTrue(service.contains("combine(sourceRepository.state)"))
        assertTrue(service.contains("collectLatest"))
        assertTrue(service.contains("sessionHandleForSource"))
        assertTrue(service.contains("SteamFriendChatRealtimeService"))
        assertTrue(service.contains("SteamChatNotificationPolicy.evaluate"))
        assertTrue(service.contains("preferences.claimNotification"))
        assertFalse(service.contains("loadAllSessionHandles"))
        assertTrue(sources.contains("fun sessionHandleForSource("))
        assertTrue(sources.contains("current.storageSource != source"))
        assertTrue(sources.contains("entryId = record.entryId"))
    }

    @Test
    fun settingsAreOptInAndReuseTheSharedMonicaSettingsSurface() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/settings/ui/SteamNotificationSettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/EtoileSharedSettingsHost.kt"
        ).readText()
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundPreferences.kt"
        ).readText()

        assertTrue(settings.contains("SettingsSection"))
        assertTrue(settings.contains("SettingsItemWithSwitch"))
        assertTrue(settings.contains("POST_NOTIFICATIONS"))
        assertTrue(settings.contains("SteamChatBackgroundServiceController.start"))
        assertTrue(host.contains("additionalSettingsContent"))
        assertTrue(host.contains("SteamNotificationSettingsEntry"))
        assertTrue(preferences.contains("enabled = values[KEY_ENABLED] ?: false"))
        assertTrue(preferences.contains("preferencesDataStore"))
    }

    @Test
    fun messageNotificationsArePrivateDeduplicatedAndOpenTheExactConversation() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationPublisher.kt"
        ).readText()
        val contract = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationContract.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileActivity.kt"
        ).readText()
        val facade = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/SteamChatBackground.kt"
        ).readText()

        assertTrue(publisher.contains("VISIBILITY_PRIVATE"))
        assertTrue(publisher.contains("setPublicVersion(publicVersion)"))
        assertTrue(publisher.contains("openConversationPendingIntent"))
        assertTrue(publisher.contains("MESSAGE_CHANNEL_ID"))
        assertTrue(publisher.contains("SERVICE_CHANNEL_ID"))
        assertTrue(publisher.contains("cancelConversation"))
        assertTrue(publisher.contains("recentConversationMessages"))
        assertTrue(publisher.contains("notificationManager.notify(address.tag, address.id"))
        assertTrue(contract.contains("handle.origin.entryId"))
        assertTrue(contract.contains("EXTRA_PARTNER_STEAM_ID"))
        assertTrue(activity.contains("override fun onNewIntent"))
        assertTrue(activity.contains("SteamChatBackground.consumeNotification"))
        assertTrue(activity.contains("SteamChatBackground.activateNotificationTarget"))
        assertFalse(activity.contains("friends.chat.background.data."))
        assertFalse(activity.contains("friends.chat.background.domain."))
        assertTrue(facade.contains("activateChatNotificationTarget"))
        assertTrue(activity.contains("currentPage = EtoilePage.CHAT"))
    }

    @Test
    fun applicationAndBootReceiverRestoreOnlyTheExplicitlyEnabledService() {
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/EtoileApplication.kt"
        ).readText()
        val facade = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/SteamChatBackground.kt"
        ).readText()
        val controller = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundServiceController.kt"
        ).readText()

        assertTrue(application.contains("SteamChatBackground.syncService"))
        assertFalse(application.contains("friends.chat.background.data."))
        assertTrue(facade.contains("SteamChatBackgroundServiceController.sync"))
        assertTrue(controller.contains("settings.first().enabled"))
        assertTrue(controller.contains("ContextCompat.startForegroundService"))
        assertTrue(controller.contains("stopService"))
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
