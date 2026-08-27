package takagi.ru.monica.steam.foundation.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider

internal sealed interface SteamImageDownloadResult {
    data class Success(val displayName: String) : SteamImageDownloadResult
    data object PermissionRequired : SteamImageDownloadResult
    data object InvalidSource : SteamImageDownloadResult
    data object UnsupportedImage : SteamImageDownloadResult
    data object TooLarge : SteamImageDownloadResult
    data object NetworkFailure : SteamImageDownloadResult
    data object StorageFailure : SteamImageDownloadResult
}

internal class SteamImageDownloader(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext

    fun requiresLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
    }

    suspend fun download(
        imageUrl: String,
        fileStem: String
    ): SteamImageDownloadResult = withContext(Dispatchers.IO) {
        if (!SteamImageDownloadPolicy.isAllowedUrl(imageUrl)) {
            logResult("invalid_source")
            return@withContext SteamImageDownloadResult.InvalidSource
        }
        if (requiresLegacyStoragePermission()) {
            return@withContext SteamImageDownloadResult.PermissionRequired
        }

        val request = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "Etoile/1.0")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful ||
                    !SteamImageDownloadPolicy.isAllowedUrl(response.request.url.toString())
                ) {
                    logResult("http_failure", "code=${response.code}")
                    return@withContext SteamImageDownloadResult.NetworkFailure
                }

                val body = response.body ?: run {
                    logResult("empty_body")
                    return@withContext SteamImageDownloadResult.NetworkFailure
                }
                val mimeType = SteamImageDownloadPolicy.normalizeMimeType(
                    body.contentType()?.toString()
                ) ?: run {
                    logResult("unsupported_image")
                    return@withContext SteamImageDownloadResult.UnsupportedImage
                }
                if (body.contentLength() > MAX_IMAGE_BYTES) {
                    logResult("too_large", "declared_bytes=${body.contentLength()}")
                    return@withContext SteamImageDownloadResult.TooLarge
                }

                val displayName = SteamImageDownloadPolicy.buildDisplayName(
                    fileStem = fileStem,
                    mimeType = mimeType,
                    timestampMillis = clock()
                )
                val bytesWritten = body.byteStream().use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveWithMediaStore(displayName, mimeType, input)
                    } else {
                        saveLegacy(displayName, mimeType, input)
                    }
                } ?: run {
                    logResult("storage_failure")
                    return@withContext SteamImageDownloadResult.StorageFailure
                }

                logResult("success", "bytes=$bytesWritten mime=$mimeType")
                SteamImageDownloadResult.Success(displayName)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: ImageTooLargeException) {
            logResult("too_large_stream")
            SteamImageDownloadResult.TooLarge
        } catch (error: Exception) {
            logResult("exception", "type=${error.javaClass.simpleName}")
            SteamImageDownloadResult.NetworkFailure
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(
        displayName: String,
        mimeType: String,
        input: InputStream
    ): Long? {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Etoile"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        var completed = false
        return try {
            val bytesWritten = resolver.openOutputStream(uri, "w")
                ?.buffered()
                ?.use { output -> copyWithLimit(input, output) }
                ?: return null
            if (bytesWritten <= 0L) return null
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            completed = true
            bytesWritten
        } finally {
            if (!completed) resolver.delete(uri, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        displayName: String,
        mimeType: String,
        input: InputStream
    ): Long? {
        val picturesRoot = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val destinationDirectory = File(picturesRoot, "Etoile")
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) return null

        val destination = uniqueDestination(destinationDirectory, displayName)
        val partial = File(destinationDirectory, ".${destination.name}.part")
        partial.delete()
        return try {
            val bytesWritten = partial.outputStream().buffered().use { output ->
                copyWithLimit(input, output)
            }
            if (bytesWritten <= 0L || !partial.renameTo(destination)) return null
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destination.absolutePath),
                arrayOf(mimeType),
                null
            )
            bytesWritten
        } finally {
            partial.delete()
        }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_IMAGE_BYTES) throw ImageTooLargeException()
            output.write(buffer, 0, count)
        }
        output.flush()
        return total
    }

    private fun uniqueDestination(directory: File, displayName: String): File {
        val direct = File(directory, displayName)
        if (!direct.exists()) return direct
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        val stem = displayName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
        var suffix = 2
        while (true) {
            val candidateName = if (extension.isBlank()) {
                "$stem ($suffix)"
            } else {
                "$stem ($suffix).$extension"
            }
            val candidate = File(directory, candidateName)
            if (!candidate.exists()) return candidate
            suffix += 1
        }
    }

    private fun logResult(result: String, details: String = "") {
        SteamDiagLogger.append(
            buildString {
                append("steam_image_download result=")
                append(result)
                if (details.isNotBlank()) {
                    append(' ')
                    append(details)
                }
            }
        )
    }

    private class ImageTooLargeException : Exception()

    private companion object {
        const val MAX_IMAGE_BYTES = 24L * 1024L * 1024L

        fun defaultClient(): OkHttpClient {
            return SteamHttpClientProvider.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }
    }
}
