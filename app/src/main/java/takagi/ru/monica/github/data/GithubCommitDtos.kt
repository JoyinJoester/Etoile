package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubCommit
import takagi.ru.monica.github.domain.GithubCommitDetails
import takagi.ru.monica.github.domain.GithubCommitFile
import takagi.ru.monica.github.domain.GithubCommitFileStatus

@Serializable
internal data class GithubCommitDto(
    val sha: String,
    val commit: GithubCommitGitDto,
    val author: GithubUserDto? = null,
    val committer: GithubUserDto? = null,
    @SerialName("html_url") val htmlUrl: String,
    val stats: GithubStatsDto? = null,
    val files: List<GithubCommitFileDto> = emptyList()
) {
    fun toDomain() = GithubCommit(
        sha = sha,
        message = commit.message,
        authorName = commit.author?.name?.takeIf(String::isNotBlank) ?: author?.login.orEmpty(),
        authorLogin = author?.login,
        authorAvatarUrl = author?.avatarUrl,
        authoredAt = commit.author?.date.orEmpty(),
        committerName = commit.committer?.name?.takeIf(String::isNotBlank) ?: committer?.login.orEmpty(),
        committedAt = commit.committer?.date.orEmpty(),
        htmlUrl = htmlUrl,
        isVerified = commit.verification?.verified == true
    )

    fun toDetails() = GithubCommitDetails(
        commit = toDomain(),
        additions = stats?.additions?.coerceAtLeast(0) ?: 0,
        deletions = stats?.deletions?.coerceAtLeast(0) ?: 0,
        totalChanges = stats?.total?.coerceAtLeast(0) ?: 0,
        files = files.map(GithubCommitFileDto::toDomain)
    )

    @Serializable
    data class GithubCommitGitDto(
        val author: GithubCommitIdentityDto? = null,
        val committer: GithubCommitIdentityDto? = null,
        val message: String = "",
        val verification: GithubVerificationDto? = null
    )

    @Serializable
    data class GithubCommitIdentityDto(
        val name: String? = null,
        val date: String? = null
    )

    @Serializable
    data class GithubVerificationDto(val verified: Boolean = false)

    @Serializable
    data class GithubStatsDto(
        val total: Int = 0,
        val additions: Int = 0,
        val deletions: Int = 0
    )

    @Serializable
    data class GithubCommitFileDto(
        val filename: String,
        @SerialName("previous_filename") val previousFilename: String? = null,
        val status: String = "",
        val additions: Int = 0,
        val deletions: Int = 0,
        val changes: Int = 0,
        @SerialName("blob_url") val blobUrl: String,
        @SerialName("raw_url") val rawUrl: String? = null,
        val patch: String? = null
    ) {
        fun toDomain() = GithubCommitFile(
            filename = filename,
            previousFilename = previousFilename,
            status = when (status.lowercase()) {
                "added" -> GithubCommitFileStatus.ADDED
                "modified" -> GithubCommitFileStatus.MODIFIED
                "removed" -> GithubCommitFileStatus.REMOVED
                "renamed" -> GithubCommitFileStatus.RENAMED
                "copied" -> GithubCommitFileStatus.COPIED
                "changed" -> GithubCommitFileStatus.CHANGED
                else -> GithubCommitFileStatus.UNKNOWN
            },
            additions = additions.coerceAtLeast(0),
            deletions = deletions.coerceAtLeast(0),
            changes = changes.coerceAtLeast(0),
            blobUrl = blobUrl,
            rawUrl = rawUrl,
            patch = patch
        )
    }
}
