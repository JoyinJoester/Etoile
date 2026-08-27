package takagi.ru.monica.steam.friends.voice.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.cm.SteamCmResponseTimeoutException

class SteamVoiceSignalingRetryPolicyTest {
    @Test
    fun transientCmFailuresRetryWithBoundedBackoff() = runTest {
        val slept = mutableListOf<Long>()
        val retries = mutableListOf<SteamVoiceSignalingRetry>()
        var calls = 0
        val retrier = SteamVoiceSignalingRetrier(
            retryDelaysMillis = longArrayOf(100L, 300L),
            sleeper = { slept += it }
        )

        val value = retrier.execute(onRetry = retries::add) {
            calls++
            if (calls < 3) throw IOException("temporary CM failure")
            "connected"
        }

        assertEquals("connected", value)
        assertEquals(3, calls)
        assertEquals(listOf(100L, 300L), slept)
        assertEquals(listOf(2, 3), retries.map(SteamVoiceSignalingRetry::attempt))
    }

    @Test
    fun authenticationAndValidationFailuresAreNotRetried() {
        var calls = 0
        val retrier = SteamVoiceSignalingRetrier(
            retryDelaysMillis = longArrayOf(1L),
            sleeper = {}
        )

        assertThrows(SteamApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                retrier.execute {
                    calls++
                    throw SteamApiException("expired", httpStatusCode = 401)
                }
            }
        }
        assertEquals(1, calls)
        assertFalse(IllegalArgumentException("invalid SDP").isRetryableSteamVoiceSignalingFailure())
        assertTrue(IOException("network").isRetryableSteamVoiceSignalingFailure())
        assertTrue(
            IOException("Steam CM is unavailable", IOException("socket reset"))
                .isRetryableSteamVoiceSignalingFailure()
        )
    }

    @Test
    fun responseTimeoutIsNotRetriedBecauseTheMutationMayHaveReachedSteam() {
        val timeout = SteamCmResponseTimeoutException("response timed out")

        assertFalse(timeout.isRetryableSteamVoiceSignalingFailure())
        assertFalse(
            IOException("Steam CM is unavailable", timeout)
                .isRetryableSteamVoiceSignalingFailure()
        )
    }

    @Test
    fun cancellationIsNeverConvertedIntoARetry() {
        val retrier = SteamVoiceSignalingRetrier(
            retryDelaysMillis = longArrayOf(1L),
            sleeper = {}
        )

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                retrier.execute { throw CancellationException("stopped") }
            }
        }
    }
}
