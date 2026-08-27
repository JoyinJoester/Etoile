package takagi.ru.monica.steam.library.context.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext

interface SteamLibraryGameContextCache {
    fun load(accountSteamId: String, appId: Int): SteamLibraryGameContext?
    fun save(context: SteamLibraryGameContext)
}

internal interface SteamLibraryGameContextKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SteamLibraryGameContextPreferencesCache internal constructor(
    private val store: SteamLibraryGameContextKeyValueStore
) : SteamLibraryGameContextCache {
    constructor(context: Context) : this(
        SteamLibraryGameContextPreferencesStore(context.applicationContext)
    )

    override fun load(accountSteamId: String, appId: Int): SteamLibraryGameContext? =
        store.get(key(accountSteamId, appId))
            ?.let(SteamLibraryGameContextCodec::decode)
            ?.takeIf { it.accountSteamId == accountSteamId && it.appId == appId }

    override fun save(context: SteamLibraryGameContext) {
        store.put(
            key(context.accountSteamId, context.appId),
            SteamLibraryGameContextCodec.encode(context)
        )
    }

    private fun key(accountSteamId: String, appId: Int): String {
        val value = "${accountSteamId.trim()}|$appId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "game_context_$digest"
    }
}

private class SteamLibraryGameContextPreferencesStore(
    context: Context
) : SteamLibraryGameContextKeyValueStore {
    private val preferences = context.getSharedPreferences(
        "steam_library_game_context", Context.MODE_PRIVATE
    )

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

internal object SteamLibraryGameContextCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(context: SteamLibraryGameContext): String =
        json.encodeToString(SteamLibraryGameContext.serializer(), context)

    fun decode(raw: String): SteamLibraryGameContext? = runCatching {
        json.decodeFromString(SteamLibraryGameContext.serializer(), raw)
    }.getOrNull()
}
