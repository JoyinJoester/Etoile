package takagi.ru.monica.steam.friends.voice.background

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
import androidx.core.content.ContextCompat
import takagi.ru.monica.EtoileActivity
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState

internal class SteamVoiceNotificationPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    fun notification(state: SteamVoiceCallState): Notification {
        val target = state.target
        val incoming = state.incomingRequest != null && target == null
        createChannels()
        val title = when {
            incoming -> "Steam 语音通话邀请"
            target != null -> target.title
            else -> "Steam 语音"
        }
        val text = when {
            incoming -> "${state.incomingRequest?.partnerSteamId.orEmpty()} 邀请你进行语音聊天"
            state.state == SteamVoiceConnectionState.CONNECTED ->
                "正在语音聊天 · ${state.participants.size.coerceAtLeast(1)} 人"
            state.state == SteamVoiceConnectionState.WAITING_FOR_ACCEPT -> "等待对方接听"
            state.state == SteamVoiceConnectionState.RECONNECTING -> "正在重新连接语音聊天"
            else -> "正在连接 Steam 语音聊天"
        }
        val builder = NotificationCompat.Builder(
            appContext,
            if (incoming) INCOMING_CHANNEL_ID else ONGOING_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launcherPendingIntent())
            .setOngoing(!incoming)
            .setOnlyAlertOnce(!incoming)
            .setAutoCancel(incoming)
            .setCategory(if (incoming) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (incoming) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (incoming) {
            builder.addAction(
                R.drawable.ic_steam_chat_notification,
                "接听",
                servicePendingIntent(SteamVoiceCallService.ACTION_ACCEPT)
            )
            builder.addAction(
                R.drawable.ic_steam_chat_notification,
                "拒绝",
                servicePendingIntent(SteamVoiceCallService.ACTION_REJECT)
            )
        } else if (target != null) {
            builder.addAction(
                R.drawable.ic_steam_chat_notification,
                if (state.microphoneMuted) "打开麦克风" else "静音",
                servicePendingIntent(SteamVoiceCallService.ACTION_TOGGLE_MIC)
            )
            builder.addAction(
                R.drawable.ic_steam_chat_notification,
                if (state.outputMuted) "打开声音" else "静音声音",
                servicePendingIntent(SteamVoiceCallService.ACTION_TOGGLE_OUTPUT)
            )
            builder.addAction(
                R.drawable.ic_steam_chat_notification,
                "离开",
                servicePendingIntent(SteamVoiceCallService.ACTION_STOP)
            )
        }
        return builder.build()
    }

    fun post(state: SteamVoiceCallState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(NOTIFICATION_ID, notification(state))
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun launcherPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        NOTIFICATION_ID,
        Intent(appContext, EtoileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(appContext, SteamVoiceCallService::class.java).setAction(action)
        val requestCode = (NOTIFICATION_ID.toString() + action).hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(appContext, requestCode, intent, flags)
        } else {
            PendingIntent.getService(appContext, requestCode, intent, flags)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    INCOMING_CHANNEL_ID,
                    "Steam 语音邀请",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "好友发来的 Steam 语音聊天邀请"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    ONGOING_CHANNEL_ID,
                    "Steam 语音聊天",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Steam 语音聊天进行中的控制通知"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    setSound(null, null)
                }
            )
        )
    }

    companion object {
        const val NOTIFICATION_ID = 887_002
        private const val INCOMING_CHANNEL_ID = "steam_voice_incoming"
        private const val ONGOING_CHANNEL_ID = "steam_voice_calls"
    }
}
