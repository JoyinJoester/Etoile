package takagi.ru.monica.steam.store.interest.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SteamStoreIgnoreSyncState {
    SYNCED,
    PENDING,
    LOCAL_ONLY
}

@Serializable
data class SteamStoreIgnoreRecord(
    val appId: Int,
    val ignored: Boolean,
    val updatedAt: Long,
    val syncState: SteamStoreIgnoreSyncState
)

@Serializable
data class SteamStoreInterestSnapshot(
    val records: List<SteamStoreIgnoreRecord> = emptyList()
) {
    val ignoredAppIds: Set<Int>
        get() = records.asSequence()
            .filter(SteamStoreIgnoreRecord::ignored)
            .map(SteamStoreIgnoreRecord::appId)
            .toCollection(linkedSetOf())

    fun record(appId: Int): SteamStoreIgnoreRecord? =
        records.firstOrNull { it.appId == appId }

    fun upsert(record: SteamStoreIgnoreRecord): SteamStoreInterestSnapshot = copy(
        records = (records.filterNot { it.appId == record.appId } + record)
            .sortedBy(SteamStoreIgnoreRecord::appId)
    )

    fun remove(appId: Int): SteamStoreInterestSnapshot = copy(
        records = records.filterNot { it.appId == appId }
    )
}

data class SteamStoreInterestAccount(
    val steamId: String,
    val steamLoginSecure: String?,
    val accessToken: String?,
    val countryCode: String
)

data class SteamStoreInterestSyncResult(
    val pendingAppIds: Set<Int>
)

fun reconcileSteamStoreInterest(
    local: SteamStoreInterestSnapshot,
    officialIgnoredAppIds: Set<Int>,
    nowMillis: Long
): SteamStoreInterestSnapshot {
    val localOverrides = local.records
        .filter { it.syncState != SteamStoreIgnoreSyncState.SYNCED }
        .associateBy(SteamStoreIgnoreRecord::appId)
    val official = officialIgnoredAppIds.asSequence()
        .filter { it > 0 && it !in localOverrides }
        .map { appId ->
            SteamStoreIgnoreRecord(
                appId = appId,
                ignored = true,
                updatedAt = nowMillis,
                syncState = SteamStoreIgnoreSyncState.SYNCED
            )
        }
        .toList()
    return SteamStoreInterestSnapshot(
        records = (official + localOverrides.values)
            .sortedBy(SteamStoreIgnoreRecord::appId)
    )
}
