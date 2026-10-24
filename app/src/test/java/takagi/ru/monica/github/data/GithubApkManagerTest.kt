package takagi.ru.monica.github.data

import android.content.ContextWrapper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GithubApkManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val requests = AtomicInteger()
    private fun manager(body: ResponseBody, status: Int = 200): GithubApkManager {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temporary.root
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response").body(body).build()
        }.build()
        return GithubApkManager(context, client)
    }

    @Test fun verifiedBytesAreCommittedOnlyAfterTheChecksumPasses() = runBlocking {
        val payload = "verified download".repeat(2000)
        val progress = mutableListOf<Float>()
        val manager = manager(TrackingBody(payload))
        val file = manager.download("https://download.example/package", "package.bin", sha256Hex(payload)) {
            progress += it
            if (it < 1f) assertNull(manager.cachedApk("package.bin"))
        }
        assertEquals(payload, file.readText())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> after > before })
        assertTrue(temporary.root.resolve("apks").listFiles()!!.none { it.extension == "part" })
    }

    @Test fun checksumFailurePreservesThePreviousDownloadAndRemovesPartialBytes() = runBlocking {
        val directory = temporary.root.resolve("apks").apply { mkdirs() }
        val previous = directory.resolve("package.bin").apply { writeText("previous verified bytes") }
        val manager = manager(TrackingBody("untrusted bytes"))
        assertTrue(runCatching {
            manager.download("https://download.example/package", "package.bin", "0".repeat(64)) {}
        }.isFailure)
        assertEquals("previous verified bytes", previous.readText())
        assertEquals(listOf("package.bin"), directory.listFiles()!!.map { it.name })
    }

    @Test fun truncatedAndEmptyResponsesNeverBecomeInstallableFiles() = runBlocking {
        for (body in listOf(TrackingBody("partial", reportedLength = 500), TrackingBody(""))) {
            val manager = manager(body)
            assertTrue(runCatching { manager.download("https://download.example/package", "package.bin") {} }.isFailure)
            assertNull(manager.cachedApk("package.bin"))
            assertTrue(temporary.root.resolve("apks").listFiles().orEmpty().isEmpty())
            assertTrue(body.closed)
        }
    }

    @Test fun cancellationStopsTheBodyReadAndRemovesPartialBytes() = runBlocking {
        val body = TrackingBody("download".repeat(100_000))
        val manager = manager(body)
        lateinit var download: Job
        download = launch(start = CoroutineStart.LAZY) {
            manager.download("https://download.example/package", "cancelled.bin") { if (it in 0.01f..0.99f) download.cancel() }
        }
        download.start()
        download.join()
        withTimeout(5000) {
            while (!body.closed || temporary.root.resolve("apks").listFiles().orEmpty().isNotEmpty()) delay(10)
        }
        assertTrue(download.isCancelled)
        assertNull(manager.cachedApk("cancelled.bin"))
    }

    @Test fun unsafeDestinationsAndInsecureUrlsAreRejectedBeforeAnyRequest() = runBlocking {
        val manager = manager(TrackingBody("unused"))
        for (name in listOf("../escape.bin", "a/b.bin", "a\\b.bin", ".", "..", "\nfile")) {
            assertTrue(name, runCatching { manager.download("https://download.example/package", name) {} }.isFailure)
        }
        for (url in listOf("http://download.example/package", "https://user:password@download.example/package")) {
            assertTrue(runCatching { manager.download(url, "package.bin") {} }.isFailure)
        }
        assertTrue(runCatching { manager.download("https://download.example/package", "package.bin", "bad checksum") {} }.isFailure)
        assertEquals(0, requests.get())
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    private class TrackingBody(payload: String, private val reportedLength: Long = payload.toByteArray().size.toLong()) : ResponseBody() {
        @Volatile var closed = false
        private val source = object : ForwardingSource(Buffer().writeUtf8(payload)) {
            override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount.coerceAtMost(1024))
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = reportedLength
        override fun source(): BufferedSource = source
    }
}
