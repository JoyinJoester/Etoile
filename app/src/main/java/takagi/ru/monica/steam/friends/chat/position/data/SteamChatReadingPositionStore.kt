package takagi.ru.monica.steam.friends.chat.position.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingPosition

class SteamChatReadingPositionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(conversationKey: String): SteamChatReadingPosition? = runCatching {
        val raw = preferences.getString(storageKey(conversationKey), null) ?: return null
        json.decodeFromString<SteamChatReadingPosition>(raw)
    }.getOrNull()

    fun save(conversationKey: String, position: SteamChatReadingPosition) {
        if (conversationKey.isBlank() || position.messageId.isBlank()) return
        preferences.edit()
            .putString(storageKey(conversationKey), json.encodeToString(position))
            .apply()
    }

    private fun storageKey(conversationKey: String): String = MessageDigest.getInstance("SHA-256")
        .digest(conversationKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val PREFERENCES_NAME = "steam_chat_reading_positions"
    }
}
