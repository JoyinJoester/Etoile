package takagi.ru.monica.github.domain

data class GithubRelease(
    val id: Long,
    val tagName: String,
    val targetCommitish: String,
    val name: String?,
    val body: String?,
    val author: GithubUserSummary,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val createdAt: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val assets: List<GithubReleaseAsset>
) {
    val displayName: String get() = name?.takeIf(String::isNotBlank) ?: tagName
}

data class GithubReleaseAsset(
    val id: Long,
    val name: String,
    val label: String?,
    val contentType: String,
    val sizeBytes: Long,
    val downloadCount: Int,
    val createdAt: String,
    val downloadUrl: String
)

interface GithubReleasesRepository {
    suspend fun releases(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRelease>>

    suspend fun release(owner: String, name: String, releaseId: Long): Result<GithubRelease>

    suspend fun releaseByTag(owner: String, name: String, tagName: String): Result<GithubRelease>
}
