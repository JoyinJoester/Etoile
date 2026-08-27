package takagi.ru.monica.steam.store.interest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreRecord
import takagi.ru.monica.steam.store.interest.domain.SteamStoreIgnoreSyncState
import takagi.ru.monica.steam.store.interest.domain.SteamStoreInterestSnapshot

class SteamStoreInterestPreferencesDataSourceTest {
    @Test
    fun persistedStateRoundTripsAndKeepsSteamIdOutOfStorageKeys() {
        val store = MemoryStore()
        val dataSource = SteamStoreInterestPreferencesDataSource(store)
        val snapshot = SteamStoreInterestSnapshot(
            records = listOf(
                SteamStoreIgnoreRecord(
                    appId = 730,
                    ignored = true,
                    updatedAt = 100L,
                    syncState = SteamStoreIgnoreSyncState.PENDING
                )
            )
        )

        dataSource.save(ACCOUNT_A, snapshot)

        assertEquals(snapshot, dataSource.load(ACCOUNT_A))
        assertEquals(SteamStoreInterestSnapshot(), dataSource.load(ACCOUNT_B))
        assertTrue(store.values.keys.none { it.contains(ACCOUNT_A) })
    }

    @Test
    fun syncPreferenceDefaultsOnAndCanBeDisabled() {
        val store = MemoryStore()
        val preferences = SteamStoreInterestPreferences(store)

        assertTrue(preferences.syncWithSteam)

        preferences.setSyncWithSteam(false)

        assertEquals(false, preferences.syncWithSteam)
    }

    private class MemoryStore : SteamStoreInterestKeyValueStore {
        val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private companion object {
        const val ACCOUNT_A = "76561198000000001"
        const val ACCOUNT_B = "76561198000000002"
    }
}
