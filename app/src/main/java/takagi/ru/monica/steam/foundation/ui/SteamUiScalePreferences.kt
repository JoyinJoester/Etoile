package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import takagi.ru.monica.data.InterfaceScale

internal fun calculateSteamUiDensity(
    baseDensity: Float,
    scalePercent: Int
): Float = InterfaceScale.calculateDensity(baseDensity, scalePercent)

private val Context.steamUiScaleDataStore by preferencesDataStore(
    name = "etoile_ui_scale"
)

class SteamUiScalePreferences(context: Context) {
    private val dataStore = context.applicationContext.steamUiScaleDataStore

    val scale: Flow<Int> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            InterfaceScale.normalizePercent(preferences[SCALE_PERCENT_KEY])
        }
        .distinctUntilChanged()

    suspend fun updateScale(scalePercent: Int) {
        dataStore.edit { preferences ->
            preferences[SCALE_PERCENT_KEY] = InterfaceScale.normalizePercent(scalePercent)
        }
    }

    private companion object {
        val SCALE_PERCENT_KEY = intPreferencesKey("ui_scale_percent")
    }
}
