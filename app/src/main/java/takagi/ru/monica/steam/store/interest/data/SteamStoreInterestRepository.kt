package takagi.ru.monica.steam.store.interest.data

import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreRecord
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestAccount
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSnapshot
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSyncResult
import takagi.ru.monica.steam.store.interest.domain.reconcileSteamStoreInterest

internal interface SteamStoreInterestRemoteDataSource {
    fun ignoredAppIds(
        account: SteamStoreInterestAccount,
        forceRefresh: Boolean = false
    ): Set<Int>

    fun isIgnored(appId: Int, account: SteamStoreInterestAccount): Boolean

    fun setIgnored(
        appId: Int,
        ignored: Boolean,
        account: SteamStoreInterestAccount
    )
}

internal class SteamStoreInterestRepository(
    private val local: SteamStoreInterestLocalDataSource,
    private val remote: SteamStoreInterestRemoteDataSource,
    private val syncSettings: SteamStoreInterestSyncSettings,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()

    fun applyLocal(
        steamId: String,
        appId: Int,
        ignored: Boolean
    ): SteamStoreIgnoreSyncState {
        require(steamId.isNotBlank()) { "Steam ID is required" }
        require(appId > 0) { "invalid Steam app id" }
        val syncState = if (syncSettings.syncWithSteam) {
            SteamStoreIgnoreSyncState.PENDING
        } else {
            SteamStoreIgnoreSyncState.LOCAL_ONLY
        }
        synchronized(lock) {
            val snapshot = local.load(steamId).upsert(
                SteamStoreIgnoreRecord(
                    appId = appId,
                    ignored = ignored,
                    updatedAt = nowMillis(),
                    syncState = syncState
                )
            )
            local.save(steamId, snapshot)
        }
        return syncState
    }

    fun localIgnoredAppIds(steamId: String): Set<Int> = synchronized(lock) {
        local.load(steamId).ignoredAppIds
    }

    fun localIgnoredState(steamId: String, appId: Int): Boolean? = synchronized(lock) {
        local.load(steamId).record(appId)?.ignored
    }

    fun syncState(steamId: String, appId: Int): SteamStoreIgnoreSyncState? =
        synchronized(lock) {
            local.load(steamId).record(appId)?.syncState?.let { state ->
                if (!syncSettings.syncWithSteam && state == SteamStoreIgnoreSyncState.PENDING) {
                    SteamStoreIgnoreSyncState.LOCAL_ONLY
                } else {
                    state
                }
            }
        }

    fun syncPending(account: SteamStoreInterestAccount): SteamStoreInterestSyncResult {
        if (!syncSettings.syncWithSteam) {
            return SteamStoreInterestSyncResult(pendingAppIds = emptySet())
        }
        val candidates = synchronized(lock) {
            val snapshot = local.load(account.steamId)
            val prepared = snapshot.copy(
                records = snapshot.records.map { record ->
                    if (record.syncState == SteamStoreIgnoreSyncState.LOCAL_ONLY) {
                        record.copy(syncState = SteamStoreIgnoreSyncState.PENDING)
                    } else {
                        record
                    }
                }
            )
            if (prepared != snapshot) local.save(account.steamId, prepared)
            prepared.records.filter { it.syncState == SteamStoreIgnoreSyncState.PENDING }
        }
        candidates.forEach { candidate ->
            val synced = runCatching {
                remote.setIgnored(
                    appId = candidate.appId,
                    ignored = candidate.ignored,
                    account = account
                )
            }.isSuccess
            if (synced) {
                synchronized(lock) {
                    val latest = local.load(account.steamId)
                    val current = latest.record(candidate.appId)
                    if (current == candidate) {
                        local.save(
                            account.steamId,
                            if (candidate.ignored) {
                                latest.upsert(
                                    candidate.copy(
                                        syncState = SteamStoreIgnoreSyncState.SYNCED
                                    )
                                )
                            } else {
                                latest.remove(candidate.appId)
                            }
                        )
                    }
                }
            }
        }
        return SteamStoreInterestSyncResult(
            pendingAppIds = synchronized(lock) {
                local.load(account.steamId).records.asSequence()
                    .filter { it.syncState == SteamStoreIgnoreSyncState.PENDING }
                    .map(SteamStoreIgnoreRecord::appId)
                    .toCollection(linkedSetOf())
            }
        )
    }

    fun ignoredAppIds(
        account: SteamStoreInterestAccount,
        forceRefresh: Boolean = false
    ): Set<Int> {
        if (!syncSettings.syncWithSteam) {
            return localIgnoredAppIds(account.steamId)
        }
        syncPending(account)
        val official = runCatching {
            remote.ignoredAppIds(account, forceRefresh)
        }.getOrElse {
            return localIgnoredAppIds(account.steamId)
        }
        return synchronized(lock) {
            val reconciled = reconcileSteamStoreInterest(
                local = local.load(account.steamId),
                officialIgnoredAppIds = official,
                nowMillis = nowMillis()
            )
            local.save(account.steamId, reconciled)
            reconciled.ignoredAppIds
        }
    }
}
