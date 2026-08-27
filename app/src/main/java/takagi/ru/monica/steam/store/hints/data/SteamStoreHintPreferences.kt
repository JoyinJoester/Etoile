package takagi.ru.monica.steam.store.hints.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import takagi.ru.monica.steam.store.hints.domain.SteamStoreHintSettings

private val Context.steamStoreHintDataStore by preferencesDataStore(
    name = "steam_store_hint_settings"
)

class SteamStoreHintPreferences(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<SteamStoreHintSettings> = appContext.steamStoreHintDataStore.data.map {
        SteamStoreHintSettings(
            ownershipHintsEnabled = it[KEY_OWNERSHIP] ?: true,
            familySharingHintsEnabled = it[KEY_FAMILY_SHARING] ?: true,
            wishlistHintsEnabled = it[KEY_WISHLIST] ?: true,
            storeTagsEnabled = it[KEY_STORE_TAGS] ?: true
        )
    }

    suspend fun setOwnershipHintsEnabled(enabled: Boolean) = update {
        this[KEY_OWNERSHIP] = enabled
    }

    suspend fun setFamilySharingHintsEnabled(enabled: Boolean) = update {
        this[KEY_FAMILY_SHARING] = enabled
    }

    suspend fun setWishlistHintsEnabled(enabled: Boolean) = update {
        this[KEY_WISHLIST] = enabled
    }

    suspend fun setStoreTagsEnabled(enabled: Boolean) = update {
        this[KEY_STORE_TAGS] = enabled
    }

    private suspend fun update(transform: MutablePreferences.() -> Unit) {
        appContext.steamStoreHintDataStore.edit(transform)
    }

    private companion object {
        val KEY_OWNERSHIP = booleanPreferencesKey("ownership_hints_enabled")
        val KEY_FAMILY_SHARING = booleanPreferencesKey("family_sharing_hints_enabled")
        val KEY_WISHLIST = booleanPreferencesKey("wishlist_hints_enabled")
        val KEY_STORE_TAGS = booleanPreferencesKey("store_tags_enabled")
    }
}
