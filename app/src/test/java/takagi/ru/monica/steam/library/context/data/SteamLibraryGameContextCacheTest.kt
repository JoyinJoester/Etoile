package takagi.ru.monica.steam.library.context.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcOwnership
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext
import takagi.ru.monica.steam.library.context.domain.mergeSteamLibraryGameContext
import takagi.ru.monica.steam.library.context.domain.steamLibraryGameContextIsCacheable

class SteamLibraryGameContextCacheTest {
    @Test
    fun cacheRoundTripsAndIsolatesSteamIdAndAppId() {
        val store = MemoryStore()
        val cache = SteamLibraryGameContextPreferencesCache(store)
        val context = completeContext()

        cache.save(context)

        assertEquals(context, cache.load(ACCOUNT_A, 620))
        assertNull(cache.load(ACCOUNT_A, 730))
        assertNull(cache.load(ACCOUNT_B, 620))
        assertTrue(store.values.keys.none { it.contains(ACCOUNT_A) })
    }

    @Test
    fun failedRefreshKeepsUsefulCachedCloudAndDlcOwnership() {
        val cached = completeContext()
        val fresh = cached.copy(
            cloud = SteamLibraryCloudContext(
                status = SteamLibraryCloudStatus.UNKNOWN,
                failure = SteamLibraryFailureReason.NETWORK
            ),
            dlc = listOf(
                cached.dlc.single().copy(
                    name = "DLC #621",
                    headerImageUrl = "",
                    ownership = SteamLibraryDlcOwnership.UNKNOWN
                )
            ),
            dlcMetadataFailure = SteamLibraryFailureReason.NETWORK,
            dlcOwnershipFailure = SteamLibraryFailureReason.NETWORK,
            fetchedAt = 200L
        )

        val merged = mergeSteamLibraryGameContext(fresh, cached)

        assertTrue(merged.usedCache)
        assertEquals(SteamLibraryCloudStatus.AVAILABLE, merged.context.cloud.status)
        assertEquals(SteamLibraryFailureReason.NETWORK, merged.context.cloud.failure)
        assertEquals("DLC", merged.context.dlc.single().name)
        assertEquals("https://cdn.example/dlc.jpg", merged.context.dlc.single().headerImageUrl)
        assertEquals(SteamLibraryDlcOwnership.OWNED, merged.context.dlc.single().ownership)
        assertFalse(steamLibraryGameContextIsCacheable(fresh))
        assertTrue(steamLibraryGameContextIsCacheable(cached))
    }

    private fun completeContext() = SteamLibraryGameContext(
        accountSteamId = ACCOUNT_A,
        appId = 620,
        ownership = SteamGameOwnership.OWNED,
        supportsSteamCloud = true,
        cloud = SteamLibraryCloudContext(
            status = SteamLibraryCloudStatus.AVAILABLE,
            fileCount = 2,
            totalBytes = 150L
        ),
        dlc = listOf(
            SteamLibraryDlcContext(
                appId = 621,
                name = "DLC",
                headerImageUrl = "https://cdn.example/dlc.jpg",
                ownership = SteamLibraryDlcOwnership.OWNED
            )
        ),
        fetchedAt = 100L
    )

    private class MemoryStore : SteamLibraryGameContextKeyValueStore {
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
