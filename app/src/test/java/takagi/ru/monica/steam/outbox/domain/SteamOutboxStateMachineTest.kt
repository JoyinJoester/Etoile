package takagi.ru.monica.steam.outbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOutboxStateMachineTest {
    @Test
    fun transitionsAreOrderedAndCountAttemptsExactlyOnce() {
        val queued = record()
        val inFlight = SteamOutboxStateMachine.transition(
            queued,
            SteamOutboxEvent.CLAIM,
            nowMillis = 2_000L
        )
        assertEquals(SteamOutboxStatus.IN_FLIGHT, inFlight.status)
        assertEquals(1, inFlight.attemptCount)

        val awaiting = SteamOutboxStateMachine.transition(
            inFlight,
            SteamOutboxEvent.AWAIT_CONFIRMATION,
            nowMillis = 3_000L
        )
        assertEquals(SteamOutboxStatus.AWAITING_CONFIRMATION, awaiting.status)
        assertEquals(1, awaiting.attemptCount)

        val completed = SteamOutboxStateMachine.transition(
            awaiting,
            SteamOutboxEvent.COMPLETE,
            nowMillis = 4_000L
        )
        assertEquals(SteamOutboxStatus.COMPLETED, completed.status)
        assertThrows(IllegalStateException::class.java) {
            SteamOutboxStateMachine.transition(
                completed,
                SteamOutboxEvent.CLAIM,
                nowMillis = 5_000L
            )
        }
    }

    @Test
    fun serverConfirmationWinsAfterRetryAndRepeatedCompletionIsIdempotent() {
        val queued = record()
        val completedFromQueue = SteamOutboxStateMachine.transition(
            queued,
            SteamOutboxEvent.COMPLETE,
            nowMillis = 2_000L
        )
        val repeated = SteamOutboxStateMachine.transition(
            completedFromQueue,
            SteamOutboxEvent.COMPLETE,
            nowMillis = 3_000L
        )
        val inFlight = SteamOutboxStateMachine.transition(
            queued,
            SteamOutboxEvent.CLAIM,
            nowMillis = 2_000L
        )
        val retryable = SteamOutboxStateMachine.transition(
            inFlight,
            SteamOutboxEvent.RETRY,
            nowMillis = 3_000L,
            error = "timeout"
        )
        val completedFromRetry = SteamOutboxStateMachine.transition(
            retryable,
            SteamOutboxEvent.COMPLETE,
            nowMillis = 4_000L
        )

        assertEquals(SteamOutboxStatus.COMPLETED, completedFromQueue.status)
        assertEquals(completedFromQueue, repeated)
        assertEquals(SteamOutboxStatus.COMPLETED, completedFromRetry.status)
    }

    @Test
    fun retryIsRecoverableWithBoundedBackoffAndError() {
        val inFlight = SteamOutboxStateMachine.transition(
            record(),
            SteamOutboxEvent.CLAIM,
            nowMillis = 2_000L
        )
        val retry = SteamOutboxStateMachine.transition(
            inFlight,
            SteamOutboxEvent.RETRY,
            nowMillis = 3_000L,
            error = "timeout"
        )
        assertEquals(SteamOutboxStatus.RETRYABLE, retry.status)
        assertEquals("timeout", retry.lastError)
        assertTrue(retry.nextAttemptAtMillis > 3_000L)

        val claimedAgain = SteamOutboxStateMachine.transition(
            retry,
            SteamOutboxEvent.CLAIM,
            nowMillis = retry.nextAttemptAtMillis
        )
        assertEquals(SteamOutboxStatus.IN_FLIGHT, claimedAgain.status)
        assertEquals(2, claimedAgain.attemptCount)
    }

    @Test
    fun repeatedDeliveryFailuresStopAtTheCommercialSafetyCap() {
        val exhausted = record().copy(
            status = SteamOutboxStatus.IN_FLIGHT,
            attemptCount = SteamOutboxStateMachine.MAX_DELIVERY_ATTEMPTS
        )

        val failed = SteamOutboxStateMachine.transition(
            exhausted,
            SteamOutboxEvent.RETRY,
            nowMillis = 5_000L,
            error = "timeout"
        )

        assertEquals(SteamOutboxStatus.PERMANENT_FAILURE, failed.status)
        assertEquals("timeout", failed.lastError)
        assertEquals(exhausted.attemptCount, failed.attemptCount)
    }

    @Test
    fun friendMessageDedupeKeyIsAccountAndRequestScoped() {
        val first = SteamOutboxKeys.friendMessage("account-a", "partner", "request-1")
        val same = SteamOutboxKeys.friendMessage("account-a", "partner", "request-1")
        val otherAccount = SteamOutboxKeys.friendMessage("account-b", "partner", "request-1")
        assertEquals(first, same)
        assertTrue(first != otherAccount)
        assertTrue(!first.contains("account-a"))
        assertTrue(!first.contains("partner"))
    }

    private fun record() = SteamOutboxRecord(
        id = "request-1",
        accountId = 1L,
        accountSteamId = "76561198000000001",
        operation = SteamOutboxOperation.FRIEND_MESSAGE,
        dedupeKey = SteamOutboxKeys.friendMessage("account-a", "partner", "request-1"),
        payload = "{}",
        status = SteamOutboxStatus.QUEUED,
        attemptCount = 0,
        nextAttemptAtMillis = 0L,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L
    )
}
