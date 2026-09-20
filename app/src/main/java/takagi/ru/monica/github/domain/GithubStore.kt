package takagi.ru.monica.github.domain

/**
 * 商店条目：一个 GitHub 仓库与其最新 Release。APK 资产从 Release 资产中筛选，
 * 并从文件名解析出可读的包信息(版本/ABI)。
 */
data class GithubStoreApp(
    val repository: GithubRepository,
    val latestRelease: GithubRelease? = null
) {
    val apkAssets: List<GithubReleaseAsset>
        get() = latestRelease?.assets?.filter { it.name.endsWith(".apk", ignoreCase = true) }
            ?: emptyList()
}

/** 从 APK 文件名解析出的软件包信息。 */
data class GithubApkPackage(
    val asset: GithubReleaseAsset,
    val version: String?,
    val abi: String?
) {
    val displayName: String get() = asset.name
}

/** 下载后用 PackageManager 从 APK 文件本体解析出的真实信息。 */
data class GithubApkDetails(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val appName: String?,
    val permissions: List<String>
)

/** 从 F-Droid 仓库索引(index-v1)解析出的应用条目。 */
data class FdroidApp(
    val packageName: String,
    val name: String,
    val summary: String?,
    val latestVersionName: String?,
    val apkName: String?,
    val apkUrl: String?,
    val sizeBytes: Long?,
    val sourceName: String = "F-Droid",
    val sourceUrl: String = "https://f-droid.org/repo",
    val projectUrl: String? = null,
    val description: String? = null,
    val versionCode: Long = 0,
    val sha256: String? = null,
    val iconUrl: String? = null
)

object GithubApkParser {

    private val abiTokens = listOf(
        "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "universal"
    )

    /**
     * 解析形如 "app-v1.2.3-arm64-v8a.apk" / "MyApp-2.0.0.apk" / "release.apk"
     * 的常见命名约定，提取版本号与 ABI。解析失败时字段为 null。
     */
    fun parse(asset: GithubReleaseAsset): GithubApkPackage {
        val base = asset.name.removeSuffix(".apk").removeSuffix(".APK")
        val abi = abiTokens.firstOrNull { base.contains(it, ignoreCase = true) }
        val version = Regex("""(?:v)?(\d+(?:\.\d+)+)""").find(base)?.groupValues?.get(1)
        return GithubApkPackage(asset = asset, version = version, abi = abi)
    }
}

fun List<GithubReleaseAsset>.toApkPackages(): List<GithubApkPackage> =
    filter { it.name.endsWith(".apk", ignoreCase = true) }.map(GithubApkParser::parse)

/** 用户自定义商店来源的输入归一化：接受 owner/name 或 GitHub 仓库链接。 */
object GithubStoreSource {
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        var text = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
        if (text.contains("github.com/")) {
            text = text.substringAfter("github.com/")
        }
        val parts = text.split("/")
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        val name = parts[1].trim()
        if (owner.isBlank() || name.isBlank()) return null
        // 排除 GitHub 站内路由路径，如 /topics/android、/search?q=...
        if (owner.lowercase() in setOf("topics", "search", "orgs", "features", "collections", "trending")) {
            return null
        }
        return "$owner/$name"
    }
}
