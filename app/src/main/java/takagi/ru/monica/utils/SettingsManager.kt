package takagi.ru.monica.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Settings manager using DataStore
 */
class SettingsManager(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val OLED_PURE_BLACK_ENABLED_KEY = booleanPreferencesKey("oled_pure_black_enabled")
        private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme")
        private val DESIGN_STYLE_KEY = stringPreferencesKey("design_style")
        private val CUSTOM_PRIMARY_COLOR_KEY = longPreferencesKey("custom_primary_color")
        private val CUSTOM_SECONDARY_COLOR_KEY = longPreferencesKey("custom_secondary_color")
        private val CUSTOM_TERTIARY_COLOR_KEY = longPreferencesKey("custom_tertiary_color")
        private val CUSTOM_NEUTRAL_COLOR_KEY = longPreferencesKey("custom_neutral_color")
        private val CUSTOM_NEUTRAL_VARIANT_COLOR_KEY = longPreferencesKey("custom_neutral_variant_color")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val SCREENSHOT_PROTECTION_KEY = booleanPreferencesKey("screenshot_protection_enabled")

        private val sharedSettingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val sharedSettingsFlowLock = Any()

        @Volatile
        private var sharedSettingsFlow: SharedFlow<AppSettings>? = null
    }

    val settingsFlow: Flow<AppSettings> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        sharedSettingsFlow ?: synchronized(sharedSettingsFlowLock) {
            sharedSettingsFlow ?: dataStore.data
                .map { preferences -> mapPreferencesToAppSettings(preferences) }
                .distinctUntilChanged()
                .shareIn(
                    scope = sharedSettingsScope,
                    started = SharingStarted.Eagerly,
                    replay = 1
                )
                .also { sharedSettingsFlow = it }
        }
    }

    private fun mapPreferencesToAppSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            oledPureBlackEnabled = preferences[OLED_PURE_BLACK_ENABLED_KEY] ?: false,
            colorScheme = runCatching {
                ColorScheme.valueOf(preferences[COLOR_SCHEME_KEY] ?: ColorScheme.DEFAULT.name)
            }.getOrDefault(ColorScheme.DEFAULT),
            designStyle = runCatching {
                DesignStyle.valueOf(preferences[DESIGN_STYLE_KEY] ?: DesignStyle.MATERIAL.name)
            }.getOrDefault(DesignStyle.MATERIAL),
            customPrimaryColor = preferences[CUSTOM_PRIMARY_COLOR_KEY] ?: 0xFF6650a4,
            customSecondaryColor = preferences[CUSTOM_SECONDARY_COLOR_KEY] ?: 0xFF625b71,
            customTertiaryColor = preferences[CUSTOM_TERTIARY_COLOR_KEY] ?: 0xFF7D5260,
            customNeutralColor = preferences[CUSTOM_NEUTRAL_COLOR_KEY]
                ?: (preferences[CUSTOM_PRIMARY_COLOR_KEY] ?: 0xFF605D66),
            customNeutralVariantColor = preferences[CUSTOM_NEUTRAL_VARIANT_COLOR_KEY]
                ?: (preferences[CUSTOM_SECONDARY_COLOR_KEY] ?: 0xFF625B71),
            language = runCatching {
                Language.valueOf(preferences[LANGUAGE_KEY] ?: Language.SYSTEM.name)
            }.getOrDefault(Language.SYSTEM),
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION_KEY] ?: false
        )
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }

    suspend fun updateColorScheme(colorScheme: ColorScheme) {
        dataStore.edit { preferences ->
            preferences[COLOR_SCHEME_KEY] = colorScheme.name
        }
    }

    suspend fun updateDesignStyle(designStyle: DesignStyle) {
        dataStore.edit { preferences ->
            preferences[DESIGN_STYLE_KEY] = designStyle.name
        }
    }

    suspend fun updateLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.name
        }
    }
}
