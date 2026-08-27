package takagi.ru.monica.steam.friends.chat.presentation

import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamChatRecoveredOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatOutboxCoordinatorTest {
    @Test
    fun timeoutIsReconciledBeforeOutboxCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingChatOutbox(SteamOutboxStatus.QUEUED)
        var sends = 0
        val gateway = TestGateway(
            send = {
                sends++
                throw SocketTimeoutException("timeout")
            },
            messages = { pending ->
                listOf(
                    pending.copy(
                        timestamp = 100L,
                        ordinal = 1,
                        clientMessageId = "",
                        deliveryState = SteamChatDeliveryState.SENT
                    )
                )
            }
        )
        val updates = mutableListOf<SteamChatDeliveryState>()
        val pending = pending()
        SteamChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            partnerSteamId = PARTNER,
            accountKey = "mdbx:42:entry-1|1|76561198000000001",
            pending = pending,
            verifyBeforeSend = false,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(1, sends)
        assertEquals("mdbx:42:entry-1|1|76561198000000001", outbox.lastAccountKey)
        assertEquals(listOf("enqueue", "claim", "await", "complete"), outbox.events)
        assertEquals(SteamChatDeliveryState.SENT, updates.last())
    }

    @Test
    fun recoveredInFlightWriteIsVerifiedWithoutDuplicateSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingChatOutbox(SteamOutboxStatus.IN_FLIGHT)
        var sends = 0
        val gateway = TestGateway(
            send = {
                sends++
                pending()
            },
            messages = { emptyList() }
        )
        val updates = mutableListOf<SteamChatDeliveryState>()
        SteamChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            partnerSteamId = PARTNER,
            pending = pending().copy(deliveryState = SteamChatDeliveryState.VERIFYING),
            verifyBeforeSend = true,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(0, sends)
        assertEquals(listOf("enqueue", "retry"), outbox.events)
        assertEquals(SteamChatDeliveryState.FAILED_RETRYABLE, updates.last())
    }

    @Test
    fun explicitRetryBypassesBackoffAndUsesTheExistingRequestId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingChatOutbox(SteamOutboxStatus.RETRYABLE)
        var sends = 0
        val gateway = TestGateway(
            send = {
                sends++
                it.copy(timestamp = 200L, ordinal = 2)
            },
            messages = { emptyList() }
        )
        val updates = mutableListOf<SteamChatDeliveryState>()

        SteamChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            partnerSteamId = PARTNER,
            pending = pending().copy(deliveryState = SteamChatDeliveryState.VERIFYING),
            verifyBeforeSend = true,
            forceRetry = true,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(1, sends)
        assertEquals(listOf("enqueue", "claim-force", "complete"), outbox.events)
        assertEquals(SteamChatDeliveryState.SENT, updates.last())
    }

    @Test
    fun exhaustedOutboxBecomesPermanentInsteadOfRetryingForever() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingChatOutbox(
            status = SteamOutboxStatus.QUEUED,
            retryResultStatus = SteamOutboxStatus.PERMANENT_FAILURE
        )
        val gateway = TestGateway(
            send = { throw SocketTimeoutException("timeout") },
            messages = { emptyList() }
        )
        val updates = mutableListOf<SteamChatDeliveryState>()

        SteamChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            partnerSteamId = PARTNER,
            pending = pending(),
            verifyBeforeSend = false,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(SteamChatDeliveryState.FAILED_PERMANENT, updates.last())
        assertEquals(listOf("enqueue", "claim", "await", "retry"), outbox.events)
    }

    private fun pending() = SteamChatMessage(
        partnerSteamId = PARTNER,
        senderSteamId = ACCOUNT_STEAM_ID,
        timestamp = 100L,
        ordinal = Int.MAX_VALUE,
        body = "hello",
        deliveryState = SteamChatDeliveryState.QUEUED,
        clientMessageId = "request-1",
        localCreatedAtMillis = 100_000L
    )

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_STEAM_ID,
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
        const val ACCOUNT_STEAM_ID = "76561198000000001"
        const val PARTNER = "76561198000000002"
    }
}

private class RecordingChatOutbox(
    private var status: SteamOutboxStatus,
    private val retryResultStatus: SteamOutboxStatus = SteamOutboxStatus.RETRYABLE
) : SteamChatOutbox {
    val events = mutableListOf<String>()
    var lastAccountKey: String? = null

    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamChatMessage,
        accountKey: String
    ): SteamOutboxRecord {
        events += "enqueue"
        lastAccountKey = accountKey
        return record(pending, status)
    }

    override suspend fun claim(clientMessageId: String, force: Boolean): SteamOutboxRecord {
        events += if (force) "claim-force" else "claim"
        status = SteamOutboxStatus.IN_FLIGHT
        return record(status = status)
    }

    override suspend fun awaitingConfirmation(clientMessageId: String): SteamOutboxRecord {
        events += "await"
        status = SteamOutboxStatus.AWAITING_CONFIRMATION
        return record(status = status)
    }

    override suspend fun complete(clientMessageId: String): SteamOutboxRecord {
        events += "complete"
        status = SteamOutboxStatus.COMPLETED
        return record(status = status)
    }

    override suspend fun retry(clientMessageId: String, error: String?): SteamOutboxRecord {
        events += "retry"
        status = retryResultStatus
        return record(status = status)
    }

    override suspend fun permanentFailure(clientMessageId: String, error: String?): SteamOutboxRecord {
        events += "permanent"
        status = SteamOutboxStatus.PERMANENT_FAILURE
        return record(status = status)
    }

    override suspend fun recover(
        account: SteamAccount,
        partnerSteamId: String,
        accountKey: String
    ): List<SteamChatRecoveredOutbox> = emptyList()

    private fun record(
        pending: SteamChatMessage = SteamChatMessage(
            partnerSteamId = "76561198000000002",
            senderSteamId = "76561198000000001",
            timestamp = 100L,
            ordinal = Int.MAX_VALUE,
            body = "hello",
            clientMessageId = "request-1",
            localCreatedAtMillis = 100_000L
        ),
        status: SteamOutboxStatus
    ) = SteamOutboxRecord(
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

private class TestGateway(
    private val send: (SteamChatMessage) -> SteamChatMessage,
    private val messages: (SteamChatMessage) -> List<SteamChatMessage>
) : SteamChatGateway {
    private var pending: SteamChatMessage? = null

    override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot =
        SteamChatSessionsSnapshot(account.steamId, emptyList(), 0L)

    override fun fetchMessages(
        account: SteamAccount,
        partnerSteamId: String,
        before: SteamChatHistoryBoundary?
    ): SteamChatPage = SteamChatPage(messages(requireNotNull(pending)), false)

    override fun sendMessage(
        account: SteamAccount,
        partnerSteamId: String,
        body: String,
        clientMessageId: String
    ): SteamChatMessage {
        val current = SteamChatMessage(
            partnerSteamId = partnerSteamId,
            senderSteamId = account.steamId,
            timestamp = 100L,
            ordinal = Int.MAX_VALUE,
            body = body,
            clientMessageId = clientMessageId,
            localCreatedAtMillis = 100_000L
        )
        pending = current
        return send(current)
    }

    override fun acknowledge(account: SteamAccount, partnerSteamId: String, timestamp: Long) = Unit
}
