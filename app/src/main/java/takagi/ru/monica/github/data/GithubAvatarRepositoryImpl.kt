package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import takagi.ru.monica.github.domain.GithubAvatarRepository

/**
 * Public avatar bytes with bounded memory/disk caches and stale fallback.
 * Bitmap decoding stays in the UI layer so this repository remains replaceable.
 */
class GithubAvatarRepositoryImpl(
    private val client: OkHttpClient = GithubNetwork.client,
    private val cacheDirectory: File
) : GithubAvatarRepository {
    private val memory = ByteArrayLruCache(MEMORY_CACHE_BYTES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun bytes(url: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalized = normalizeAvatarUrl(url) ?: return@githubRunCatching null
            val cached = memory.get(normalized)
            if (cached != null) return@githubRunCatching cached

            val lock = locks.getOrPut(normalized) { Mutex() }
            try {
                lock.withLock {
                    memory.get(normalized)?.let { return@withLock it }
                    cacheDirectory.mkdirs()
                    val cacheFile = File(cacheDirectory, "${sha256Hex(normalized)}.img")
                    val now = System.currentTimeMillis()
                    val stale = readCache(cacheFile)
                    if (stale != null && now - cacheFile.lastModified() <= FRESH_CACHE_MILLIS) {
                        memory.put(normalized, stale)
                        return@withLock stale
                    }

                    val fresh = fetch(normalized)
                    val selected = fresh ?: stale
                    fresh?.let {
                        writeCache(cacheFile, it)
                        trimDiskCache(now)
                    }
                    selected?.also { memory.put(normalized, it) }
                }
            } finally {
                locks.remove(normalized, lock)
            }
        }
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Etoile-GitHub-Client")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            if (body.contentLength() > MAX_RESPONSE_BYTES) return@use null
            readLimited(body.byteStream(), MAX_RESPONSE_BYTES)
        }
    }.getOrNull()

    private fun readCache(file: File): ByteArray? {
        if (!file.isFile || file.length() !in 1..MAX_RESPONSE_BYTES.toLong()) return null
        return runCatching { file.readBytes() }.getOrNull()
            ?: run { file.delete(); null }
    }

    private fun writeCache(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                file.writeBytes(bytes)
                temporary.delete()
            }
        }.onFailure { temporary.delete() }
    }

    private fun trimDiskCache(now: Long) {
        val files = cacheDirectory.listFiles()?.filter(File::isFile).orEmpty()
        files.filter { now - it.lastModified() > MAX_CACHE_AGE_MILLIS }.forEach(File::delete)
        var retainedBytes = 0L
        files.filter(File::exists)
            .sortedByDescending(File::lastModified)
            .forEach { file ->
                retainedBytes += file.length()
                if (retainedBytes > DISK_CACHE_BYTES) file.delete()
            }
    }

    private companion object {
        const val MEMORY_CACHE_BYTES = 8 * 1024 * 1024
        const val DISK_CACHE_BYTES = 24L * 1024L * 1024L
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val FRESH_CACHE_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_CACHE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
        fun normalizeAvatarUrl(value: String): String? {
            val parsed = value.trim().takeIf(String::isNotEmpty)?.toHttpUrlOrNull() ?: return null
            return parsed.takeIf { it.scheme == "https" && it.host.isNotBlank() }?.toString()
        }

        fun readLimited(input: InputStream, limit: Int): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}

private class ByteArrayLruCache(private val maximumBytes: Int) {
    private val values = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var retainedBytes = 0

    @Synchronized
    fun get(key: String): ByteArray? = values[key]

    @Synchronized
    fun put(key: String, value: ByteArray) {
        retainedBytes -= values.put(key, value)?.size ?: 0
        retainedBytes += value.size
        val iterator = values.entries.iterator()
        while (retainedBytes > maximumBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            retainedBytes -= eldest.value.size
            iterator.remove()
        }
    }
}
