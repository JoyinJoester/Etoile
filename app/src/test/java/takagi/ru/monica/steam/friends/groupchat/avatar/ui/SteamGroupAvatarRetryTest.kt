package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamGroupAvatarRetryTest {
    @Test
    fun retriesWhileSteamCdnPublishesANewAvatar() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = loadSteamGroupAvatarWithRetry(
            retryDelaysMillis = listOf(0L, 100L, 200L),
            delayBlock = { delays += it }
        ) {
            attempts++
            "avatar".takeIf { attempts == 3 }
        }

        assertEquals("avatar", result)
        assertEquals(3, attempts)
        assertEquals(listOf(100L, 200L), delays)
    }
}
