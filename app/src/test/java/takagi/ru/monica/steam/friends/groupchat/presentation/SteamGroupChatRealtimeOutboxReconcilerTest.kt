package takagi.ru.monica.steam.friends.groupchat.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatRecoveredOutbox
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

class SteamGroupChatRealtimeOutboxReconcilerTest {
    @Test
    fun closestIdenticalPendingMessageCompletesForTheRealtimeEcho() = runTest {
        val outbox = EchoOutbox(
            listOf(
                recovered("older", 100_000L),
                recovered("closest", 129_000L)
            )
        )

        val completed = completeMatchingRealtimeGroupOutboxEcho(
            outbox = outbox,
            account = account(),
            accountKey = "room|1|$ACCOUNT_ID",
            message = message(clientId = "", timestamp = 130L)
        )

        assertEquals("closest", completed)
        assertEquals(listOf("closest"), outbox.completed)
        assertEquals("room|1|$ACCOUNT_ID", outbox.lastAccountKey)
    }

    private fun recovered(id: String, createdAt: Long) = SteamGroupChatRecoveredOutbox(
        message = message(clientId = id, timestamp = createdAt / 1_000L).copy(
            localCreatedAtMillis = createdAt
        ),
        verifyBeforeSend = true
    )

    private fun message(clientId: String, timestamp: Long) = SteamGroupChatMessage(
        groupId = "8",
        chatId = "9",
        senderSteamId = ACCOUNT_ID,
        timestamp = timestamp,
        ordinal = if (clientId.isBlank()) 4 else Int.MAX_VALUE,
        body = "same body",
        clientMessageId = clientId,
        localCreatedAtMillis = timestamp * 1_000L
    )

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_ID,
        accountName = "account",
        displayName = "Account",
        deviceId = "device",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
    }
}

private class EchoOutbox(
    private val recovered: List<SteamGroupChatRecoveredOutbox>
) : SteamGroupChatOutbox {
    val completed = mutableListOf<String>()
    var lastAccountKey: String? = null

    override suspend fun recover(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        accountKey: String
    ): List<SteamGroupChatRecoveredOutbox> {
        lastAccountKey = accountKey
        return recovered
    }

    override suspend fun complete(clientMessageId: String): SteamOutboxRecord {
        completed += clientMessageId
        return record(clientMessageId, SteamOutboxStatus.COMPLETED)
    }

    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
        accountKey: String
    ): SteamOutboxRecord = record(pending.clientMessageId, SteamOutboxStatus.QUEUED)

    override suspend fun claim(clientMessageId: String, force: Boolean): SteamOutboxRecord =
        record(clientMessageId, SteamOutboxStatus.IN_FLIGHT)

    override suspend fun awaitingConfirmation(clientMessageId: String): SteamOutboxRecord =
        record(clientMessageId, SteamOutboxStatus.AWAITING_CONFIRMATION)

    override suspend fun retry(clientMessageId: String, error: String?): SteamOutboxRecord =
        record(clientMessageId, SteamOutboxStatus.RETRYABLE)

    override suspend fun permanentFailure(
        clientMessageId: String,
        error: String?
    ): SteamOutboxRecord = record(clientMessageId, SteamOutboxStatus.PERMANENT_FAILURE)

    private fun record(id: String, status: SteamOutboxStatus) = SteamOutboxRecord(
        id = id,
        accountId = 1L,
        accountSteamId = "76561198000000001",
        operation = SteamOutboxOperation.GROUP_MESSAGE,
        dedupeKey = "dedupe-$id",
        payload = "{}",
        status = status,
        attemptCount = 1,
        nextAttemptAtMillis = 0L,
        createdAtMillis = 0L,
        updatedAtMillis = 0L
    )
}
