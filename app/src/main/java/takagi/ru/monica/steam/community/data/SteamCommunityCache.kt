package takagi.ru.monica.steam.community.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot
import takagi.ru.monica.steam.community.eligibility.domain.CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockSource
import takagi.ru.monica.steam.community.eligibility.domain.withSteamLevelEvidence

interface SteamCommunityCache {
    fun load(accountSteamId: String): SteamCommunitySnapshot?
    fun save(snapshot: SteamCommunitySnapshot)
}

internal interface SteamCommunityKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SteamCommunityPreferencesCache internal constructor(
    private val store: SteamCommunityKeyValueStore
) : SteamCommunityCache {
    constructor(context: Context) : this(
        SteamCommunityPreferencesStore(context.applicationContext)
    )

    override fun load(accountSteamId: String): SteamCommunitySnapshot? =
        store.get(key(accountSteamId))
            ?.let(SteamCommunityCacheCodec::decode)
            ?.takeIf { it.accountSteamId == accountSteamId }
            ?.sanitizeLegacyEligibility()

    override fun save(snapshot: SteamCommunitySnapshot) {
        store.put(key(snapshot.accountSteamId), SteamCommunityCacheCodec.encode(snapshot))
    }

    private fun key(value: String): String = "community_" + MessageDigest.getInstance("SHA-256")
        .digest(value.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun SteamCommunitySnapshot.sanitizeLegacyEligibility(): SteamCommunitySnapshot {
    val sanitized = unlockProgress?.let { progress ->
        if (
            progress.status != SteamCommunityRestrictionStatus.UNRESTRICTED ||
            progress.evidenceRevision >= CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
        ) {
            progress
        } else {
            progress.copy(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                spentUsdCents = null,
                remainingUsdCents = progress.thresholdUsdCents,
                localRemainingMinor = progress.localThresholdMinor,
                exactProgress = false,
                suggestedGames = emptyList()
            )
        }
    }
    val resolved = sanitized.withSteamLevelEvidence(steamLevel)
    return if (resolved == unlockProgress) this else copy(unlockProgress = resolved)
}

private class SteamCommunityPreferencesStore(context: Context) : SteamCommunityKeyValueStore {
    private val preferences = context.getSharedPreferences(
        "steam_community_cache", Context.MODE_PRIVATE
    )

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

internal object SteamCommunityCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: SteamCommunitySnapshot) =
        json.encodeToString(SteamCommunitySnapshot.serializer(), snapshot)
    fun decode(raw: String): SteamCommunitySnapshot? = runCatching {
        json.decodeFromString(SteamCommunitySnapshot.serializer(), raw)
    }.getOrNull()
}
