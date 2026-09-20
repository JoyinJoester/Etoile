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

/** Release metadata ready to be sent to GitHub, with the editor limits already checked. */
class GithubReleaseDraft private constructor(
    val tagName: String,
    val title: String,
    val body: String,
    val targetCommitish: String?,
    val isDraft: Boolean,
    val isPrerelease: Boolean
) {
    companion object {
        const val MAX_TITLE_LENGTH = 255
        const val MAX_BODY_LENGTH = 125_000

        fun fromInput(
            tagName: String,
            title: String,
            body: String,
            targetCommitish: String?,
            isDraft: Boolean,
            isPrerelease: Boolean
        ): Result<GithubReleaseDraft> = runCatching {
            val normalizedTitle = title.trim()
            val commitish = targetCommitish?.trim()?.takeIf(String::isNotEmpty)
            require(GithubGitRef.isValidTagName(tagName.trim())) { "Invalid tag name" }
            require(commitish == null || GithubGitRef.isValidBranchName(commitish)) { "Invalid target" }
            require(normalizedTitle.length <= MAX_TITLE_LENGTH) { "Title is too long" }
            require(body.length <= MAX_BODY_LENGTH) { "Notes are too long" }
            GithubReleaseDraft(
                tagName = tagName.trim(),
                title = normalizedTitle,
                body = body,
                targetCommitish = commitish,
                isDraft = isDraft,
                isPrerelease = isPrerelease
            )
        }
    }
}

/**
 * A file ready to be streamed into a release as an attachment. [open] is called once per upload
 * attempt, so a redirect or a retry reads the file again from the start instead of replaying an
 * exhausted stream.
 */
class GithubReleaseAssetUpload private constructor(
    val fileName: String,
    val label: String,
    val contentType: String,
    val contentLength: Long,
    val open: () -> GithubAssetInput
) {
    companion object {
        const val MAX_LABEL_LENGTH = 100
        const val MAX_CONTENT_LENGTH = 2_000_000_000L

        fun fromFile(
            fileName: String,
            label: String,
            contentType: String?,
            contentLength: Long,
            open: () -> GithubAssetInput
        ): Result<GithubReleaseAssetUpload> = runCatching {
            val name = fileName.trim()
            val optionalLabel = label.trim()
            val mediaType = contentType?.trim()?.takeIf(String::isNotEmpty)
            require(
                name.isNotEmpty() && name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 }
            ) { "Invalid file name" }
            require(optionalLabel.length <= MAX_LABEL_LENGTH) { "Label is too long" }
            require(mediaType == null || mediaType.isMediaType()) { "Invalid content type" }
            require(contentLength > 0) { "File is empty" }
            require(contentLength <= MAX_CONTENT_LENGTH) { "File is too large" }
            GithubReleaseAssetUpload(
                fileName = name,
                label = optionalLabel,
                contentType = mediaType ?: OCTET_STREAM,
                contentLength = contentLength,
                open = open
            )
        }

        private const val OCTET_STREAM = "application/octet-stream"

        // OkHttp rejects anything outside these token characters when it parses the media type.
        private fun String.isMediaType(): Boolean = split('/').let {
            it.size == 2 && it.all { part ->
                part.isNotEmpty() && part.all { c -> c.isLetterOrDigit() || c in "!#$%&'*+-.^_`|~" }
            }
        }
    }
}

/** The bytes of a release attachment, named without platform stream types. */
interface GithubAssetInput {
    /** Reads up to [count] bytes into [target] starting at [offset]; returns the count read or -1 at the end. */
    fun read(target: ByteArray, offset: Int, count: Int): Int

    fun close()
}

interface GithubReleasesRepository {
    suspend fun releases(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRelease>>

    /** Explicit refresh path. Implementations may bypass a short-lived local cache. */
    suspend fun refreshReleases(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRelease>> = releases(owner, name, page, perPage)

    suspend fun release(owner: String, name: String, releaseId: Long): Result<GithubRelease>

    suspend fun releaseByTag(owner: String, name: String, tagName: String): Result<GithubRelease>

    suspend fun createRelease(
        owner: String,
        name: String,
        draft: GithubReleaseDraft
    ): Result<GithubRelease>

    /** Overwrites the release metadata. The tag keeps pointing at the commit it was created from. */
    suspend fun updateRelease(
        owner: String,
        name: String,
        releaseId: Long,
        draft: GithubReleaseDraft
    ): Result<GithubRelease>

    /** Removes the release and its attachments. The git tag created when publishing survives. */
    suspend fun deleteRelease(owner: String, name: String, releaseId: Long): Result<Unit>

    /** Streams [asset] as an attachment of an existing release and returns the created asset. */
    suspend fun uploadAsset(
        owner: String,
        name: String,
        releaseId: Long,
        asset: GithubReleaseAssetUpload
    ): Result<GithubReleaseAsset>

    /** Removes one attachment; the release and its tag are untouched. */
    suspend fun deleteAsset(owner: String, name: String, assetId: Long): Result<Unit>
}
