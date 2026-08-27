package takagi.ru.monica.steam.store.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamStorageSource

class SteamStoreAccountLoadTrackerTest {
    @Test
    fun initializesOncePerAccountAndStorageContext() {
        val tracker = SteamStoreAccountLoadTracker()

        assertTrue(tracker.shouldInitialize(null, SteamStorageSource.Local))
        assertFalse(tracker.shouldInitialize(null, SteamStorageSource.Local))
        assertTrue(tracker.shouldInitialize(1L, SteamStorageSource.Local))
        assertFalse(tracker.shouldInitialize(1L, SteamStorageSource.Local))
        assertTrue(tracker.shouldInitialize(1L, SteamStorageSource.Mdbx(9L)))
    }
}
