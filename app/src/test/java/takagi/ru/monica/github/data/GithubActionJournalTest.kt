package takagi.ru.monica.github.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.data.runAction

@OptIn(ExperimentalCoroutinesApi::class)
class GithubActionJournalTest {
    private val scope = GithubActionScope(1, "Private-Owner", "Private-Repo")
    private val operation = GithubActionOperation(GithubActionKind.DISPATCH, 7)

    @Test fun failureSurvivesNewJournalAndDoesNotReplayUntilExplicitConsent() = runTest {
        val storage = Storage()
        val first = GithubActionJournalImpl(storage, UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        assertTrue(first.runAction(scope, operation, false, { true }) { calls++; Result.failure<Unit>(IOException()) }.isFailure)
        assertEquals(setOf(operation), first.observe(scope).first())
        val cold = GithubActionJournalImpl(storage, UnconfinedTestDispatcher(testScheduler))
        assertEquals(setOf(operation), cold.observe(scope).first())
        assertTrue(cold.runAction(scope, operation, false, { true }) { calls++; Result.success(Unit) }.isFailure)
        assertEquals(1, calls)
        assertTrue(cold.runAction(scope, operation, true, { true }) { calls++; Result.success(Unit) }.isSuccess)
        assertEquals(2, calls)
        assertTrue(cold.observe(scope).first().isEmpty())
    }

    @Test fun recordsAreScopedToNumericAccountRepositoryKindAndTarget() = runTest {
        val journal = InMemoryGithubActionJournal()
        for (kind in GithubActionKind.entries) journal.finish(journal.begin(scope, operation.copy(kind = kind), false), false)
        assertEquals(GithubActionKind.entries.toSet(), journal.observe(scope).first().map { it.kind }.toSet())
        assertEquals(GithubActionKind.entries.size, journal.observe(scope.copy(owner = "private-owner", name = "private-repo")).first().size)
        assertTrue(journal.observe(scope.copy(accountId = 2)).first().isEmpty())
        assertTrue(journal.observe(scope.copy(name = "another-repo")).first().isEmpty())
        val other = operation.copy(targetId = 8)
        journal.finish(journal.begin(scope, other, false), true)
        assertEquals(GithubActionKind.entries.size, journal.observe(scope).first().size)
    }

    @Test fun liveAttemptRejectsAnotherAttemptAndAcknowledgementEvenWithRepeatConsent() = runTest {
        val journal = InMemoryGithubActionJournal()
        val attempt = journal.begin(scope, operation, false)
        assertTrue(runCatching { journal.begin(scope, operation, true) }.isFailure)
        assertTrue(runCatching { journal.acknowledge(scope, operation) }.isFailure)
        journal.finish(attempt, false)
        journal.acknowledge(scope, operation)
        assertTrue(journal.observe(scope).first().isEmpty())
    }

    @Test fun lateSuccessNeverClearsANewerPersistedAttempt() = runTest {
        val storage = Storage()
        val first = GithubActionJournalImpl(storage, UnconfinedTestDispatcher(testScheduler))
        val old = first.begin(scope, operation, false)
        val cold = GithubActionJournalImpl(storage, UnconfinedTestDispatcher(testScheduler))
        val newer = cold.begin(scope, operation, true)
        assertNotEquals(old.id, newer.id)
        first.finish(old, true)
        assertEquals(setOf(operation), cold.observe(scope).first())
        cold.finish(newer, true)
        assertTrue(cold.observe(scope).first().isEmpty())
    }

    @Test fun persistenceFailurePreventsNetworkAndClearFailureRetainsWarning() = runTest {
        val storage = Storage().apply { writeSucceeds = false }
        val journal = GithubActionJournalImpl(storage, UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        assertTrue(journal.runAction(scope, operation, false, { true }) { calls++; Result.success(Unit) }.isFailure)
        assertEquals(0, calls)
        storage.writeSucceeds = true
        val attempt = journal.begin(scope, operation, false)
        storage.writeSucceeds = false
        assertTrue(runCatching { journal.finish(attempt, true) }.isFailure)
        assertEquals(setOf(operation), journal.observe(scope).first())
        storage.writeSucceeds = true
        journal.acknowledge(scope, operation) // Failed clear must still release its live-flight slot.
        assertTrue(journal.observe(scope).first().isEmpty())
    }

    @Test fun cancellationWhilePersistingReleasesLiveFlightButKeepsWarningAndNeverSends() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = object : Storage() {
            override fun write(key: String, value: String?): Boolean {
                if (value != null) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                return super.write(key, value)
            }
        }
        val journal = GithubActionJournalImpl(storage, Dispatchers.IO)
        val calls = AtomicInteger()
        val pending = launch {
            journal.runAction(scope, operation, false, { true }) { calls.incrementAndGet(); Result.success(Unit) }
        }
        runCurrent()
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            pending.cancel()
        } finally { release.countDown() }
        pending.join()
        assertEquals(0, calls.get())
        assertEquals(setOf(operation), journal.observe(scope).first())
        val next = journal.begin(scope, operation, true)
        journal.finish(next, true)
        assertTrue(journal.observe(scope).first().isEmpty())
    }

    @Test fun cancellationOrSessionChangeAfterResponseRetainsUncertainty() = runTest {
        for (cancelled in listOf(false, true)) {
            val journal = InMemoryGithubActionJournal()
            var current = true
            val pending = launch {
                journal.runAction(scope, operation, false, { current }) {
                    assertEquals(setOf(operation), journal.observe(scope).first()) // Durable before IO.
                    if (cancelled) currentCoroutineContext().cancel() else current = false
                    Result.success(Unit)
                }
            }
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(setOf(operation), journal.observe(scope).first())
        }
    }

    @Test fun inspectionAndAcknowledgementNeverExecuteTheProvidedWrite() = runTest {
        val journal = InMemoryGithubActionJournal()
        val attempt = journal.begin(scope, operation, false)
        journal.finish(attempt, false)
        assertEquals(setOf(operation), journal.observe(scope).first())
        journal.acknowledge(scope, operation)
        assertTrue(journal.observe(scope).first().isEmpty())
    }

    private open class Storage : GithubActionJournalStorage {
        val values = mutableMapOf<String, String>()
        var writeSucceeds = true
        override fun entries(): Map<String, String> = values.toMap()
        override fun write(key: String, value: String?): Boolean {
            if (!writeSucceeds) return false
            if (value == null) values.remove(key) else values[key] = value
            return true
        }
    }
}
