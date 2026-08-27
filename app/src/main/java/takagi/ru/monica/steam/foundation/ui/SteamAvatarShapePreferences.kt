package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class SteamAvatarShapeOption(val storedValue: String) {
    SQUARE("square"),
    ROUNDED("rounded"),
    CIRCLE("circle");

    companion object {
        fun fromStoredValue(value: String?): SteamAvatarShapeOption {
            return entries.firstOrNull { it.storedValue == value } ?: SQUARE
        }
    }
}
private val Context.steamAvatarShapeDataStore by preferencesDataStore(
    name = "etoile_avatar_shape"
)

class SteamAvatarShapePreferences(context: Context) {
    private val dataStore = context.applicationContext.steamAvatarShapeDataStore

    /**
     * Shape used by avatars that do not render a Steam avatar frame.
     *
     * `avatar_shape` is intentionally used as a fallback so users upgrading
     * from the single-setting version keep their existing appearance.
     */
    val plainShape: Flow<SteamAvatarShapeOption> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.readShape(PLAIN_SHAPE_KEY) }
        .distinctUntilChanged()

    /** Shape used by avatars rendered inside a Steam avatar-frame overlay. */
    val framedShape: Flow<SteamAvatarShapeOption> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.readShape(FRAMED_SHAPE_KEY) }
        .distinctUntilChanged()

    suspend fun updatePlainShape(shape: SteamAvatarShapeOption) {
        dataStore.edit { preferences ->
            migrateLegacyShapeIfNeeded(preferences)
            // Keep the legacy key in sync for older app versions.
            preferences[AVATAR_SHAPE_KEY] = shape.storedValue
            preferences[PLAIN_SHAPE_KEY] = shape.storedValue
        }
    }

    suspend fun updateFramedShape(shape: SteamAvatarShapeOption) {
        dataStore.edit { preferences ->
            migrateLegacyShapeIfNeeded(preferences)
            preferences[FRAMED_SHAPE_KEY] = shape.storedValue
        }
    }

    private fun Preferences.readShape(key: Preferences.Key<String>): SteamAvatarShapeOption {
        return SteamAvatarShapeOption.fromStoredValue(this[key] ?: this[AVATAR_SHAPE_KEY])
    }

    /**
     * Materialize both new keys the first time either setting is changed.
     * This preserves the old value for the untouched setting while allowing
     * the two controls to diverge afterwards.
     */
    private fun migrateLegacyShapeIfNeeded(preferences: MutablePreferences) {
        val legacyValue = preferences[AVATAR_SHAPE_KEY]
        val legacyShape = SteamAvatarShapeOption.fromStoredValue(legacyValue).storedValue
        if (preferences[PLAIN_SHAPE_KEY] == null) {
            preferences[PLAIN_SHAPE_KEY] = legacyShape
        }
        if (preferences[FRAMED_SHAPE_KEY] == null) {
            preferences[FRAMED_SHAPE_KEY] = legacyShape
        }
    }

    private companion object {
        val AVATAR_SHAPE_KEY = stringPreferencesKey("avatar_shape")
        val PLAIN_SHAPE_KEY = stringPreferencesKey("plain_avatar_shape")
        val FRAMED_SHAPE_KEY = stringPreferencesKey("framed_avatar_shape")
    }
}
