package takagi.ru.monica.github.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.github.domain.GithubCacheFallbackMonitor
import takagi.ru.monica.github.domain.GithubCacheFallbackSnapshot
import java.io.IOException
import java.security.MessageDigest

/** A serialized GET response kept separately from the access token store. */
data class GithubCachedResponse(
    val body: String,
    val linkHeader: String?,
    val etag: String?,
    val savedAtEpochMillis: Long
)

/**
 * Small storage boundary so repositories do not depend on Android preferences
 * and can use an in-memory implementation in unit tests.
 */
interface GithubCacheStore {
    fun read(key: String): GithubCachedResponse?
    fun write(key: String, response: GithubCachedResponse)
    fun clear()
}

object NoOpGithubCacheStore : GithubCacheStore {
    override fun read(key: String): GithubCachedResponse? = null
    override fun write(key: String, response: GithubCachedResponse) = Unit
    override fun clear() = Unit
}

interface GithubCacheStatusReporter {
    fun onFallback(cacheKey: String, cachedAtEpochMillis: Long, detectedAtEpochMillis: Long)
    fun onValidated(cacheKey: String)
    fun clear()
}

object NoOpGithubCacheStatusReporter : GithubCacheStatusReporter {
    override fun onFallback(cacheKey: String, cachedAtEpochMillis: Long, detectedAtEpochMillis: Long) = Unit
    override fun onValidated(cacheKey: String) = Unit
    override fun clear() = Unit
}

/** Tracks cache fallbacks per request key without exposing cache keys to UI. */
class GithubCacheFallbackStore : GithubCacheStatusReporter, GithubCacheFallbackMonitor {
    private val lock = Any()
    private val fallbacks = LinkedHashMap<String, GithubCacheFallbackSnapshot>()
    private val _state = MutableStateFlow<GithubCacheFallbackSnapshot?>(null)
    override val state: StateFlow<GithubCacheFallbackSnapshot?> = _state.asStateFlow()

    override fun onFallback(
        cacheKey: String,
        cachedAtEpochMillis: Long,
        detectedAtEpochMillis: Long
    ) {
        synchronized(lock) {
            fallbacks[cacheKey] = GithubCacheFallbackSnapshot(
                cachedAtEpochMillis = cachedAtEpochMillis,
                detectedAtEpochMillis = detectedAtEpochMillis
            )
            publishLatest()
        }
    }

    override fun onValidated(cacheKey: String) {
        synchronized(lock) {
            fallbacks.remove(cacheKey)
            publishLatest()
        }
    }

    override fun clear() {
        synchronized(lock) {
            fallbacks.clear()
            _state.value = null
        }
    }

    private fun publishLatest() {
        _state.value = fallbacks.values.maxWithOrNull(
            compareBy<GithubCacheFallbackSnapshot> { it.detectedAtEpochMillis }
                .thenBy { it.cachedAtEpochMillis }
        )
    }
}

/** Adds invalidation callbacks while keeping the storage implementation focused. */
class GithubInvalidatingCacheStore(
    private val delegate: GithubCacheStore,
    private val onInvalidated: () -> Unit
) : GithubCacheStore {
    override fun read(key: String): GithubCachedResponse? = delegate.read(key)

    override fun write(key: String, response: GithubCachedResponse) {
        delegate.write(key, response)
    }

    override fun clear() {
        delegate.clear()
        onInvalidated()
    }
}

object GithubCacheKeys {
    fun endpoint(namespace: String, scope: String, url: String): String =
        "$namespace:$scope:$url"
}

fun Request.Builder.withCacheValidator(etag: String?): Request.Builder = apply {
    if (!etag.isNullOrBlank()) header("If-None-Match", etag)
}

inline fun <T> GithubCacheStore.invalidateAfter(block: () -> T): T {
    val value = block()
    clear()
    return value
}

/** Encrypted on-device cache for non-token GitHub response bodies. */
class GithubEncryptedCacheStore(context: Context) : GithubCacheStore {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFERENCES_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(key: String): GithubCachedResponse? {
        val prefix = keyPrefix(key)
        val body = preferences.getString("$prefix.body", null) ?: return null
        return GithubCachedResponse(
            body = body,
            linkHeader = preferences.getString("$prefix.link", null),
            etag = preferences.getString("$prefix.etag", null),
            savedAtEpochMillis = preferences.getLong("$prefix.savedAt", 0L)
        )
    }

    override fun write(key: String, response: GithubCachedResponse) {
        // SharedPreferences is not a database; reject unexpectedly large
        // payloads instead of risking memory pressure or a broken XML file.
        if (response.body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) return
        val prefix = keyPrefix(key)
        preferences.edit()
            .putString("$prefix.body", response.body)
            .putString("$prefix.link", response.linkHeader)
            .putString("$prefix.etag", response.etag)
            .putLong("$prefix.savedAt", response.savedAtEpochMillis)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "etoile_github_response_cache"
        const val MAX_BODY_BYTES = 512 * 1024
    }
}

/**
 * Executes a cacheable GET while preserving HTTP validators and safe fallback
 * rules. A caller still owns JSON decoding, so this helper stays independent
 * of any feature DTO.
 */
class GithubCachedGetExecutor(
    private val store: GithubCacheStore,
    private val statusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() }
) {
    fun <T> execute(
        client: OkHttpClient,
        cacheKey: String,
        request: (etag: String?) -> Request,
        decode: (body: String, linkHeader: String?) -> T
    ): T {
        val cached = store.read(cacheKey)
        val preparedRequest = request(cached?.etag)
        return try {
            client.newCall(preparedRequest).execute().use { response ->
                when {
                    response.code == 304 && cached != null -> {
                        val decoded = decode(cached.body, cached.linkHeader)
                        statusReporter.onValidated(cacheKey)
                        decoded
                    }
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        val linkHeader = response.header("Link")
                        val decoded = decode(body, linkHeader)
                        store.write(
                            cacheKey,
                            GithubCachedResponse(
                                body = body,
                                linkHeader = linkHeader,
                                etag = response.header("ETag"),
                                savedAtEpochMillis = nowEpochMillis()
                            )
                        )
                        statusReporter.onValidated(cacheKey)
                        decoded
                    }
                    response.code >= 500 && cached != null -> {
                        val decoded = decode(cached.body, cached.linkHeader)
                        statusReporter.onFallback(
                            cacheKey = cacheKey,
                            cachedAtEpochMillis = cached.savedAtEpochMillis,
                            detectedAtEpochMillis = nowEpochMillis()
                        )
                        decoded
                    }
                    else -> throw GithubApiException(response.code)
                }
            }
        } catch (networkError: IOException) {
            cached?.let {
                val decoded = decode(it.body, it.linkHeader)
                statusReporter.onFallback(
                    cacheKey = cacheKey,
                    cachedAtEpochMillis = it.savedAtEpochMillis,
                    detectedAtEpochMillis = nowEpochMillis()
                )
                return decoded
            }
            throw networkError
        }
    }
}

fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun keyPrefix(key: String): String = "k_${sha256Hex(key)}"
