package takagi.ru.monica.steam.store.interest.data

import android.content.Context
import android.content.SharedPreferences

internal interface SteamStoreInterestKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

internal interface SteamStoreInterestSyncSettings {
    val syncWithSteam: Boolean
}

internal class SteamStoreInterestPreferences internal constructor(
    private val store: SteamStoreInterestKeyValueStore
) : SteamStoreInterestSyncSettings {
    constructor(context: Context) : this(
        SteamStoreInterestSharedPreferencesStore(context.applicationContext)
    )

    override val syncWithSteam: Boolean
        get() = store.get(KEY_SYNC_WITH_STEAM)?.toBooleanStrictOrNull() ?: true

    fun setSyncWithSteam(enabled: Boolean) {
        store.put(KEY_SYNC_WITH_STEAM, enabled.toString())
    }

    private companion object {
        const val KEY_SYNC_WITH_STEAM = "sync_with_steam"
    }
}

internal class SteamStoreInterestSharedPreferencesStore(
    context: Context
) : SteamStoreInterestKeyValueStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "steam_store_interest"
    }
}
