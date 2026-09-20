package takagi.ru.monica.github.data

import android.content.Context
import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.github.domain.FdroidApp
import takagi.ru.monica.github.domain.FdroidSource
import takagi.ru.monica.github.domain.FdroidSources
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

class GithubFdroidIndexRepository(
    private val context: Context,
    private val client: OkHttpClient = GithubNetwork.client.newBuilder().callTimeout(3, java.util.concurrent.TimeUnit.MINUTES).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
    private val indexUrl: String = "https://f-droid.org/repo/index-v1.jar",
    private val repoBaseUrl: String = "https://f-droid.org/repo/"
) {
    suspend fun loadCatalog(limit: Int = Int.MAX_VALUE): Result<List<FdroidApp>> =
        loadSource(FdroidSources.builtIn.first { it.url.trimEnd('/') == repoBaseUrl.trimEnd('/') }, limit)

    suspend fun loadSource(source: FdroidSource, limit: Int = Int.MAX_VALUE, forceRefresh: Boolean = false): Result<List<FdroidApp>> =
        withContext(Dispatchers.IO) {
            locks.getOrPut(source.url) { Mutex() }.withLock {
            githubRunCatching {
                val dir = File(context.cacheDir, "fdroid").apply { mkdirs() }
                val key = digest(source.url.toByteArray())
                val index = File(dir, "$key.jar")
                if (forceRefresh || !index.exists() || System.currentTimeMillis() - index.lastModified() > 6 * 60 * 60 * 1000L) {
                    val tmp = File(dir, "$key.tmp")
                    try {
                        client.newCall(Request.Builder().url(source.url.trimEnd('/') + "/index-v1.jar").build()).execute().use { response ->
                            if (!response.isSuccessful) throw GithubApiException.of(response)
                            response.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        }
                        verify(tmp, source)
                        check(tmp.renameTo(index))
                    } finally { tmp.delete() }
                }
                verify(index, source)
                JarFile(index, true).use { jar ->
                    val entry = jar.getJarEntry("index-v1.json") ?: error("Missing index")
                    jar.getInputStream(entry).bufferedReader().use { parse(JsonReader(it), source).take(limit) }
                }
            }
        }

    }

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private fun verify(file: File, source: FdroidSource) {
        JarFile(file, true).use { jar ->
            val entry = jar.getJarEntry("index-v1.json") ?: error("Missing index")
            // Reading the entire entry is required before JarFile verifies signatures.
            jar.getInputStream(entry).use { input ->
                val buffer = ByteArray(8192)
                while (input.read(buffer) != -1) { /* verify signed content */ }
            }
            check(entry.certificates.orEmpty().any { digest(it.encoded).equals(source.fingerprint, true) }) {
                "Repository signing certificate mismatch"
            }
        }
    }

    private data class Info(val id: String, val name: String, val summary: String?, val description: String?, val project: String?, val icon: String?)
    private fun parse(reader: JsonReader, source: FdroidSource): List<FdroidApp> {
        val apps = linkedMapOf<String, Info>()
        val packages = linkedMapOf<String, FdroidApp>()
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "apps" -> {
                reader.beginArray()
                while (reader.hasNext()) readApp(reader)?.let { apps[it.id] = it }
                reader.endArray()
            }
            "packages" -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val id = reader.nextName()
                    readPackage(reader, source)?.let { packages[id] = it.copy(packageName = id) }
                }
                reader.endObject()
            }
            else -> reader.skipValue()
        }
        reader.endObject()
        return apps.values.mapNotNull { info -> packages[info.id]?.copy(name = info.name,
            summary = info.summary, description = info.description, projectUrl = info.project,
            iconUrl = info.icon?.let { (source.url.trimEnd('/') + "/icons/").toHttpUrl().newBuilder().addPathSegment(it).build().toString() }) }
    }

    private fun readApp(r: JsonReader): Info? {
        var id: String? = null; var name: String? = null; var summary: String? = null
        var description: String? = null; var project: String? = null; var icon: String? = null
        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
            when (key) {
                "packageName" -> id = r.nextString()
                "name" -> name = r.nextString()
                "summary" -> summary = r.nextString()
                "description" -> description = r.nextString()
                "sourceCode" -> project = r.nextString()
                "icon" -> icon = r.nextString()
                "localized" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        val locale = r.nextName()
                        if (locale !in setOf("en-US", "en", "zh-CN")) { r.skipValue(); continue }
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "name" -> name = r.nextString()
                            "summary" -> summary = r.nextString()
                            "description" -> description = r.nextString()
                            else -> r.skipValue()
                        }
                        r.endObject()
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return id?.let { Info(it, name?.trim()?.takeIf(String::isNotBlank) ?: it, summary?.trim(), description, project, icon) }
    }

    private fun readPackage(r: JsonReader, source: FdroidSource): FdroidApp? {
        var best: FdroidApp? = null
        r.beginArray()
        while (r.hasNext()) {
            var version: String? = null; var code = -1L; var apk: String? = null
            var size: Long? = null; var min = 1; var max = Int.MAX_VALUE
            var hash: String? = null; var hashType: String? = null
            val abis = mutableListOf<String>()
            r.beginObject()
            while (r.hasNext()) {
                val key = r.nextName()
                if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
                when (key) {
                    "versionName" -> version = r.nextString()
                    "versionCode" -> code = r.nextLong()
                    "apkName" -> apk = r.nextString()
                    "size" -> size = r.nextLong()
                    "minSdkVersion" -> min = r.nextInt()
                    "maxSdkVersion" -> max = r.nextInt().takeIf { it > 0 } ?: Int.MAX_VALUE
                    "hash" -> hash = r.nextString()
                    "hashType" -> hashType = r.nextString()
                    "nativecode" -> { r.beginArray(); while (r.hasNext()) abis += r.nextString(); r.endArray() }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            val compatible = Build.VERSION.SDK_INT in min..max &&
                (abis.isEmpty() || abis.any { it in Build.SUPPORTED_ABIS })
            if (compatible && apk?.endsWith(".apk", true) == true && code > (best?.versionCode ?: -1) &&
                hashType == "sha256" && hash?.matches(Regex("[0-9a-fA-F]{64}")) == true) {
                best = FdroidApp("", "", null, version, apk,
                    (source.url.trimEnd('/') + "/").toHttpUrl().newBuilder().addPathSegment(apk!!).build().toString(),
                    size, source.name, source.url, versionCode = code, sha256 = hash)
            }
        }
        r.endArray()
        return best
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02X".format(it) }
}
