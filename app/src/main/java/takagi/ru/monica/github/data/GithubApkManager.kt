package takagi.ru.monica.github.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.github.domain.GithubApkDetails
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 商店的 APK 下载/解析/安装链路：
 * 1. OkHttp 下载到私有 apks 目录并回报进度；
 * 2. PackageManager.getPackageArchiveInfo 解析出真实包名/版本/权限；
 * 3. 通过 FileProvider 生成拉起系统安装器的 Intent。
 */
class GithubApkManager(
    private val context: Context,
    private val client: OkHttpClient = GithubNetwork.client.newBuilder().followSslRedirects(false).callTimeout(10, java.util.concurrent.TimeUnit.MINUTES).readTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build()
) {
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    val supportedAbis: List<String> get() = android.os.Build.SUPPORTED_ABIS.toList()

    fun installedVersionCode(packageName: String): Long? = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(packageName, 0))
    }.getOrNull()

    private val apksDir: File
        get() = File(context.filesDir, "apks").apply { mkdirs() }

    /** 下载 APK,[onProgress] 回报 0f..1f(按 1% 步进节流,避免高频重组)。 */
    suspend fun download(url: String, fileName: String, expectedSha256: String? = null, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            require(request.url.isHttps && request.url.username.isEmpty() && request.url.password.isEmpty())
            require(fileName.isNotBlank() && fileName.length <= 240 && fileName !in setOf(".", "..") &&
                fileName.none { it == '/' || it == '\\' || it.isISOControl() })
            require(expectedSha256 == null || expectedSha256.matches(Regex("[0-9a-fA-F]{64}")))
            downloadLocks.getOrPut(fileName) { Mutex() }.withLock {
                val target = File(apksDir, fileName)
                val staged = File.createTempFile("download-", ".part", apksDir)
                val context = currentCoroutineContext()
                try {
                    client.newCall(request).readAuthResponse { response ->
                        try {
                            context.ensureActive()
                            if (!response.isSuccessful) throw GithubApiException.of(response)
                            val body = response.body ?: throw GithubApiException.of(response)
                            val total = body.contentLength()
                            val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
                            body.byteStream().use { input ->
                                staged.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var written = 0L
                                    var lastReported = 0f
                                    while (true) {
                                        context.ensureActive()
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        output.write(buffer, 0, read)
                                        digest?.update(buffer, 0, read)
                                        written += read
                                        if (total > 0) {
                                            val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
                                            if (fraction - lastReported >= PROGRESS_STEP && fraction < 1f) {
                                                lastReported = fraction
                                                onProgress(fraction)
                                            }
                                        }
                                    }
                                    if (written == 0L || (total >= 0 && written != total)) throw IOException("Incomplete APK download")
                                    output.flush()
                                }
                            }
                            context.ensureActive()
                            if (digest != null && !digest.digest().joinToString("") { "%02x".format(it) }.equals(expectedSha256, true)) {
                                throw IOException("APK checksum mismatch")
                            }
                        } catch (error: Exception) {
                            // The callback may finish after cancellation has already resumed the caller.
                            staged.delete()
                            throw error
                        }
                    }
                    context.ensureActive()
                    check(staged.renameTo(target)) { "Could not commit APK download" }
                    onProgress(1f)
                    target
                } finally {
                    staged.delete()
                }
            }
        }

    /** 用 PackageManager 解析 APK 文件本体；损坏的包返回 null。 */
    fun parse(file: File): GithubApkDetails? = runCatching {
        val pm = context.packageManager
        val packageInfo: PackageInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return null
        GithubApkDetails(
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            minSdk = packageInfo.applicationInfo?.let { appInfo ->
                runCatching { appInfo.minSdkVersion }.getOrNull()?.takeIf { it > 0 }
            },
            targetSdk = packageInfo.applicationInfo?.let { appInfo ->
                runCatching { appInfo.targetSdkVersion }.getOrNull()?.takeIf { it > 0 }
            },
            appName = resolveAppName(pm, file, packageInfo.packageName),
            permissions = resolvePermissions(pm, file)
        )
    }.getOrNull()

    private fun resolveAppName(pm: PackageManager, file: File, packageName: String): String? =
        runCatching {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
                ?.applicationInfo
                ?.let { appInfo ->
                    appInfo.sourceDir = file.absolutePath
                    appInfo.publicSourceDir = file.absolutePath
                    pm.getApplicationLabel(appInfo).toString()
                }
        }.getOrNull() ?: packageName

    private fun resolvePermissions(pm: PackageManager, file: File): List<String> =
        runCatching {
            // GET_PERMISSIONS 只能通过隐藏的 archive 路径拿到，此处读取 manifest 权限摘要。
            val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            info?.requestedPermissions?.toList().orEmpty().distinct()
        }.getOrDefault(emptyList())

    /** 系统安装器 Intent,APK 位于私有目录所以必须走 FileProvider。 */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 已安装包的版本名,未安装返回 null(用于"已安装/可更新"徽标)。 */
    fun installedVersion(packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun cachedApk(fileName: String): File? =
        File(apksDir, fileName).takeIf { it.exists() && it.length() > 0 }
}

private const val PROGRESS_STEP = 0.01f
