package takagi.ru.monica.steam.network.optimization

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamHostSessionStatsTrackerTest {
    @Test
    fun recordsPerHostAndTotalHitsWithoutPersistenceWrites() {
        var now = 100L
        val tracker = SteamHostSessionStatsTracker(clock = { now })

        tracker.record("store.steampowered.com")
        now = 200L
        tracker.record("store.steampowered.com")
        tracker.record("steamcommunity.com")

        val stats = tracker.stats.value
        assertEquals(3L, stats.totalHitCount)
        assertEquals(2L, stats.hosts.getValue("store.steampowered.com").hitCount)
        assertEquals(200L, stats.hosts.getValue("store.steampowered.com").lastHitAtEpochMillis)
        assertEquals(1L, stats.hosts.getValue("steamcommunity.com").hitCount)
    }
}
