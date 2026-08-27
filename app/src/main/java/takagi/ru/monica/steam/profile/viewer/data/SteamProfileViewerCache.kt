package takagi.ru.monica.steam.profile.viewer.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparison
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot

internal interface SteamProfileViewerCache {
    fun loadProfile(viewerSteamId: String, targetSteamId: String): SteamProfileViewerSnapshot?
    fun saveProfile(snapshot: SteamProfileViewerSnapshot)
    fun loadAchievements(
        viewerSteamId: String,
        targetSteamId: String,
        appId: Int
    ): SteamAchievementComparison?
    fun saveAchievements(comparison: SteamAchievementComparison)
}

internal class SteamProfileViewerPreferencesCache(context: Context) : SteamProfileViewerCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        "steam_profile_viewer_cache",
        Context.MODE_PRIVATE
    )

    override fun loadProfile(
        viewerSteamId: String,
        targetSteamId: String
    ): SteamProfileViewerSnapshot? = preferences
        .getString(profileKey(viewerSteamId, targetSteamId), null)
        ?.let(SteamProfileViewerCacheCodec::decodeProfile)
        ?.takeIf { it.viewerSteamId == viewerSteamId && it.target.steamId == targetSteamId }

    override fun saveProfile(snapshot: SteamProfileViewerSnapshot) {
        preferences.edit().putString(
            profileKey(snapshot.viewerSteamId, snapshot.target.steamId),
            SteamProfileViewerCacheCodec.encodeProfileForStorage(snapshot)
        ).apply()
    }

    override fun loadAchievements(
        viewerSteamId: String,
        targetSteamId: String,
        appId: Int
    ): SteamAchievementComparison? = preferences
        .getString(achievementKey(viewerSteamId, targetSteamId, appId), null)
        ?.let(SteamProfileViewerCacheCodec::decodeAchievements)
        ?.takeIf {
            it.viewerSteamId == viewerSteamId &&
                it.targetSteamId == targetSteamId &&
                it.appId == appId
        }

    override fun saveAchievements(comparison: SteamAchievementComparison) {
        preferences.edit().putString(
            achievementKey(
                comparison.viewerSteamId,
                comparison.targetSteamId,
                comparison.appId
            ),
            SteamProfileViewerCacheCodec.encodeAchievements(comparison)
        ).apply()
    }

    private fun profileKey(viewerSteamId: String, targetSteamId: String): String =
        "profile_${digest("$viewerSteamId|$targetSteamId")}"

    private fun achievementKey(
        viewerSteamId: String,
        targetSteamId: String,
        appId: Int
    ): String = "achievements_${digest("$viewerSteamId|$targetSteamId|$appId")}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal object SteamProfileViewerCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeProfile(snapshot: SteamProfileViewerSnapshot): String =
        json.encodeToString(SteamProfileViewerSnapshot.serializer(), snapshot)

    fun encodeProfileForStorage(snapshot: SteamProfileViewerSnapshot): String = encodeProfile(
        if (snapshot.badges.size <= MAX_CACHED_BADGES) {
            snapshot
        } else {
            snapshot.copy(badges = snapshot.badges.take(MAX_CACHED_BADGES))
        }
    )

    fun decodeProfile(raw: String): SteamProfileViewerSnapshot? = runCatching {
        json.decodeFromString(SteamProfileViewerSnapshot.serializer(), raw)
    }.getOrNull()

    fun encodeAchievements(comparison: SteamAchievementComparison): String =
        json.encodeToString(SteamAchievementComparison.serializer(), comparison)

    fun decodeAchievements(raw: String): SteamAchievementComparison? = runCatching {
        json.decodeFromString(SteamAchievementComparison.serializer(), raw)
    }.getOrNull()

    private const val MAX_CACHED_BADGES = 250
}
