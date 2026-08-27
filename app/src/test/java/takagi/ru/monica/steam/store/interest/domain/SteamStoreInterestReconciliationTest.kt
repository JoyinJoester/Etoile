package takagi.ru.monica.steam.store.interest.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamStoreInterestReconciliationTest {
    @Test
    fun pendingAndLocalOnlyChoicesOverrideOlderOfficialState() {
        val local = SteamStoreInterestSnapshot(
            records = listOf(
                record(10, ignored = true, SteamStoreIgnoreSyncState.PENDING),
                record(20, ignored = false, SteamStoreIgnoreSyncState.PENDING),
                record(30, ignored = true, SteamStoreIgnoreSyncState.SYNCED),
                record(50, ignored = true, SteamStoreIgnoreSyncState.LOCAL_ONLY)
            )
        )

        val reconciled = reconcileSteamStoreInterest(
            local = local,
            officialIgnoredAppIds = setOf(20, 40),
            nowMillis = 500L
        )

        assertEquals(setOf(10, 40, 50), reconciled.ignoredAppIds)
        assertEquals(SteamStoreIgnoreSyncState.PENDING, reconciled.record(10)?.syncState)
        assertEquals(false, reconciled.record(20)?.ignored)
        assertNull(reconciled.record(30))
        assertEquals(SteamStoreIgnoreSyncState.SYNCED, reconciled.record(40)?.syncState)
        assertEquals(SteamStoreIgnoreSyncState.LOCAL_ONLY, reconciled.record(50)?.syncState)
    }

    private fun record(
        appId: Int,
        ignored: Boolean,
        syncState: SteamStoreIgnoreSyncState
    ) = SteamStoreIgnoreRecord(
        appId = appId,
        ignored = ignored,
        updatedAt = 100L,
        syncState = syncState
    )
}
