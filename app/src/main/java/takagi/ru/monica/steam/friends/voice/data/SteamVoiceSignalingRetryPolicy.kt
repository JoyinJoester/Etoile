package takagi.ru.monica.steam.friends.voice.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.cm.SteamCmResponseTimeoutException

internal data class SteamVoiceSignalingRetry(
    val attempt: Int,
    val delayMillis: Long,
    val failure: Throwable
)

/** Mirrors Steam's bounded backoff instead of ending a call on one transient CM failure. */
internal class SteamVoiceSignalingRetrier(
    private val retryDelaysMillis: LongArray = longArrayOf(500L, 1_500L, 3_000L),
    private val sleeper: suspend (Long) -> Unit = { delay(it) }
) {
    init {
        require(retryDelaysMillis.isNotEmpty())
        require(retryDelaysMillis.all { it >= 0L })
    }

    suspend fun <T> execute(
        onRetry: (SteamVoiceSignalingRetry) -> Unit = {},
        block: suspend (attempt: Int) -> T
    ): T {
        var previousFailure: Throwable? = null
        val maximumAttempts = retryDelaysMillis.size + 1
        for (attempt in 1..maximumAttempts) {
            previousFailure?.let { failure ->
                val retry = SteamVoiceSignalingRetry(
                    attempt = attempt,
                    delayMillis = retryDelaysMillis[attempt - 2],
                    failure = failure
                )
                onRetry(retry)
                sleeper(retry.delayMillis)
            }
            try {
                return block(attempt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (attempt == maximumAttempts || !error.isRetryableSteamVoiceSignalingFailure()) {
                    throw error
                }
                previousFailure = error
            }
        }
        error("Steam voice signaling retry loop exhausted")
    }
}

internal fun Throwable.isRetryableSteamVoiceSignalingFailure(): Boolean {
    val failures = causeChain().toList()
    if (failures.any { it is SteamCmResponseTimeoutException }) return false
    failures.filterIsInstance<SteamApiException>().firstOrNull()?.let { apiError ->
        return when (val status = apiError.httpStatusCode) {
            408, 429 -> true
            null -> false
            else -> status >= 500
        }
    }
    return failures.any { it is IOException }
}

private fun Throwable.causeChain(): Sequence<Throwable> = sequence {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this@causeChain
    while (current != null && visited.add(current)) {
        yield(current)
        current = current.cause
    }
}
