package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamChatRecoveredOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

class SteamChatRealtimeOutboxReconcilerTest {
    @Test
    fun matchingLocalEchoCompletesTheOriginalDurableRequest() = runTest {
        val outbox = RealtimeEchoOutbox(pending("hello"))

        val completed = completeMatchingRealtimeOutboxEcho(
            outbox = outbox,
            account = account(),
            accountKey = "mdbx:42:entry|1|$ACCOUNT",
            message = serverEcho("hello")
        )

        assertEquals("client-1", completed)
        assertEquals("client-1", outbox.completedId)
        assertEquals("mdbx:42:entry|1|$ACCOUNT", outbox.recoveredAccountKey)
    }

    @Test
    fun unrelatedEchoDoesNotCompleteAnotherWrite() = runTest {
        val outbox = RealtimeEchoOutbox(pending("hello"))

        val completed = completeMatchingRealtimeOutboxEcho(
            outbox,
            account(),
            "room|1|$ACCOUNT",
            serverEcho("different")
        )

        assertNull(completed)
        assertNull(outbox.completedId)
    }

    private fun pending(body: String) = SteamChatMessage(
        partnerSteamId = PARTNER,
        senderSteamId = ACCOUNT,
        timestamp = 100L,
        ordinal = Int.MAX_VALUE,
        body = body,
        deliveryState = SteamChatDeliveryState.VERIFYING,
        clientMessageId = "client-1",
        localCreatedAtMillis = 100_000L
    )

    private fun serverEcho(body: String) = SteamChatMessage(
        partnerSteamId = PARTNER,
        senderSteamId = ACCOUNT,
        timestamp = 101L,
        ordinal = 2,
        body = body
    )

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT,
        accountName = "account",
        displayName = "Account",
        deviceId = "device",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT = "76561198000000001"
        const val PARTNER = "76561198000000003"
    }
}

private class RealtimeEchoOutbox(
    private val pending: SteamChatMessage
) : SteamChatOutbox {
    var completedId: String? = null
    var recoveredAccountKey: String? = null

    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamChatMessage,
        accountKey: String
    ) = record()

    override suspend fun claim(clientMessageId: String, force: Boolean) = record()

    override suspend fun awaitingConfirmation(clientMessageId: String) = record()

    override suspend fun complete(clientMessageId: String): SteamOutboxRecord {
        completedId = clientMessageId
        return record(SteamOutboxStatus.COMPLETED)
    }

    override suspend fun retry(clientMessageId: String, error: String?) = record()

    override suspend fun permanentFailure(clientMessageId: String, error: String?) = record()

    override suspend fun recover(
        account: SteamAccount,
        partnerSteamId: String,
        accountKey: String
    ): List<SteamChatRecoveredOutbox> {
        recoveredAccountKey = accountKey
        return listOf(SteamChatRecoveredOutbox(pending, verifyBeforeSend = true))
    }

    private fun record(status: SteamOutboxStatus = SteamOutboxStatus.AWAITING_CONFIRMATION) =
        SteamOutboxRecord(
            id = pending.clientMessageId,
            accountId = 1L,
            accountSteamId = pending.senderSteamId,
            operation = SteamOutboxOperation.FRIEND_MESSAGE,
            dedupeKey = "dedupe",
            payload = "{}",
            status = status,
            attemptCount = 1,
            nextAttemptAtMillis = 0L,
            createdAtMillis = pending.localCreatedAtMillis,
            updatedAtMillis = pending.localCreatedAtMillis
        )
}
