package takagi.ru.monica.steam.friends.chat.background.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatBackgroundSettings
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationIdentity

private val Context.steamChatBackgroundDataStore by preferencesDataStore(
    name = "steam_chat_background_settings"
)

class SteamChatBackgroundPreferences(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<SteamChatBackgroundSettings> =
        appContext.steamChatBackgroundDataStore.data.map { values ->
            SteamChatBackgroundSettings(enabled = values[KEY_ENABLED] ?: false)
        }

    suspend fun setEnabled(enabled: Boolean) {
        appContext.steamChatBackgroundDataStore.edit { values ->
            values[KEY_ENABLED] = enabled
        }
    }

    /** Atomically claims a realtime message before a notification is posted. */
    suspend fun claimNotification(identity: SteamChatNotificationIdentity): Boolean {
        var claimed = false
        appContext.steamChatBackgroundDataStore.edit { values ->
            val update = SteamChatNotificationHistory.claim(
                encodedHistory = values[KEY_RECENT_NOTIFICATIONS],
                notificationKey = identity.stableKey
            )
            claimed = update.claimed
            values[KEY_RECENT_NOTIFICATIONS] = update.encodedHistory
        }
        return claimed
    }

    /** Releases a claim when Android rejects the notification so a replay can retry it. */
    suspend fun releaseNotification(identity: SteamChatNotificationIdentity) {
        appContext.steamChatBackgroundDataStore.edit { values ->
            values[KEY_RECENT_NOTIFICATIONS] = SteamChatNotificationHistory.release(
                encodedHistory = values[KEY_RECENT_NOTIFICATIONS],
                notificationKey = identity.stableKey
            )
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_RECENT_NOTIFICATIONS = stringPreferencesKey("recent_notification_keys")
    }
}
