package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

private const val STEAM_IMAGE_TIMEOUT_MS = 4_000
private const val STEAM_IMAGE_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L
private const val STEAM_IMAGE_MAX_ENTRY_BYTES = 24L * 1024L * 1024L
private const val STEAM_IMAGE_MAX_CACHE_BYTES = 64L * 1024L * 1024L
private const val STEAM_IMAGE_MAX_REDIRECTS = 4
private const val STEAM_IMAGE_MAX_DECODE_DIMENSION = 2_048
private const val STEAM_IMAGE_MAX_DECODE_PIXELS = 4_194_304L
private const val STEAM_IMAGE_MAX_SOURCE_DIMENSION = 4_096
private const val STEAM_IMAGE_MAX_SOURCE_PIXELS = 16_777_216L
// v2 intentionally bypasses bytes cached by the old bitmap/thumbnail path.
// Chat stickers must retain their original APNG container, not a first-frame
// derivative that may already be present on an upgraded installation.
private const val STEAM_IMAGE_CACHE_VERSION = "v2"
private val steamImageCacheLock = Any()

internal suspend fun loadSteamRemoteImage(context: Context, imageUrl: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        loadSteamRemoteBytesBlocking(context, imageUrl)
            ?.let(::decodeSteamRemoteBitmap)
            ?.asImageBitmap()
    }

/** Loads the original CDN bytes so animated WebP/GIF stickers are not flattened. */
internal suspend fun loadSteamRemoteBytes(context: Context, imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) { loadSteamRemoteBytesBlocking(context, imageUrl) }

private fun loadSteamRemoteBytesBlocking(context: Context, imageUrl: String): ByteArray? {
    val normalizedUrl = normalizeSteamImageUrl(imageUrl)
    if (!SteamRemoteImageCache.isAllowedSteamImageUrl(normalizedUrl)) return null

    val cacheFile = steamRemoteImageCacheFileForNormalizedUrl(context, normalizedUrl)
    val cached = synchronized(steamImageCacheLock) { readSteamImageCacheEntry(cacheFile) }
    if (cached != null && !isSteamRemoteImageCacheExpired(cacheFile)) {
        cacheFile.setLastModified(System.currentTimeMillis())
        return cached
    }

    val downloaded = runCatching { downloadSteamRemoteImageBytes(normalizedUrl) }.getOrNull()
    if (downloaded != null) {
        synchronized(steamImageCacheLock) {
            writeSteamImageCacheEntry(cacheFile, downloaded)
            pruneSteamImageCache(cacheFile.parentFile, cacheFile.name)
        }
        return downloaded
    }
    return cached
}

internal fun normalizeSteamImageUrl(imageUrl: String): String {
    val trimmed = imageUrl.trim()
    val normalized = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://steamcommunity.com$trimmed"
        else -> trimmed
    }
    return normalized.replace(
        oldValue = "https://steamcommunity.com/economy/",
        newValue = "https://community.cloudflare.steamstatic.com/economy/",
        ignoreCase = true
    )
}

