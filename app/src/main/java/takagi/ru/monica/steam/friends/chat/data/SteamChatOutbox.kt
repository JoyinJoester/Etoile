package takagi.ru.monica.steam.friends.chat.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.outbox.data.SteamOutboxCommand
import takagi.ru.monica.steam.outbox.data.SteamOutboxStore
import takagi.ru.monica.steam.outbox.domain.SteamOutboxEvent
import takagi.ru.monica.steam.outbox.domain.SteamOutboxKeys
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

interface SteamChatOutbox {
    suspend fun enqueue(
        account: SteamAccount,
        pending: SteamChatMessage,
        accountKey: String = legacySteamChatAccountKey(account)
    ): SteamOutboxRecord
    suspend fun claim(clientMessageId: String, force: Boolean = false): SteamOutboxRecord
    suspend fun awaitingConfirmation(clientMessageId: String): SteamOutboxRecord
    suspend fun complete(clientMessageId: String): SteamOutboxRecord
    suspend fun retry(clientMessageId: String, error: String?): SteamOutboxRecord
    suspend fun permanentFailure(clientMessageId: String, error: String?): SteamOutboxRecord
    suspend fun recover(
        account: SteamAccount,
        partnerSteamId: String,
        accountKey: String = legacySteamChatAccountKey(account)
    ): List<SteamChatRecoveredOutbox>
}

data class SteamChatRecoveredOutbox(
    val message: SteamChatMessage,
    val verifyBeforeSend: Boolean
)

class SteamChatRoomOutbox(
    private val store: SteamOutboxStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SteamChatOutbox {
    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamChatMessage,
        accountKey: String
    ): SteamOutboxRecord {
        val payload = SteamChatOutboxPayload(
            accountKey = accountKey,
            partnerSteamId = pending.partnerSteamId,
            body = pending.body,
            replyToStableId = pending.replyToStableId,
            localCreatedAtMillis = pending.localCreatedAtMillis
        )
        return store.enqueue(
            SteamOutboxCommand(
                id = pending.clientMessageId,
                accountId = account.id,
                accountSteamId = account.steamId,
                operation = SteamOutboxOperation.FRIEND_MESSAGE,
                dedupeKey = SteamOutboxKeys.friendMessage(
                    accountKey = accountKey,
                    partnerSteamId = pending.partnerSteamId,
                    clientMessageId = pending.clientMessageId
                ),
                payload = json.encodeToString(payload),
                createdAtMillis = pending.localCreatedAtMillis
            )
        )
    }

    override suspend fun claim(clientMessageId: String, force: Boolean): SteamOutboxRecord =
        store.transition(
            id = clientMessageId,
            event = SteamOutboxEvent.CLAIM,
            forceClaim = force
        )

    override suspend fun awaitingConfirmation(clientMessageId: String): SteamOutboxRecord =
        store.transition(clientMessageId, SteamOutboxEvent.AWAIT_CONFIRMATION)

    override suspend fun complete(clientMessageId: String): SteamOutboxRecord =
        store.transition(clientMessageId, SteamOutboxEvent.COMPLETE)

    override suspend fun retry(clientMessageId: String, error: String?): SteamOutboxRecord =
        store.transition(clientMessageId, SteamOutboxEvent.RETRY, error)

    override suspend fun permanentFailure(
        clientMessageId: String,
        error: String?
    ): SteamOutboxRecord = store.transition(
        clientMessageId,
        SteamOutboxEvent.PERMANENT_FAILURE,
        error
    )

    override suspend fun recover(
        account: SteamAccount,
        partnerSteamId: String,
        accountKey: String
    ): List<SteamChatRecoveredOutbox> = store.recoverable(account.id)
        .asSequence()
        .filter { it.accountSteamId == account.steamId }
        .filter { it.operation == SteamOutboxOperation.FRIEND_MESSAGE }
        .mapNotNull { record ->
            val payload = runCatching {
                json.decodeFromString<SteamChatOutboxPayload>(record.payload)
            }.getOrNull() ?: return@mapNotNull null
            if (payload.partnerSteamId != partnerSteamId) return@mapNotNull null
            if (payload.accountKey != null && payload.accountKey != accountKey) {
                return@mapNotNull null
            }
            SteamChatRecoveredOutbox(
                message = SteamChatMessage(
                    partnerSteamId = payload.partnerSteamId,
                    senderSteamId = account.steamId,
                    timestamp = payload.localCreatedAtMillis / 1_000L,
                    ordinal = Int.MAX_VALUE,
                    body = payload.body,
                    deliveryState = record.toDeliveryState(),
                    clientMessageId = record.id,
                    localCreatedAtMillis = payload.localCreatedAtMillis,
                    replyToStableId = payload.replyToStableId
                ),
                verifyBeforeSend = record.status != SteamOutboxStatus.QUEUED
            )
        }
        .toList()

    companion object {
        fun from(context: Context): SteamChatRoomOutbox =
            SteamChatRoomOutbox(SteamOutboxStore.from(context.applicationContext))
    }
}

@Serializable
private data class SteamChatOutboxPayload(
    val accountKey: String? = null,
    val partnerSteamId: String,
    val body: String,
    val replyToStableId: String?,
    val localCreatedAtMillis: Long
)

private fun legacySteamChatAccountKey(account: SteamAccount): String =
    "${account.id}:${account.steamId}"

private fun SteamOutboxRecord.toDeliveryState(): SteamChatDeliveryState = when (status) {
    SteamOutboxStatus.QUEUED -> SteamChatDeliveryState.QUEUED
    SteamOutboxStatus.IN_FLIGHT -> SteamChatDeliveryState.SENDING
    SteamOutboxStatus.AWAITING_CONFIRMATION -> SteamChatDeliveryState.VERIFYING
    SteamOutboxStatus.RETRYABLE -> SteamChatDeliveryState.FAILED_RETRYABLE
    SteamOutboxStatus.COMPLETED -> SteamChatDeliveryState.SENT
    SteamOutboxStatus.PERMANENT_FAILURE,
    SteamOutboxStatus.CANCELLED -> SteamChatDeliveryState.FAILED_PERMANENT
}
