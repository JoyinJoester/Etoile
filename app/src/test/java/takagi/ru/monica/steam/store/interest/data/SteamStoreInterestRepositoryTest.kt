package takagi.ru.monica.steam.store.interest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestAccount
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSnapshot

class SteamStoreInterestRepositoryTest {
    @Test
    fun localMutationSurvivesSteamFailureAndSyncsOnLaterRetry() {
        val local = MemoryLocalDataSource()
        val remote = FakeRemoteDataSource().apply { failMutations = true }
        val settings = FakeSyncSettings(syncWithSteam = true)
        val repository = SteamStoreInterestRepository(
            local = local,
            remote = remote,
            syncSettings = settings,
            nowMillis = { 100L }
        )

        assertEquals(
            SteamStoreIgnoreSyncState.PENDING,
            repository.applyLocal(ACCOUNT_ID, appId = 730, ignored = true)
        )
        assertTrue(repository.localIgnoredAppIds(ACCOUNT_ID).contains(730))

        val failed = repository.syncPending(account())

        assertEquals(setOf(730), failed.pendingAppIds)
        assertTrue(repository.localIgnoredAppIds(ACCOUNT_ID).contains(730))

        remote.failMutations = false
        val synced = repository.syncPending(account())

        assertTrue(synced.pendingAppIds.isEmpty())
        assertTrue(remote.ignoredAppIds.contains(730))
        assertEquals(
            SteamStoreIgnoreSyncState.SYNCED,
            repository.syncState(ACCOUNT_ID, 730)
        )
    }

    @Test
    fun localOnlyModeNeverRequiresSteam() {
        val local = MemoryLocalDataSource()
        val remote = FakeRemoteDataSource()
        val settings = FakeSyncSettings(syncWithSteam = false)
        val repository = SteamStoreInterestRepository(
            local = local,
            remote = remote,
            syncSettings = settings,
            nowMillis = { 100L }
        )

        assertEquals(
            SteamStoreIgnoreSyncState.LOCAL_ONLY,
            repository.applyLocal(ACCOUNT_ID, appId = 620, ignored = true)
        )

        assertEquals(setOf(620), repository.ignoredAppIds(account()))
        assertEquals(0, remote.readCount)
        assertEquals(0, remote.mutationCount)
    }

    @Test
    fun officialStateIsImportedWithoutReplacingPendingLocalUnignore() {
        val local = MemoryLocalDataSource()
        val remote = FakeRemoteDataSource().apply {
            ignoredAppIds += setOf(570, 730)
            failMutations = true
        }
        val repository = SteamStoreInterestRepository(
            local = local,
            remote = remote,
            syncSettings = FakeSyncSettings(syncWithSteam = true),
            nowMillis = { 100L }
        )
        repository.applyLocal(ACCOUNT_ID, appId = 730, ignored = false)

        val ignored = repository.ignoredAppIds(account(), forceRefresh = true)

        assertEquals(setOf(570), ignored)
        assertFalse(repository.localIgnoredAppIds(ACCOUNT_ID).contains(730))
        assertEquals(SteamStoreIgnoreSyncState.PENDING, repository.syncState(ACCOUNT_ID, 730))
    }

    private fun account() = SteamStoreInterestAccount(
        steamId = ACCOUNT_ID,
        steamLoginSecure = "$ACCOUNT_ID||token",
        accessToken = "token",
        countryCode = "CN"
    )

    private class MemoryLocalDataSource : SteamStoreInterestLocalDataSource {
        private val states = linkedMapOf<String, SteamStoreInterestSnapshot>()

        override fun load(steamId: String): SteamStoreInterestSnapshot =
            states[steamId] ?: SteamStoreInterestSnapshot()

        override fun save(steamId: String, snapshot: SteamStoreInterestSnapshot) {
            states[steamId] = snapshot
        }
    }

    private class FakeSyncSettings(
        override var syncWithSteam: Boolean
    ) : SteamStoreInterestSyncSettings

    private class FakeRemoteDataSource : SteamStoreInterestRemoteDataSource {
        val ignoredAppIds = linkedSetOf<Int>()
        var failMutations = false
        var readCount = 0
        var mutationCount = 0

        override fun ignoredAppIds(
            account: SteamStoreInterestAccount,
            forceRefresh: Boolean
        ): Set<Int> {
            readCount++
            return ignoredAppIds.toSet()
        }

        override fun isIgnored(appId: Int, account: SteamStoreInterestAccount): Boolean {
            readCount++
            return appId in ignoredAppIds
        }

        override fun setIgnored(
            appId: Int,
            ignored: Boolean,
            account: SteamStoreInterestAccount
        ) {
            mutationCount++
            if (failMutations) error("Steam unavailable")
            if (ignored) ignoredAppIds += appId else ignoredAppIds -= appId
        }
    }

    private companion object {
        const val ACCOUNT_ID = "76561198000000000"
    }
}
