package takagi.ru.monica.github.feature.store

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.FdroidApp

class StoreCatalogFilterTest {
    private val reader = FdroidApp("org.reader", "Reader", "Feeds", "1", null, null, 1)
    private val notes = reader.copy(packageName = "org.notes", name = "Notes", summary = null)

    @Test fun scansOnWorkerAndHonorsInstalledUpdateAndTextFilters() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val main = Thread.currentThread()
            val values = listOf(reader, notes)
            val apps = object : AbstractList<FdroidApp>() {
                override val size get() = values.size
                override fun get(index: Int): FdroidApp {
                    assertNotSame(main, Thread.currentThread())
                    return values[index]
                }
            }
            val source = MutableStateFlow(StoreUiState(fdroidApps = apps, query = " FEEDS "))
            assertEquals(listOf(reader), withTimeout(5000) { source.fdroidCatalogProjections(worker).first().apps })
            source.value = source.value.copy(query = "", fdroidFilter = FdroidCatalogFilter.INSTALLED,
                fdroidInstalled = mapOf(notes.packageName to "0"))
            assertEquals(listOf(notes), source.fdroidCatalogProjections(worker).first().apps)
            source.value = source.value.copy(fdroidFilter = FdroidCatalogFilter.UPDATES, updates = listOf(reader), query = "notes")
            assertTrue(source.fdroidCatalogProjections(worker).first().apps.isEmpty())
        }
    }

    @Test fun queryCanChangeWhileScanIsBlockedAndCancelledResultIsNeverPublished() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val visits = AtomicInteger()
            val apps = object : AbstractList<FdroidApp>() {
                override val size = 2
                override fun get(index: Int): FdroidApp {
                    if (visits.incrementAndGet() == 1) {
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    return if (index == 0) reader else notes
                }
            }
            val source = MutableStateFlow(StoreUiState(fdroidApps = apps, query = "reader"))
            val results = Channel<FdroidCatalogProjection>(Channel.UNLIMITED)
            val job = launch { source.fdroidCatalogProjections(worker).collect {
                if (it.inputs == source.value.fdroidCatalogInputs()) results.send(it)
            } }
            try {
                withTimeout(5000) { entered.await() }
                source.value = source.value.copy(query = "notes")
                yield() // Process cancellation while the worker still holds the obsolete scan.
                release.countDown()
                val result = withTimeout(5000) { results.receive() }
                assertEquals("notes", result.inputs.query)
                assertEquals(listOf(notes), result.apps)
                assertTrue(results.tryReceive().isFailure)
            } finally {
                release.countDown()
                job.cancel()
                job.join()
            }
        }
    }

    @Test fun progressDoesNotRescanAndPendingQueryCannotExposePriorMatchesOrAdvanceReader() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val visits = AtomicInteger()
            val apps = object : AbstractList<FdroidApp>() {
                override val size = 2
                override fun get(index: Int): FdroidApp {
                    visits.incrementAndGet()
                    return if (index == 0) reader else notes
                }
            }
            val source = MutableStateFlow(StoreUiState(fdroidApps = apps, query = "reader"))
            val results = Channel<FdroidCatalogProjection>(Channel.UNLIMITED)
            val job = launch { source.fdroidCatalogProjections(worker).collect { results.send(it) } }
            try {
                val first = withTimeout(5000) { results.receive() }
                val ready = source.value.copy(fdroidProjection = first)
                assertEquals(listOf(reader), ready.filteredFdroidApps)
                assertFalse(ready.isFilteringFdroid)
                source.value = ready.copy(downloads = mapOf("asset" to ApkDownloadUiState(progress = .4f)))
                yield(); yield()
                assertEquals(2, visits.get())
                assertTrue(results.tryReceive().isFailure)
                val pending = ready.copy(query = "notes")
                assertTrue(pending.isFilteringFdroid)
                assertTrue(pending.filteredFdroidApps.isEmpty())
                source.value = pending
                val next = withTimeout(5000) { results.receive() }
                assertEquals(listOf(notes), pending.copy(fdroidProjection = next).filteredFdroidApps)
            } finally {
                job.cancel()
                job.join()
            }
        }
    }
}
