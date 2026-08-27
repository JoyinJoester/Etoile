package takagi.ru.monica.steam.library.screenshots.domain

internal data class SteamGameScreenshot(
    val publishedFileId: String,
    val appId: Int,
    val thumbnailUrl: String,
    val imageUrl: String,
    val aspectRatio: Float
)

internal data class SteamGameScreenshotsBatch(
    val screenshots: List<SteamGameScreenshot>,
    val hasMore: Boolean
)
