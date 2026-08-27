package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset

@Serializable
internal data class GithubReleaseDto(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    @SerialName("target_commitish") val targetCommitish: String = "",
    val name: String? = null,
    val body: String? = null,
    val author: GithubUserDto? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GithubReleaseAssetDto> = emptyList()
) {
    fun toDomain() = GithubRelease(
        id = id,
        tagName = tagName,
        targetCommitish = targetCommitish,
        name = name,
        body = body,
        author = author.toDomainOrGhost(),
        isDraft = draft,
        isPrerelease = prerelease,
        createdAt = createdAt,
        publishedAt = publishedAt,
        htmlUrl = htmlUrl,
        assets = assets.map(GithubReleaseAssetDto::toDomain)
    )
}

@Serializable
internal data class GithubReleaseAssetDto(
    val id: Long,
    val name: String,
    val label: String? = null,
    @SerialName("content_type") val contentType: String = "application/octet-stream",
    val size: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("browser_download_url") val downloadUrl: String
) {
    fun toDomain() = GithubReleaseAsset(
        id = id,
        name = name,
        label = label,
        contentType = contentType,
        sizeBytes = size.coerceAtLeast(0),
        downloadCount = downloadCount.coerceAtLeast(0),
        createdAt = createdAt,
        downloadUrl = downloadUrl
    )
}
