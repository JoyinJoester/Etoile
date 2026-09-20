package takagi.ru.monica.github.domain

enum class GithubContentType {
    DIRECTORY,
    FILE,
    SYMLINK,
    SUBMODULE,
    UNKNOWN
}

data class GithubBranch(
    val name: String,
    val sha: String,
    val isProtected: Boolean
)

data class GithubTag(
    val name: String,
    val sha: String
)

data class GithubContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: GithubContentType,
    val htmlUrl: String?,
    val downloadUrl: String?
)

sealed interface GithubFileContent {
    data class Text(val value: String, val sha: String? = null) : GithubFileContent

    data object Binary : GithubFileContent
    data object TooLarge : GithubFileContent
}

interface GithubRepositoryContentsRepository {
    suspend fun resolveRef(
        owner: String,
        name: String,
        ref: String
    ): Result<String> = Result.failure(UnsupportedOperationException("Ref resolution unavailable"))

    suspend fun createBranch(
        owner: String,
        name: String,
        branch: String,
        fromSha: String
    ): Result<GithubBranch> = Result.failure(UnsupportedOperationException("Branch creation unavailable"))

    suspend fun deleteBranch(owner: String, name: String, branch: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Branch deletion unavailable"))

    suspend fun renameBranch(owner: String, name: String, branch: String, newName: String): Result<GithubBranch> =
        Result.failure(UnsupportedOperationException("Branch rename unavailable"))

    suspend fun createTag(
        owner: String,
        name: String,
        tag: String,
        fromSha: String
    ): Result<GithubTag> = Result.failure(UnsupportedOperationException("Tag creation unavailable"))

    suspend fun deleteTag(owner: String, name: String, tag: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Tag deletion unavailable"))

    suspend fun write(owner: String, name: String, change: GithubFileWrite): Result<GithubFileWriteResult> =
        Result.failure(UnsupportedOperationException("File writing unavailable"))
    suspend fun branches(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubBranch>>

    suspend fun tags(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubTag>>

    suspend fun directory(
        owner: String,
        name: String,
        path: String = "",
        ref: String? = null
    ): Result<List<GithubContentItem>>

    suspend fun file(
        owner: String,
        name: String,
        path: String,
        ref: String? = null
    ): Result<GithubFileContent>
}
