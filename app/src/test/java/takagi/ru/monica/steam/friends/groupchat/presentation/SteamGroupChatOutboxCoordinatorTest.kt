package takagi.ru.monica.steam.friends.groupchat.presentation

import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatRecoveredOutbox
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessagePage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxRecord
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SteamGroupChatOutboxCoordinatorTest {
    @Test
    fun timeoutIsReconciledBeforeOutboxCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingGroupOutbox(SteamOutboxStatus.QUEUED)
        var sends = 0
        val gateway = TestGateway(
            send = {
                sends++
                throw SocketTimeoutException("timeout")
            },
            history = { listOf(serverEcho()) }
        )
        val updates = mutableListOf<SteamGroupChatDeliveryState>()

        SteamGroupChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            accountKey = "mdbx:42:entry-1|1|$ACCOUNT_STEAM_ID",
            pending = pending(),
            verifyBeforeSend = false,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(1, sends)
        assertEquals("mdbx:42:entry-1|1|$ACCOUNT_STEAM_ID", outbox.lastAccountKey)
        assertEquals(listOf("enqueue", "claim", "await", "complete"), outbox.events)
        assertEquals(SteamGroupChatDeliveryState.SENT, updates.last())
    }

    @Test
    fun recoveredInFlightWriteIsVerifiedWithoutDuplicateSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingGroupOutbox(SteamOutboxStatus.IN_FLIGHT)
        var sends = 0
        val gateway = TestGateway(
            send = {
                sends++
                serverEcho()
            },
            history = { emptyList() }
        )
        val updates = mutableListOf<SteamGroupChatDeliveryState>()

        SteamGroupChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            pending = pending().copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING),
            verifyBeforeSend = true,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(0, sends)
        assertEquals(listOf("enqueue", "retry"), outbox.events)
        assertEquals(SteamGroupChatDeliveryState.FAILED_RETRYABLE, updates.last())
    }

    @Test
    fun explicitRetryBypassesBackoffAndReusesTheClientMessageId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingGroupOutbox(SteamOutboxStatus.RETRYABLE)
        var sentClientId = ""
        val gateway = TestGateway(
            send = { message ->
                sentClientId = message.clientMessageId
                serverEcho()
            },
            history = { emptyList() }
        )
        val updates = mutableListOf<SteamGroupChatDeliveryState>()

        SteamGroupChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            pending = pending().copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING),
            verifyBeforeSend = true,
            forceRetry = true,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(REQUEST_ID, sentClientId)
        assertEquals(listOf("enqueue", "claim-force", "complete"), outbox.events)
        assertEquals(SteamGroupChatDeliveryState.SENT, updates.last())
    }

    @Test
    fun exhaustedOutboxBecomesPermanentInsteadOfRetryingForever() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val outbox = RecordingGroupOutbox(
            status = SteamOutboxStatus.QUEUED,
            retryResultStatus = SteamOutboxStatus.PERMANENT_FAILURE
        )
        val gateway = TestGateway(
            send = { throw SocketTimeoutException("timeout") },
            history = { emptyList() }
        )
        val updates = mutableListOf<SteamGroupChatDeliveryState>()

        SteamGroupChatOutgoingCoordinator(
            scope = this,
            gateway = gateway,
            sessionResolver = null,
            ioDispatcher = dispatcher,
            outbox = outbox
        ).dispatch(
            account = account(),
            pending = pending(),
            verifyBeforeSend = false,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { updates += it.deliveryState }
        )

        advanceUntilIdle()

        assertEquals(listOf("enqueue", "claim", "await", "retry"), outbox.events)
        assertEquals(SteamGroupChatDeliveryState.FAILED_PERMANENT, updates.last())
    }

    private fun pending() = SteamGroupChatMessage(
        groupId = GROUP_ID,
        chatId = CHAT_ID,
        senderSteamId = ACCOUNT_STEAM_ID,
        timestamp = 100L,
        ordinal = Int.MAX_VALUE,
        body = "hello",
        clientMessageId = REQUEST_ID,
        localCreatedAtMillis = 100_000L,
        deliveryState = SteamGroupChatDeliveryState.QUEUED
    )

    private fun serverEcho() = SteamGroupChatMessage(
        groupId = GROUP_ID,
        chatId = CHAT_ID,
        senderSteamId = ACCOUNT_STEAM_ID,
        timestamp = 101L,
        ordinal = 1,
        body = "hello"
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
        const val GROUP_ID = "8"
        const val CHAT_ID = "9"
        const val REQUEST_ID = "request-1"
    }
}

private class RecordingGroupOutbox(
    private var status: SteamOutboxStatus,
    private val retryResultStatus: SteamOutboxStatus = SteamOutboxStatus.RETRYABLE
) : SteamGroupChatOutbox {
    val events = mutableListOf<String>()
    var lastAccountKey: String? = null

    override suspend fun enqueue(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
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

    override suspend fun permanentFailure(
        clientMessageId: String,
        error: String?
    ): SteamOutboxRecord {
        events += "permanent"
        status = SteamOutboxStatus.PERMANENT_FAILURE
        return record(status = status)
    }

    override suspend fun recover(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        accountKey: String
    ): List<SteamGroupChatRecoveredOutbox> = emptyList()

    private fun record(
        pending: SteamGroupChatMessage = SteamGroupChatMessage(
            groupId = "8",
            chatId = "9",
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
        operation = SteamOutboxOperation.GROUP_MESSAGE,
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
    private val send: (SteamGroupChatMessage) -> SteamGroupChatMessage,
    private val history: () -> List<SteamGroupChatMessage>
) : SteamGroupChatGateway {
    override fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary> = emptyList()

    override fun getHistory(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        before: SteamGroupChatHistoryBoundary?
    ): SteamGroupChatMessagePage = SteamGroupChatMessagePage(history(), false)

    override fun sendMessage(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        body: String
    ): SteamGroupChatMessage = send(
        SteamGroupChatMessage(
            groupId = groupId,
            chatId = chatId,
            senderSteamId = account.steamId,
            timestamp = 100L,
            ordinal = Int.MAX_VALUE,
            body = body,
            clientMessageId = "request-1",
            localCreatedAtMillis = 100_000L
        )
    )

    override fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String = "8"

    override fun inviteFriend(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        steamId: String
    ) = Unit

    override fun acknowledge(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        timestamp: Long
    ) = Unit
}
