package takagi.ru.monica.steam.friends.voice.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

internal data class SteamVoiceMediaSample(
    val bytesSent: Long,
    val packetsSent: Long,
    val audioLevel: Double,
    val enabled: Boolean,
    val muted: Boolean,
    val readyState: String,
    val permission: String
) {
    companion object {
        fun parse(raw: String): SteamVoiceMediaSample? = runCatching {
            val value = Json.parseToJsonElement(raw).jsonObject
            SteamVoiceMediaSample(
                bytesSent = value["bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                packetsSent = value["packets"]?.jsonPrimitive?.longOrNull ?: 0L,
                audioLevel = value["audioLevel"]?.jsonPrimitive?.doubleOrNull ?: -1.0,
                enabled = value["enabled"]?.jsonPrimitive?.booleanOrNull == true,
                muted = value["muted"]?.jsonPrimitive?.booleanOrNull == true,
                readyState = value["readyState"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                permission = value["permission"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.getOrNull()
    }
}

internal enum class SteamVoiceMediaHealth {
    HEALTHY,
    TRACK_MISSING,
    TRACK_ENDED,
    PERMISSION_REVOKED,
    OUTBOUND_STALLED
}

internal fun isSteamVoiceMediaConnected(
    targetType: SteamVoiceTargetType,
    voiceChatId: String,
    iceConnected: Boolean,
    webRtcUpdated: Boolean,
    directAccepted: Boolean,
    localMediaReady: Boolean
): Boolean = voiceChatId.isNotBlank() && iceConnected && webRtcUpdated && localMediaReady &&
    (targetType == SteamVoiceTargetType.GROUP || directAccepted)

/** Detects the one-way-audio case without treating silence or local mute as failure. */
internal class SteamVoiceMediaHealthMonitor(
    private val stallMillis: Long = DEFAULT_STALL_MILLIS,
    private val speakingLevelThreshold: Double = DEFAULT_SPEAKING_LEVEL_THRESHOLD
) {
    private var lastBytesSent: Long? = null
    private var lastPacketsSent: Long? = null
    private var speakingWithoutProgressSince: Long? = null

    fun observe(
        sample: SteamVoiceMediaSample,
        nowMillis: Long,
        microphoneMuted: Boolean
    ): SteamVoiceMediaHealth {
        if (sample.permission.equals("denied", ignoreCase = true)) {
            return SteamVoiceMediaHealth.PERMISSION_REVOKED
        }
        if (sample.readyState.equals("missing", ignoreCase = true) || sample.readyState.isBlank()) {
            return SteamVoiceMediaHealth.TRACK_MISSING
        }
        if (!sample.readyState.equals("live", ignoreCase = true)) {
            return SteamVoiceMediaHealth.TRACK_ENDED
        }

        val previousBytes = lastBytesSent
        val previousPackets = lastPacketsSent
        val countersReset = previousBytes != null && previousPackets != null &&
            (sample.bytesSent < previousBytes || sample.packetsSent < previousPackets)
        val progressed = previousBytes != null && previousPackets != null &&
            (sample.bytesSent > previousBytes || sample.packetsSent > previousPackets)
        lastBytesSent = sample.bytesSent
        lastPacketsSent = sample.packetsSent

        if (previousBytes == null || previousPackets == null || countersReset || progressed) {
            speakingWithoutProgressSince = if (
                !microphoneMuted && sample.enabled && !sample.muted &&
                sample.audioLevel >= speakingLevelThreshold
            ) nowMillis else null
            return SteamVoiceMediaHealth.HEALTHY
        }
        if (microphoneMuted || !sample.enabled || sample.muted) {
            speakingWithoutProgressSince = null
            return SteamVoiceMediaHealth.HEALTHY
        }
        if (sample.audioLevel < speakingLevelThreshold) {
            speakingWithoutProgressSince = null
            return SteamVoiceMediaHealth.HEALTHY
        }

        val stalledSince = speakingWithoutProgressSince ?: nowMillis.also {
            speakingWithoutProgressSince = it
        }
        return if (nowMillis - stalledSince >= stallMillis) {
            SteamVoiceMediaHealth.OUTBOUND_STALLED
        } else {
            SteamVoiceMediaHealth.HEALTHY
        }
    }

    fun reset() {
        lastBytesSent = null
        lastPacketsSent = null
        speakingWithoutProgressSince = null
    }

    private companion object {
        const val DEFAULT_STALL_MILLIS = 15_000L
        const val DEFAULT_SPEAKING_LEVEL_THRESHOLD = 0.01
    }
}

internal data class SteamVoiceRecoveryAttempt(
    val attempt: Int,
    val delayMillis: Long
)

/** Limits automatic media rebuilds so renderer or device failures cannot create a restart loop. */
internal class SteamVoiceRecoveryBudget(
    private val maxAttempts: Int = 3,
    private val windowMillis: Long = 120_000L,
    private val retryDelaysMillis: LongArray = longArrayOf(0L, 1_000L, 3_000L)
) {
    private val attempts = ArrayDeque<Long>()

    init {
        require(maxAttempts > 0)
        require(windowMillis > 0L)
        require(retryDelaysMillis.isNotEmpty())
        require(retryDelaysMillis.all { it >= 0L })
    }

    fun request(nowMillis: Long): SteamVoiceRecoveryAttempt? {
        val first = attempts.firstOrNull()
        if (first != null && (nowMillis < first || nowMillis - first >= windowMillis)) {
            attempts.clear()
        }
        if (attempts.size >= maxAttempts) return null
        val attempt = attempts.size + 1
        val delay = retryDelaysMillis[(attempt - 1).coerceAtMost(retryDelaysMillis.lastIndex)]
        attempts.addLast(nowMillis)
        return SteamVoiceRecoveryAttempt(attempt, delay)
    }

    fun reset() {
        attempts.clear()
    }
}
