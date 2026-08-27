package takagi.ru.monica.steam.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.utils.BoundedLogExecutorFactory

/**
 * Persisted Steam diagnostics for login/import failures.
 *
 * Keep entries protocol-level only: no account names, SteamIDs, passwords,
 * tokens, secrets, confirmation codes, or raw payloads.
 */
object SteamDiagLogger {
    private const val TAG = "EtoileDiag"
    private const val LOG_DIR_NAME = "steam_logs"
    private const val LOG_FILE_NAME = "steam_diag_v1.log"
    private const val MAX_LOG_FILE_BYTES = 1024 * 1024L
    private const val ROTATE_KEEP_LINES = 4000

    private val fileLock = Any()
    private val writeExecutor = BoundedLogExecutorFactory.createSingleThreadExecutor("etoile-diag")

    @Volatile
    private var persistentLogFile: File? = null

    fun initialize(context: Context) {
        if (persistentLogFile != null) return
        synchronized(fileLock) {
            if (persistentLogFile != null) return
            runCatching {
                val logDir = File(context.applicationContext.filesDir, LOG_DIR_NAME)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val file = File(logDir, LOG_FILE_NAME)
                persistentLogFile = file
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val header = buildString {
                    appendLine("=== Etoile Diag Session ===")
                    appendLine("session_start=$time")
                    appendLine("app_version=${BuildConfig.FULL_VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("app_display_version=${BuildConfig.VERSION_NAME}")
                    appendLine("build_time=${BuildConfig.BUILD_TIME}")
                    appendLine("git_sha=${BuildConfig.GIT_SHA}")
                    appendLine("android_api=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("===")
                }
                file.appendText(header)
            }.onFailure {
                runCatching { Log.e(TAG, "Failed to initialize Steam diag logger", it) }
            }
        }
    }

    fun append(rawLine: String) {
        val file = persistentLogFile ?: return
        val sanitizedLine = sanitizeSteamDiagnosticLine(rawLine)
        if (sanitizedLine.isBlank()) return
        writeExecutor.execute {
            runCatching { Log.d(TAG, sanitizedLine) }
            synchronized(fileLock) {
                runCatching {
                    if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                        rotate(file)
                    }
                    file.appendText(sanitizedLine + "\n")
                }.onFailure {
                    runCatching { Log.e(TAG, "Failed to append Steam diag log", it) }
                }
            }
        }
    }

    fun exportPersistedLogs(maxEntries: Int = 2000): String {
        val file = persistentLogFile ?: return ""
        if (!file.exists()) return ""
        return synchronized(fileLock) {
            runCatching {
                file.readLines()
                    .takeLast(maxEntries.coerceAtLeast(1))
                    .joinToString(separator = "\n")
            }.getOrDefault("")
        }
    }

    fun clear() {
        synchronized(fileLock) {
            runCatching {
                persistentLogFile?.let { file ->
                    if (file.exists()) {
                        file.writeText("")
                    }
                }
            }
        }
    }

    private fun rotate(file: File) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val tail = runCatching {
            file.readLines().takeLast(ROTATE_KEEP_LINES)
        }.getOrElse { emptyList() }

        val output = buildString {
            appendLine("=== steam diag log rotated at $time ===")
            tail.forEach { appendLine(it) }
        }
        file.writeText(output)
    }

}

internal fun sanitizeSteamDiagnosticLine(rawText: String): String {
    var text = rawText
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
    text = BEARER_PATTERN.replace(text) { "Bearer <redacted>" }
    text = SECRET_ASSIGNMENT_PATTERN.replace(text) { match ->
        match.groupValues[1] + "<redacted>"
    }
    text = URL_SECRET_PATTERN.replace(text) { match ->
        match.groupValues[1] + "<redacted>"
    }
    return text
        .replace(STEAM_ID64_PATTERN, "<steamid64-redacted>")
        .replace(EMAIL_PATTERN, "<email-redacted>")
        .replace(JWT_PATTERN, "<token-redacted>")
        .replace(LONG_TOKEN_PATTERN, "<token-redacted>")
        .replace(BASE64_PATTERN, "<token-redacted>")
        .take(MAX_DIAGNOSTIC_LINE_CHARS)
}

private val BEARER_PATTERN = Regex(
    "\\bBearer\\s+[A-Za-z0-9._~+/=-]+",
    RegexOption.IGNORE_CASE
)
private val SECRET_ASSIGNMENT_PATTERN = Regex(
    """(?i)([\"']?(?:steamid|account|user|username|accountName|account_name|password|pwd|passwd|token|access_token|refresh_token|shared_secret|identity_secret|secret_1|code|sessionid|steamLoginSecure|authorization|cookie|api_key|oauth_token|account_key)[\"']?\s*[:=]\s*[\"']?)[^\"',}\s&]+"""
)
private val URL_SECRET_PATTERN = Regex(
    """(?i)([?&](?:access_token|refresh_token|token|sessionid|auth|key|api_key|oauth_token)=)[^&#\s]+"""
)
private val STEAM_ID64_PATTERN = Regex("\\b7656119\\d{10}\\b")
private val EMAIL_PATTERN = Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
private val JWT_PATTERN = Regex("\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
private val LONG_TOKEN_PATTERN = Regex("\\b[A-Za-z0-9]{28,}\\b")
private val BASE64_PATTERN = Regex("[A-Za-z0-9+/]{40,}={0,2}")
private const val MAX_DIAGNOSTIC_LINE_CHARS = 1_200
