package takagi.ru.monica.steam.foundation.release

/**
 * Public release metadata for the standalone Etoile application.
 *
 * Keep this small object free of secrets so UI, update checks, and diagnostics
 * cannot drift to a different repository by accident.
 */
object SteamReleaseConfig {
    const val repositoryOwner = "JoyinJoester"
    const val repositoryName = "Etoile"
    const val repositoryUrl = "https://github.com/JoyinJoester/Etoile"
    const val issuesUrl = "https://github.com/JoyinJoester/Etoile/issues"
    const val releasesUrl = "https://github.com/JoyinJoester/Etoile/releases"
    const val latestReleaseApiUrl =
        "https://api.github.com/repos/JoyinJoester/Etoile/releases/latest"
    const val updateUserAgent = "Etoile"

    private val knownAbiTokens = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86"
    )

    /**
     * Select an APK that matches the device ABI while keeping a universal
     * fallback for releases that provide one.
     */
    internal fun selectReleaseApkAssetName(
        assetNames: List<String>,
        supportedAbis: List<String>
    ): String? {
        val apkNames = assetNames.filter { it.endsWith(".apk", ignoreCase = true) }
        if (apkNames.isEmpty()) return null

        supportedAbis.firstNotNullOfOrNull { abi ->
            val token = abi.trim().lowercase()
            if (token.isBlank()) {
                null
            } else {
                val pattern = Regex("(^|[-_.])${Regex.escape(token)}([-_.]|$)")
                apkNames.firstOrNull { name -> pattern.containsMatchIn(name.lowercase()) }
            }
        }?.let { return it }

        apkNames.firstOrNull { name ->
            name.contains("universal", ignoreCase = true)
        }?.let { return it }

        // A single ABI-neutral APK is commonly published without the word
        // "universal". It is safe to use only when the filename does not
        // identify a different architecture; never guess by taking the first
        // ABI-specific asset from a release.
        val neutralAssets = apkNames.filter { name ->
            knownAbiTokens.none { abi ->
                Regex("(^|[-_.])${Regex.escape(abi)}([-_.]|$)")
                    .containsMatchIn(name.lowercase())
            }
        }
        return neutralAssets.singleOrNull()
    }
}
