package takagi.ru.monica.steam.store.purchase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext

class SteamStorePurchaseContextCacheTest {
    @Test
    fun cacheRoundTripsAndIsolatesSteamIdAndAppId() {
        val store = MemoryStore()
        val cache = SteamStorePurchasePreferencesCache(store)
        val context = SteamStorePurchaseContext(
            accountSteamId = ACCOUNT_A,
            appId = 620,
            ownership = SteamStoreOwnershipStatus.FAMILY_SHARED,
            familyGroupId = 42L,
            ownerSteamIds = listOf(ACCOUNT_B),
            fetchedAt = 99L
        )

        cache.save(context)

        assertEquals(context, cache.load(ACCOUNT_A, 620))
        assertNull(cache.load(ACCOUNT_A, 730))
        assertNull(cache.load(ACCOUNT_B, 620))
        assertTrue(store.values.keys.none { it.contains(ACCOUNT_A) })
    }

    private class MemoryStore : SteamStorePurchaseKeyValueStore {
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
