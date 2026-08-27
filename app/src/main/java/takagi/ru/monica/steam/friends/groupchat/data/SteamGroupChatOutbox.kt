package takagi.ru.monica.steam.friends.groupchat.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.outbox.data.SteamOutboxCommand
import takagi.ru.monica.steam.outbox.data.SteamOutboxStore
import takagi.ru.monica.steam.outbox.domain.SteamOutboxEvent
import takagi.ru.monica.steam.outbox.domain.SteamOutboxKeys
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

interface SteamGroupChatOutbox {
    suspend fun enqueue(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
        accountKey: String = legacySteamGroupChatAccountKey(account)
    ): SteamOutboxRecord

    suspend fun claim(clientMessageId: String, force: Boolean = false): SteamOutboxRecord
    suspend fun awaitingConfirmation(clientMessageId: String): SteamOutboxRecord
    suspend fun complete(clientMessageId: String): SteamOutboxRecord
    suspend fun retry(clientMessageId: String, error: String?): SteamOutboxRecord
    suspend fun permanentFailure(clientMessageId: String, error: String?): SteamOutboxRecord

    suspend fun recover(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        accountKey: String = legacySteamGroupChatAccountKey(account)
    ): List<SteamGroupChatRecoveredOutbox>
}

data class SteamGroupChatRecoveredOutbox(
    val message: SteamGroupChatMessage,
    val verifyBeforeSend: Boolean
)

class SteamGroupChatRoomOutbox(
    private val store: SteamOutboxStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SteamGroupChatOutbox {
    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
        accountKey: String
    ): SteamOutboxRecord {
        val payload = SteamGroupChatOutboxPayload(
            accountKey = accountKey,
            groupId = pending.groupId,
            chatId = pending.chatId,
            body = pending.body,
            localCreatedAtMillis = pending.localCreatedAtMillis
        )
        return store.enqueue(
            SteamOutboxCommand(
                id = pending.clientMessageId,
                accountId = account.id,
                accountSteamId = account.steamId,
                operation = SteamOutboxOperation.GROUP_MESSAGE,
                dedupeKey = SteamOutboxKeys.groupMessage(
                    accountKey = accountKey,
                    groupId = pending.groupId,
                    chatId = pending.chatId,
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
        groupId: String,
        chatId: String,
        accountKey: String
    ): List<SteamGroupChatRecoveredOutbox> = store.recoverable(account.id)
        .asSequence()
        .filter { it.accountSteamId == account.steamId }
        .filter { it.operation == SteamOutboxOperation.GROUP_MESSAGE }
        .mapNotNull { record ->
            val payload = runCatching {
                json.decodeFromString<SteamGroupChatOutboxPayload>(record.payload)
            }.getOrNull() ?: return@mapNotNull null
            if (payload.groupId != groupId || payload.chatId != chatId) return@mapNotNull null
            if (payload.accountKey != null && payload.accountKey != accountKey) {
                return@mapNotNull null
            }
            SteamGroupChatRecoveredOutbox(
                message = SteamGroupChatMessage(
                    groupId = payload.groupId,
                    chatId = payload.chatId,
                    senderSteamId = account.steamId,
                    timestamp = payload.localCreatedAtMillis / 1_000L,
                    ordinal = Int.MAX_VALUE,
                    body = payload.body,
                    clientMessageId = record.id,
                    localCreatedAtMillis = payload.localCreatedAtMillis,
                    deliveryState = record.toGroupDeliveryState()
                ),
                verifyBeforeSend = record.status != SteamOutboxStatus.QUEUED
            )
        }
        .toList()

    companion object {
        fun from(context: Context): SteamGroupChatRoomOutbox =
            SteamGroupChatRoomOutbox(SteamOutboxStore.from(context.applicationContext))
    }
}

@Serializable
private data class SteamGroupChatOutboxPayload(
    val accountKey: String? = null,
    val groupId: String,
    val chatId: String,
    val body: String,
    val localCreatedAtMillis: Long
)

private fun legacySteamGroupChatAccountKey(account: SteamAccount): String =
    "${account.id}|${account.steamId}"

private fun SteamOutboxRecord.toGroupDeliveryState(): SteamGroupChatDeliveryState = when (status) {
    SteamOutboxStatus.QUEUED -> SteamGroupChatDeliveryState.QUEUED
    SteamOutboxStatus.IN_FLIGHT -> SteamGroupChatDeliveryState.SENDING
    SteamOutboxStatus.AWAITING_CONFIRMATION -> SteamGroupChatDeliveryState.VERIFYING
    SteamOutboxStatus.RETRYABLE -> SteamGroupChatDeliveryState.FAILED_RETRYABLE
    SteamOutboxStatus.COMPLETED -> SteamGroupChatDeliveryState.SENT
    SteamOutboxStatus.PERMANENT_FAILURE,
    SteamOutboxStatus.CANCELLED -> SteamGroupChatDeliveryState.FAILED_PERMANENT
}