private fun downloadSteamRemoteImageBytes(initialUrl: String): ByteArray? {
    var currentUrl = initialUrl
    var redirectCount = 0
    while (redirectCount <= STEAM_IMAGE_MAX_REDIRECTS) {
        if (!SteamRemoteImageCache.isAllowedSteamImageUrl(currentUrl)) return null
        val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = STEAM_IMAGE_TIMEOUT_MS
            readTimeout = STEAM_IMAGE_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/png,image/*;q=0.8")
            setRequestProperty("User-Agent", "Etoile/Android")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode in REDIRECT_STATUS_CODES) {
                val location = connection.getHeaderField("Location") ?: return null
                currentUrl = URL(URL(currentUrl), location).toString()
                redirectCount++
                continue
            }
            if (responseCode !in 200..299) return null
            if (connection.contentLengthLong > STEAM_IMAGE_MAX_ENTRY_BYTES) return null
            return connection.inputStream.use(::readBoundedSteamImageBytes)
        } finally {
            connection.disconnect()
        }
    }
    return null
}

private fun readBoundedSteamImageBytes(input: InputStream): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > STEAM_IMAGE_MAX_ENTRY_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray().takeIf(ByteArray::isNotEmpty)
}

internal fun steamRemoteImageCacheFile(context: Context, imageUrl: String): File {
    val normalizedUrl = normalizeSteamImageUrl(imageUrl)
    return steamRemoteImageCacheFileForNormalizedUrl(context, normalizedUrl)
}

private fun steamRemoteImageCacheFileForNormalizedUrl(context: Context, imageUrl: String): File =
    File(
        File(context.cacheDir, "steam_confirmation_images_$STEAM_IMAGE_CACHE_VERSION"),
        "${steamRemoteImageCacheKey(imageUrl)}.bin"
    )

internal fun steamRemoteImageCacheKey(imageUrl: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(imageUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun readSteamImageCacheEntry(cacheFile: File): ByteArray? {
    if (!cacheFile.isFile || cacheFile.length() !in 1..STEAM_IMAGE_MAX_ENTRY_BYTES) {
        cacheFile.delete()
        return null
    }
    return runCatching { cacheFile.readBytes() }
        .getOrNull()
        ?.takeIf(ByteArray::isNotEmpty)
        ?: run {
            cacheFile.delete()
            null
        }
}

private fun writeSteamImageCacheEntry(cacheFile: File, bytes: ByteArray) {
    if (bytes.isEmpty() || bytes.size.toLong() > STEAM_IMAGE_MAX_ENTRY_BYTES) return
    cacheFile.parentFile?.mkdirs()
    val temporaryFile = File(
        requireNotNull(cacheFile.parentFile),
        "${cacheFile.name}.${System.nanoTime()}.tmp"
    )
    try {
        temporaryFile.writeBytes(bytes)
        if (cacheFile.exists()) cacheFile.delete()
        if (!temporaryFile.renameTo(cacheFile)) cacheFile.writeBytes(bytes)
    } finally {
        temporaryFile.delete()
    }
}

private fun pruneSteamImageCache(directory: File?, protectedName: String) {
    val entries = directory?.listFiles().orEmpty().map { file ->
        SteamRemoteImageCacheEntry(
            name = file.name,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            temporary = file.name.endsWith(".tmp")
        )
    }
    steamRemoteImageCacheEvictions(
        entries = entries,
        protectedName = protectedName,
        nowMillis = System.currentTimeMillis()
    ).forEach { name -> File(directory, name).delete() }
}

internal data class SteamRemoteImageCacheEntry(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val temporary: Boolean = false
)

internal fun steamRemoteImageCacheEvictions(
    entries: List<SteamRemoteImageCacheEntry>,
    protectedName: String,
    nowMillis: Long,
    maximumCacheBytes: Long = STEAM_IMAGE_MAX_CACHE_BYTES,
    maximumEntryBytes: Long = STEAM_IMAGE_MAX_ENTRY_BYTES,
    ttlMillis: Long = STEAM_IMAGE_CACHE_TTL_MS
): Set<String> {
    val evictions = linkedSetOf<String>()
    val validEntries = entries.filter { entry ->
        val invalid = entry.temporary || entry.sizeBytes !in 1..maximumEntryBytes
        if (invalid) evictions += entry.name
        !invalid
    }
    validEntries
        .filter { entry ->
            entry.name != protectedName && nowMillis - entry.lastModifiedMillis > ttlMillis
        }
        .forEach { entry -> evictions += entry.name }
    var total = validEntries
        .filterNot { it.name in evictions }
        .sumOf(SteamRemoteImageCacheEntry::sizeBytes)
    validEntries
        .filterNot { it.name == protectedName || it.name in evictions }
        .sortedBy(SteamRemoteImageCacheEntry::lastModifiedMillis)
        .forEach { entry ->
            if (total <= maximumCacheBytes) return@forEach
            evictions += entry.name
            total -= entry.sizeBytes
        }
    return evictions
}

private fun isSteamRemoteImageCacheExpired(cacheFile: File): Boolean =
    !cacheFile.isFile ||
        System.currentTimeMillis() - cacheFile.lastModified() > STEAM_IMAGE_CACHE_TTL_MS

internal fun decodeSteamRemoteBitmap(payload: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > STEAM_IMAGE_MAX_DECODE_DIMENSION ||
        bounds.outHeight / sampleSize > STEAM_IMAGE_MAX_DECODE_DIMENSION ||
        (bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) >
        STEAM_IMAGE_MAX_DECODE_PIXELS
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        payload,
        0,
        payload.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
}

internal fun isSafeSteamRemoteImagePayload(payload: ByteArray): Boolean {
    if (payload.isEmpty() || payload.size.toLong() > STEAM_IMAGE_MAX_ENTRY_BYTES) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
    return bounds.outWidth in 1..STEAM_IMAGE_MAX_SOURCE_DIMENSION &&
        bounds.outHeight in 1..STEAM_IMAGE_MAX_SOURCE_DIMENSION &&
        bounds.outWidth.toLong() * bounds.outHeight <= STEAM_IMAGE_MAX_SOURCE_PIXELS
}

private val REDIRECT_STATUS_CODES = setOf(
    HttpURLConnection.HTTP_MOVED_PERM,
    HttpURLConnection.HTTP_MOVED_TEMP,
    HttpURLConnection.HTTP_SEE_OTHER,
    307,
    308
)
