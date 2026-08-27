package takagi.ru.monica.steam.foundation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SteamRemoteImageCachePolicyTest {
    @Test
    fun sha256NamesAvoidLegacyJavaHashCollisions() {
        val first = "https://steamstatic.com/Aa"
        val second = "https://steamstatic.com/BB"
        assertEquals(first.hashCode(), second.hashCode())

        assertNotEquals(steamRemoteImageCacheKey(first), steamRemoteImageCacheKey(second))
        assertEquals(64, steamRemoteImageCacheKey(first).length)
    }

    @Test
    fun pruningRemovesTemporaryOversizedExpiredAndOldestEntries() {
        val now = 10_000L
        val evictions = steamRemoteImageCacheEvictions(
            entries = listOf(
                SteamRemoteImageCacheEntry("temporary.tmp", 10L, now, temporary = true),
                SteamRemoteImageCacheEntry("oversized.bin", 101L, now),
                SteamRemoteImageCacheEntry("expired.bin", 20L, 1L),
                SteamRemoteImageCacheEntry("old.bin", 40L, 8_000L),
                SteamRemoteImageCacheEntry("new.bin", 40L, 9_000L),
                SteamRemoteImageCacheEntry("protected.bin", 40L, 7_000L)
            ),
            protectedName = "protected.bin",
            nowMillis = now,
            maximumCacheBytes = 80L,
            maximumEntryBytes = 100L,
            ttlMillis = 5_000L
        )

        assertEquals(
            setOf("temporary.tmp", "oversized.bin", "expired.bin", "old.bin"),
            evictions
        )
        assertFalse("protected.bin" in evictions)
    }
}
