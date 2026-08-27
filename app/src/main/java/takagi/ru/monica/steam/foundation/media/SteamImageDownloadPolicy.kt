package takagi.ru.monica.steam.foundation.media

import takagi.ru.monica.steam.profile.SteamRemoteImageCache

internal object SteamImageDownloadPolicy {
    private const val DEFAULT_MAX_FILE_STEM_LENGTH = 80
    private val invalidFilenameCharacters = Regex("""[\\/:*?"<>|\p{Cc}]""")
    private val repeatedSeparators = Regex("[_\\s]+")

    fun isAllowedUrl(rawUrl: String): Boolean =
        SteamRemoteImageCache.isAllowedSteamImageUrl(rawUrl)

    fun normalizeMimeType(rawMimeType: String?): String? {
        return when (rawMimeType.orEmpty().substringBefore(';').trim().lowercase()) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg"
            "image/png" -> "image/png"
            "image/webp" -> "image/webp"
            "image/gif" -> "image/gif"
            "image/avif" -> "image/avif"
            else -> null
        }
    }

    fun buildDisplayName(
        fileStem: String,
        mimeType: String,
        timestampMillis: Long,
        fallbackStem: String = "steam_image"
    ): String {
        val extension = when (normalizeMimeType(mimeType)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            else -> "jpg"
        }
        return buildString {
            append(safeFileStem(fileStem, fallbackStem, DEFAULT_MAX_FILE_STEM_LENGTH))
            append('_')
            append(timestampMillis.coerceAtLeast(0L))
            append('.')
            append(extension)
        }
    }

    fun safeFileStem(
        rawName: String,
        fallbackStem: String = "steam_image",
        maxLength: Int = DEFAULT_MAX_FILE_STEM_LENGTH
    ): String {
        val fallback = fallbackStem
            .replace(invalidFilenameCharacters, "_")
            .replace(repeatedSeparators, "_")
            .trim(' ', '.', '_')
            .ifBlank { "steam_image" }
        return rawName
            .replace(invalidFilenameCharacters, "_")
            .replace(repeatedSeparators, "_")
            .trim(' ', '.', '_')
            .take(maxLength.coerceAtLeast(1))
            .trim(' ', '.', '_')
            .ifBlank { fallback }
    }
}
