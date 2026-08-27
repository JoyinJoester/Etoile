package takagi.ru.monica.steam.friends.voice.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

class SteamVoiceMediaRecoveryPolicyTest {
    @Test
    fun speakingWithoutOutboundProgressRequestsMediaRecovery() {
        val monitor = SteamVoiceMediaHealthMonitor(stallMillis = 15_000L)

        assertEquals(
            SteamVoiceMediaHealth.HEALTHY,
            monitor.observe(sample(packets = 10, bytes = 1_000, level = 0.08), 0L, false)
        )
        assertEquals(
            SteamVoiceMediaHealth.HEALTHY,
            monitor.observe(sample(packets = 10, bytes = 1_000, level = 0.08), 10_000L, false)
        )
        assertEquals(
            SteamVoiceMediaHealth.OUTBOUND_STALLED,
            monitor.observe(sample(packets = 10, bytes = 1_000, level = 0.08), 25_000L, false)
        )
    }

    @Test
    fun silenceAndIntentionalMuteDoNotTriggerFalseRecovery() {
        val silent = SteamVoiceMediaHealthMonitor(stallMillis = 10_000L)
        silent.observe(sample(packets = 10, bytes = 1_000, level = 0.0), 0L, false)
        assertEquals(
            SteamVoiceMediaHealth.HEALTHY,
            silent.observe(sample(packets = 10, bytes = 1_000, level = 0.0), 60_000L, false)
        )

        val muted = SteamVoiceMediaHealthMonitor(stallMillis = 10_000L)
        muted.observe(sample(packets = 10, bytes = 1_000, level = 0.2), 0L, true)
        assertEquals(
            SteamVoiceMediaHealth.HEALTHY,
            muted.observe(sample(packets = 10, bytes = 1_000, level = 0.2), 60_000L, true)
        )
    }

    @Test
    fun missingTrackAndRevokedPermissionAreReportedImmediately() {
        val monitor = SteamVoiceMediaHealthMonitor()

        assertEquals(
            SteamVoiceMediaHealth.TRACK_MISSING,
            monitor.observe(sample(readyState = "missing"), 0L, false)
        )
        assertEquals(
            SteamVoiceMediaHealth.PERMISSION_REVOKED,
            monitor.observe(sample(permission = "denied"), 1L, false)
        )
    }

    @Test
    fun recoveryBudgetStopsCrashLoopsAndExpiresOldAttempts() {
        val budget = SteamVoiceRecoveryBudget(
            maxAttempts = 3,
            windowMillis = 120_000L,
            retryDelaysMillis = longArrayOf(0L, 1_000L, 3_000L)
        )

        assertEquals(0L, budget.request(0L)?.delayMillis)
        assertEquals(1_000L, budget.request(1_000L)?.delayMillis)
        assertEquals(3_000L, budget.request(2_000L)?.delayMillis)
        assertEquals(null, budget.request(3_000L))
        assertEquals(0L, budget.request(121_000L)?.delayMillis)
    }

    @Test
    fun mediaStatsParserAcceptsTheWebViewPayload() {
        val parsed = SteamVoiceMediaSample.parse(
            """{"bytes":42,"packets":7,"audioLevel":0.15,"enabled":true,"muted":false,"readyState":"live","permission":"granted"}"""
        )

        assertNotNull(parsed)
        assertEquals(42L, parsed?.bytesSent)
        assertEquals(7L, parsed?.packetsSent)
        assertEquals("granted", parsed?.permission)
    }

    @Test
    fun directCallCannotBecomeConnectedBeforeTheRemoteUserAccepts() {
        assertFalse(
            isSteamVoiceMediaConnected(
                targetType = SteamVoiceTargetType.DIRECT,
                voiceChatId = "voice-1",
                iceConnected = true,
                webRtcUpdated = true,
                directAccepted = false,
                localMediaReady = true
            )
        )
        assertTrue(
            isSteamVoiceMediaConnected(
                targetType = SteamVoiceTargetType.DIRECT,
                voiceChatId = "voice-1",
                iceConnected = true,
                webRtcUpdated = true,
                directAccepted = true,
                localMediaReady = true
            )
        )
        assertTrue(
            isSteamVoiceMediaConnected(
                targetType = SteamVoiceTargetType.GROUP,
                voiceChatId = "voice-1",
                iceConnected = true,
                webRtcUpdated = true,
                directAccepted = false,
                localMediaReady = true
            )
        )
        assertFalse(
            isSteamVoiceMediaConnected(
                targetType = SteamVoiceTargetType.GROUP,
                voiceChatId = "voice-1",
                iceConnected = true,
                webRtcUpdated = true,
                directAccepted = false,
                localMediaReady = false
            )
        )
    }

    private fun sample(
        packets: Long = 1L,
        bytes: Long = 1L,
        level: Double = 0.0,
        enabled: Boolean = true,
        muted: Boolean = false,
        readyState: String = "live",
        permission: String = "granted"
    ) = SteamVoiceMediaSample(
        bytesSent = bytes,
        packetsSent = packets,
        audioLevel = level,
        enabled = enabled,
        muted = muted,
        readyState = readyState,
        permission = permission
    )
}
