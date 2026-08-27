package takagi.ru.monica.steam.outbox.domain

import kotlin.math.min
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
enum class SteamOutboxOperation {
    FRIEND_MESSAGE,
    GROUP_MESSAGE,
    GENERIC
}

@Serializable
enum class SteamOutboxStatus {
    QUEUED,
    IN_FLIGHT,
    AWAITING_CONFIRMATION,
    COMPLETED,
    RETRYABLE,
    PERMANENT_FAILURE,
    CANCELLED
}

@Serializable
data class SteamOutboxRecord(
    val id: String,
    val accountId: Long,
    val accountSteamId: String,
    val operation: SteamOutboxOperation,
    val dedupeKey: String,
    val payload: String,
    val status: SteamOutboxStatus,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastError: String? = null
)

enum class SteamOutboxEvent {
    CLAIM,
    AWAIT_CONFIRMATION,
    COMPLETE,
    RETRY,
    PERMANENT_FAILURE,
    CANCEL
}

object SteamOutboxStateMachine {
    fun transition(
        record: SteamOutboxRecord,
        event: SteamOutboxEvent,
        nowMillis: Long,
        error: String? = null
    ): SteamOutboxRecord {
        if (
            (record.status == SteamOutboxStatus.COMPLETED &&
                event == SteamOutboxEvent.COMPLETE) ||
            (record.status == SteamOutboxStatus.CANCELLED &&
                event == SteamOutboxEvent.CANCEL)
        ) {
            return record
        }
        val nextStatus = when (record.status) {
            SteamOutboxStatus.QUEUED -> when (event) {
                SteamOutboxEvent.CLAIM -> SteamOutboxStatus.IN_FLIGHT
                SteamOutboxEvent.COMPLETE -> SteamOutboxStatus.COMPLETED
                SteamOutboxEvent.CANCEL -> SteamOutboxStatus.CANCELLED
                else -> invalid(record, event)
            }
            SteamOutboxStatus.RETRYABLE -> when (event) {
                SteamOutboxEvent.CLAIM -> {
                    check(nowMillis >= record.nextAttemptAtMillis) {
                        "Outbox item is not ready for retry"
                    }
                    SteamOutboxStatus.IN_FLIGHT
                }
                SteamOutboxEvent.COMPLETE -> SteamOutboxStatus.COMPLETED
                SteamOutboxEvent.CANCEL -> SteamOutboxStatus.CANCELLED
                else -> invalid(record, event)
            }
            SteamOutboxStatus.IN_FLIGHT,
            SteamOutboxStatus.AWAITING_CONFIRMATION -> when (event) {
                SteamOutboxEvent.AWAIT_CONFIRMATION -> SteamOutboxStatus.AWAITING_CONFIRMATION
                SteamOutboxEvent.COMPLETE -> SteamOutboxStatus.COMPLETED
                SteamOutboxEvent.RETRY -> if (record.attemptCount >= MAX_DELIVERY_ATTEMPTS) {
                    SteamOutboxStatus.PERMANENT_FAILURE
                } else {
                    SteamOutboxStatus.RETRYABLE
                }
                SteamOutboxEvent.PERMANENT_FAILURE -> SteamOutboxStatus.PERMANENT_FAILURE
                SteamOutboxEvent.CANCEL -> SteamOutboxStatus.CANCELLED
                SteamOutboxEvent.CLAIM -> invalid(record, event)
            }
            SteamOutboxStatus.COMPLETED,
            SteamOutboxStatus.PERMANENT_FAILURE,
            SteamOutboxStatus.CANCELLED -> invalid(record, event)
        }
        val nextAttempt = when (nextStatus) {
            SteamOutboxStatus.AWAITING_CONFIRMATION -> nowMillis + CONFIRMATION_WINDOW_MILLIS
            SteamOutboxStatus.RETRYABLE -> nowMillis + retryDelayMillis(record.attemptCount)
            else -> record.nextAttemptAtMillis
        }
        return record.copy(
            status = nextStatus,
            attemptCount = if (event == SteamOutboxEvent.CLAIM) {
                record.attemptCount + 1
            } else {
                record.attemptCount
            },
            nextAttemptAtMillis = nextAttempt,
            updatedAtMillis = nowMillis,
            lastError = when (event) {
                SteamOutboxEvent.RETRY,
                SteamOutboxEvent.PERMANENT_FAILURE -> error?.trim()?.takeIf(String::isNotBlank)
                SteamOutboxEvent.COMPLETE,
                SteamOutboxEvent.CLAIM,
                SteamOutboxEvent.AWAIT_CONFIRMATION -> null
                SteamOutboxEvent.CANCEL -> record.lastError
            }
        )
    }

    private fun retryDelayMillis(attemptCount: Int): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 8)
        return min(MAX_RETRY_DELAY_MILLIS, BASE_RETRY_DELAY_MILLIS shl exponent)
    }

    private fun invalid(record: SteamOutboxRecord, event: SteamOutboxEvent): Nothing =
        error("Invalid Outbox transition ${record.status} -> $event")

    private const val BASE_RETRY_DELAY_MILLIS = 1_000L
    private const val MAX_RETRY_DELAY_MILLIS = 5 * 60 * 1_000L
    private const val CONFIRMATION_WINDOW_MILLIS = 45_000L
    const val MAX_DELIVERY_ATTEMPTS = 5
}

object SteamOutboxKeys {
    fun friendMessage(accountKey: String, partnerSteamId: String, clientMessageId: String): String {
        val material = "friend-message|${accountKey.trim()}|${partnerSteamId.trim()}|${clientMessageId.trim()}"
        return hashed("friend-message", material)
    }

    fun groupMessage(
        accountKey: String,
        groupId: String,
        chatId: String,
        clientMessageId: String
    ): String {
        val material = "group-message|${accountKey.trim()}|${groupId.trim()}|" +
            "${chatId.trim()}|${clientMessageId.trim()}"
        return hashed("group-message", material)
    }

    private fun hashed(prefix: String, material: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "$prefix:$digest"
    }
}
