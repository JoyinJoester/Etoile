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
    data class Text(val value: String) : GithubFileContent

    data object Binary : GithubFileContent
    data object TooLarge : GithubFileContent
}

interface GithubRepositoryContentsRepository {
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
