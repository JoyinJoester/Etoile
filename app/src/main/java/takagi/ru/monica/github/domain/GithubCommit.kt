package takagi.ru.monica.github.domain

data class GithubCommit(
    val sha: String,
    val message: String,
    val authorName: String,
    val authorLogin: String?,
    val authorAvatarUrl: String?,
    val authoredAt: String,
    val committerName: String,
    val committedAt: String,
    val htmlUrl: String,
    val isVerified: Boolean
) {
    val shortSha: String get() = sha.take(7)
    val title: String get() = message.lineSequence().firstOrNull().orEmpty().ifBlank { shortSha }
}

data class GithubCommitDetails(
    val commit: GithubCommit,
    val additions: Int,
    val deletions: Int,
    val totalChanges: Int,
    val files: List<GithubCommitFile>
)

enum class GithubCommitFileStatus { ADDED, MODIFIED, REMOVED, RENAMED, COPIED, CHANGED, UNKNOWN }

data class GithubCommitFile(
    val filename: String,
    val previousFilename: String?,
    val status: GithubCommitFileStatus,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val blobUrl: String,
    val rawUrl: String?,
    val patch: String?
)

interface GithubCommitsRepository {
    suspend fun commits(
        owner: String,
        name: String,
        ref: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubCommit>>

    suspend fun commit(owner: String, name: String, sha: String): Result<GithubCommitDetails>
}
