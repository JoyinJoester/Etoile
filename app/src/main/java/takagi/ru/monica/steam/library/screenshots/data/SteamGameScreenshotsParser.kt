package takagi.ru.monica.steam.library.screenshots.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import takagi.ru.monica.steam.foundation.media.SteamImageDownloadPolicy
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshot
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsBatch

internal object SteamGameScreenshotsParser {
    private const val COMMUNITY_BASE = "https://steamcommunity.com"
    private val backgroundImagePattern = Regex(
        pattern = """url\(\s*(['\"]?)(.*?)\1\s*\)""",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(html: String, expectedAppId: Int): SteamGameScreenshotsBatch {
        require(expectedAppId > 0) { "valid Steam app ID required" }
        val document = Jsoup.parse(html, COMMUNITY_BASE)
        val cards = document.select(
            "a.profile_media_item[data-publishedfileid][data-appid]"
        ).filter { card -> card.attr("data-appid").toIntOrNull() == expectedAppId }
        val screenshots = cards.mapNotNull { card ->
            parseScreenshot(card, expectedAppId)
        }.distinctBy(SteamGameScreenshot::publishedFileId)
        return SteamGameScreenshotsBatch(
            screenshots = screenshots,
            hasMore = cards.isNotEmpty() && document.selectFirst("#MoreContentForm") != null
        )
    }

    fun isAuthenticationPage(html: String): Boolean {
        val document = Jsoup.parse(html, COMMUNITY_BASE)
        return document.selectFirst(
            "form#login_form, form[action*=login][method=post], " +
                "input[name=username] + input[name=password]"
        ) != null || (
            document.selectFirst("input[name=username]") != null &&
                document.selectFirst("input[name=password]") != null
            )
    }

    private fun parseScreenshot(card: Element, expectedAppId: Int): SteamGameScreenshot? {
        val publishedFileId = card.attr("data-publishedfileid")
            .trim()
            .takeIf { value -> value.all(Char::isDigit) && value.isNotEmpty() }
            ?: return null
        val rawImageUrl = card.selectFirst(".imgWallItem")
            ?.attr("style")
            ?.let(backgroundImagePattern::find)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: card.selectFirst("img[src]")
                ?.absUrl("src")
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: return null
        val parsedImageUrl = normalizedSteamImageUrl(rawImageUrl) ?: return null
        val originalImageUrl = parsedImageUrl.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val thumbnailUrl = if (parsedImageUrl.queryParameterNames.isNotEmpty()) {
            parsedImageUrl.toString()
        } else {
            screenshotThumbnailUrl(parsedImageUrl)
        }
        val aspectRatio = card.attr("data-desired-aspect")
            .toFloatOrNull()
            ?.takeIf(Float::isFinite)
            ?.coerceIn(0.5f, 2.5f)
            ?: DEFAULT_ASPECT_RATIO
        return SteamGameScreenshot(
            publishedFileId = publishedFileId,
            appId = expectedAppId,
            thumbnailUrl = thumbnailUrl,
            imageUrl = originalImageUrl,
            aspectRatio = aspectRatio
        )
    }

    private fun normalizedSteamImageUrl(rawUrl: String): HttpUrl? {
        val normalized = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "$COMMUNITY_BASE$rawUrl"
            else -> rawUrl
        }
        if (!SteamImageDownloadPolicy.isAllowedUrl(normalized)) return null
        return normalized.toHttpUrlOrNull()
    }

    private fun screenshotThumbnailUrl(url: HttpUrl): String = url.newBuilder()
        .addQueryParameter("imw", "640")
        .addQueryParameter("ima", "fit")
        .addQueryParameter("impolicy", "Letterbox")
        .addQueryParameter("imcolor", "#000000")
        .addQueryParameter("letterbox", "false")
        .build()
        .toString()

    private const val DEFAULT_ASPECT_RATIO = 16f / 9f
}
