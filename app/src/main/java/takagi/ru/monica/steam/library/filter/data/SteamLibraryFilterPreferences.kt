package takagi.ru.monica.steam.library.filter.data

import android.content.Context
import android.content.SharedPreferences
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryAchievementStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryOwnershipFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlayStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlaytimeFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder

internal class SteamLibraryFilterPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(accountId: Long?): SteamLibraryFilterSelection {
        val prefix = keyPrefix(accountId)
        val storedValues = SteamLibraryFilterStoredValues(
            ownership = preferences.getString(prefix + KEY_OWNERSHIP, null),
            playStatus = preferences.getString(prefix + KEY_PLAY_STATUS, null),
            achievementStatus = preferences.getString(prefix + KEY_ACHIEVEMENT_STATUS, null),
            playtime = preferences.getString(prefix + KEY_PLAYTIME, null),
            sortOrder = preferences.getString(prefix + KEY_SORT_ORDER, null),
            requiresSteamCloud = preferences.getString(prefix + KEY_STEAM_CLOUD, null)
        )
        return decodeSteamLibraryFilterSelection(
            values = storedValues,
            legacyFilterName = preferences.getString(LEGACY_KEY_FILTER, null)
        )
    }

    fun save(accountId: Long?, selection: SteamLibraryFilterSelection) {
        val prefix = keyPrefix(accountId)
        val values = encodeSteamLibraryFilterSelection(selection)
        preferences.edit()
            .putString(prefix + KEY_OWNERSHIP, values.ownership)
            .putString(prefix + KEY_PLAY_STATUS, values.playStatus)
            .putString(prefix + KEY_ACHIEVEMENT_STATUS, values.achievementStatus)
            .putString(prefix + KEY_PLAYTIME, values.playtime)
            .putString(prefix + KEY_SORT_ORDER, values.sortOrder)
            .putString(prefix + KEY_STEAM_CLOUD, values.requiresSteamCloud)
            .apply()
    }

    private fun keyPrefix(accountId: Long?): String = "account_${accountId ?: 0L}_"

    private companion object {
        const val PREFERENCES_NAME = "steam_library_preferences"
        const val LEGACY_KEY_FILTER = "game_filter"
        const val KEY_OWNERSHIP = "ownership"
        const val KEY_PLAY_STATUS = "play_status"
        const val KEY_ACHIEVEMENT_STATUS = "achievement_status"
        const val KEY_PLAYTIME = "playtime"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_STEAM_CLOUD = "steam_cloud"
    }
}

internal data class SteamLibraryFilterStoredValues(
    val ownership: String? = null,
    val playStatus: String? = null,
    val achievementStatus: String? = null,
    val playtime: String? = null,
    val sortOrder: String? = null,
    val requiresSteamCloud: String? = null
) {
    val hasStoredSelection: Boolean
        get() = ownership != null || playStatus != null || achievementStatus != null ||
            playtime != null || sortOrder != null || requiresSteamCloud != null
}

internal fun encodeSteamLibraryFilterSelection(
    selection: SteamLibraryFilterSelection
): SteamLibraryFilterStoredValues = SteamLibraryFilterStoredValues(
    ownership = selection.ownership.name,
    playStatus = selection.playStatus.name,
    achievementStatus = selection.achievementStatus.name,
    playtime = selection.playtime.name,
    sortOrder = selection.sortOrder.name,
    requiresSteamCloud = selection.requiresSteamCloud.toString()
)

internal fun decodeSteamLibraryFilterSelection(
    values: SteamLibraryFilterStoredValues,
    legacyFilterName: String? = null
): SteamLibraryFilterSelection {
    if (!values.hasStoredSelection) return legacySelection(legacyFilterName)
    return SteamLibraryFilterSelection(
        ownership = enumValueOrDefault(values.ownership, SteamLibraryOwnershipFilter.ALL),
        playStatus = enumValueOrDefault(values.playStatus, SteamLibraryPlayStatusFilter.ALL),
        achievementStatus = enumValueOrDefault(
            values.achievementStatus,
            SteamLibraryAchievementStatusFilter.ALL
        ),
        playtime = enumValueOrDefault(values.playtime, SteamLibraryPlaytimeFilter.ANY),
        sortOrder = enumValueOrDefault(values.sortOrder, SteamLibrarySortOrder.SMART),
        requiresSteamCloud = values.requiresSteamCloud?.toBooleanStrictOrNull() ?: false
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private fun legacySelection(name: String?): SteamLibraryFilterSelection = when (name) {
    "UNPLAYED" -> SteamLibraryFilterSelection(
        playStatus = SteamLibraryPlayStatusFilter.UNPLAYED
    )
    "RECENT" -> SteamLibraryFilterSelection(
        playStatus = SteamLibraryPlayStatusFilter.RECENT
    )
    "PERFECT" -> SteamLibraryFilterSelection(
        achievementStatus = SteamLibraryAchievementStatusFilter.PERFECT
    )
    "FAMILY_SHARED" -> SteamLibraryFilterSelection(
        ownership = SteamLibraryOwnershipFilter.FAMILY_SHARED
    )
    "STEAM_CLOUD" -> SteamLibraryFilterSelection(requiresSteamCloud = true)
    else -> SteamLibraryFilterSelection()
}
