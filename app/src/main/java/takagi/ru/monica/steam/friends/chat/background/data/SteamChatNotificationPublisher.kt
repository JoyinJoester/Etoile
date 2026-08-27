package takagi.ru.monica.steam.friends.chat.background.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import takagi.ru.monica.EtoileActivity
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreview
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreviewKind
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal enum class SteamChatBackgroundConnectionState {
    WAITING_FOR_ACCOUNT,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}
internal class SteamChatNotificationPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val friendsCache = SteamFriendsPreferencesCache(appContext)
    private val recentConversationMessages = object : LinkedHashMap<
        SteamChatNotificationAddress,
        List<RecentConversationMessage>
    >(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<
                SteamChatNotificationAddress,
                List<RecentConversationMessage>
            >?
        ): Boolean = size > MAX_TRACKED_CONVERSATIONS
    }

    fun canPostMessageNotifications(): Boolean {
        createChannels()
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(MESSAGE_CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                return false
            }
        }
        return true
    }

    fun foregroundNotification(
        handle: SteamAccountSessionHandle?,
        state: SteamChatBackgroundConnectionState
    ): Notification {
        createChannels()
        val accountName = handle?.account?.displayName
            ?.ifBlank { handle.account.accountName }
            ?.ifBlank { handle.account.steamId }
        val statusText = when (state) {
            SteamChatBackgroundConnectionState.WAITING_FOR_ACCOUNT ->
                appContext.getString(R.string.steam_chat_background_waiting)
            SteamChatBackgroundConnectionState.CONNECTING ->
                appContext.getString(R.string.steam_chat_background_connecting, accountName.orEmpty())
            SteamChatBackgroundConnectionState.CONNECTED -> null
            SteamChatBackgroundConnectionState.RECONNECTING ->
                appContext.getString(R.string.steam_chat_background_reconnecting, accountName.orEmpty())
        }
        return NotificationCompat.Builder(appContext, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentIntent(handle?.let { chatListPendingIntent(it) } ?: launcherPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .apply {
                statusText?.let(::setContentText)
            }
            .build()
    }

    fun updateForeground(
        handle: SteamAccountSessionHandle?,
        state: SteamChatBackgroundConnectionState
    ) {
        notificationManager.notify(
            SERVICE_NOTIFICATION_ID,
            foregroundNotification(handle, state)
        )
    }

    fun publishIncomingMessage(
        handle: SteamAccountSessionHandle,
        message: SteamChatMessage,
        preview: SteamChatNotificationPreview
    ): Boolean {
        if (!canPostMessageNotifications()) return false
        createChannels()
        val accountName = handle.account.displayName
            .ifBlank { handle.account.accountName }
            .ifBlank { handle.account.steamId }
        val friendName = friendsCache.load(handle.account.steamId)
            ?.friends
            ?.firstOrNull { friend -> friend.steamId == message.partnerSteamId }
            ?.displayName
            .orEmpty()
            .ifBlank { message.partnerSteamId }
        val previewText = preview.displayText(appContext)
        val accountPerson = Person.Builder().setName(accountName).build()
        val friendPerson = Person.Builder()
            .setName(friendName)
            .setKey(message.partnerSteamId)
            .build()
        val timestampMillis = message.timestamp
            .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        val address = steamChatNotificationAddress(
            accountKey = handle.stableKey,
            partnerSteamId = message.partnerSteamId
        )
        val conversationMessages = rememberConversationMessage(
            address = address,
            message = message,
            text = previewText,
            timestampMillis = timestampMillis
        )
        val publicVersion = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(appContext.getString(R.string.steam_chat_notification_public_title))
            .setContentText(appContext.getString(R.string.steam_chat_notification_public_text))
            .build()
        val notification = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(friendName)
            .setContentText(previewText)
            .setSubText(accountName)
            .setStyle(
                NotificationCompat.MessagingStyle(accountPerson).also { style ->
                    conversationMessages.forEach { recent ->
                        style.addMessage(
                            recent.text,
                            recent.timestampMillis,
                            friendPerson
                        )
                    }
                }
            )
            .setContentIntent(
                SteamChatNotificationContract.openConversationPendingIntent(
                    appContext,
                    handle,
                    message.partnerSteamId
                )
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(address.groupKey)
            .build()
        return runCatching {
            notificationManager.notify(address.tag, address.id, notification)
        }.isSuccess
    }

    fun cancelConversation(accountKey: String, partnerSteamId: String) {
        val address = steamChatNotificationAddress(accountKey, partnerSteamId)
        synchronized(recentConversationMessages) {
            recentConversationMessages.remove(address)
        }
        notificationManager.cancel(address.tag, address.id)
    }

    fun cancelForeground() {
        notificationManager.cancel(SERVICE_NOTIFICATION_ID)
    }

    private fun rememberConversationMessage(
        address: SteamChatNotificationAddress,
        message: SteamChatMessage,
        text: String,
        timestampMillis: Long
    ): List<RecentConversationMessage> = synchronized(recentConversationMessages) {
        val identity = "${message.timestamp}:${message.ordinal}"
        val updated = (
            recentConversationMessages[address]
                .orEmpty()
                .filterNot { recent -> recent.identity == identity } +
                RecentConversationMessage(
                    identity = identity,
                    text = text,
                    timestampMillis = timestampMillis,
                    ordinal = message.ordinal
                )
            )
            .sortedWith(
                compareBy<RecentConversationMessage> { recent -> recent.timestampMillis }
                    .thenBy { recent -> recent.ordinal }
            )
            .takeLast(MAX_RECENT_MESSAGES)
        recentConversationMessages[address] = updated
        updated
    }

    private fun chatListPendingIntent(handle: SteamAccountSessionHandle): PendingIntent =
        SteamChatNotificationContract.openChatListPendingIntent(appContext, handle)

    private fun launcherPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        SERVICE_NOTIFICATION_ID,
        Intent(appContext, EtoileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                appContext.getString(R.string.steam_chat_background_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.steam_chat_background_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
        )
        manager.deleteNotificationChannel(LEGACY_SERVICE_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                appContext.getString(R.string.steam_chat_message_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.steam_chat_message_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun SteamChatNotificationPreview.displayText(context: Context): String = when (kind) {
        SteamChatNotificationPreviewKind.TEXT -> text
        SteamChatNotificationPreviewKind.STICKER -> context.getString(
            R.string.steam_chat_notification_sticker,
            text
        )
        SteamChatNotificationPreviewKind.IMAGE -> context.getString(
            R.string.steam_chat_notification_image,
            text
        )
        SteamChatNotificationPreviewKind.VIDEO -> context.getString(
            R.string.steam_chat_notification_video,
            text
        )
        SteamChatNotificationPreviewKind.FILE -> context.getString(
            R.string.steam_chat_notification_file,
            text
        )
        SteamChatNotificationPreviewKind.GAME_INVITE -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_game_invite)
        }
        SteamChatNotificationPreviewKind.STEAM_EVENT -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_steam_event)
        }
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 887_001
        private const val SERVICE_CHANNEL_ID = "steam_chat_background_runtime_v2"
        private const val LEGACY_SERVICE_CHANNEL_ID = "steam_chat_background_service"
        private const val MESSAGE_CHANNEL_ID = "steam_chat_messages"
        private const val MAX_RECENT_MESSAGES = 6
        private const val MAX_TRACKED_CONVERSATIONS = 48
    }

    private data class RecentConversationMessage(
        val identity: String,
        val text: String,
        val timestampMillis: Long,
        val ordinal: Int
    )
}
