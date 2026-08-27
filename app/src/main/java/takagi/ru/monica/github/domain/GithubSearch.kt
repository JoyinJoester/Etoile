package takagi.ru.monica.github.domain

data class GithubUserSearchResult(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
    val accountType: String?
)

data class GithubCodeSearchResult(
    val id: String,
    val name: String,
    val path: String,
    val sha: String,
    val repositoryFullName: String,
    val htmlUrl: String
)

enum class GithubIssueSearchType { ISSUE, PULL_REQUEST }

data class GithubIssueSearchResult(
    val id: Long,
    val number: Int,
    val title: String,
    val state: GithubIssueState,
    val type: GithubIssueSearchType,
    val isDraft: Boolean,
    val author: GithubUserSummary,
    val labels: List<GithubIssueLabel>,
    val comments: Int,
    val repositoryFullName: String,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String
)

interface GithubGlobalSearchRepository {
    suspend fun users(query: String, page: Int = 1, perPage: Int = 20): Result<GithubPage<GithubUserSearchResult>>

    suspend fun code(query: String, page: Int = 1, perPage: Int = 20): Result<GithubPage<GithubCodeSearchResult>>

    suspend fun issues(query: String, page: Int = 1, perPage: Int = 20): Result<GithubPage<GithubIssueSearchResult>>

    suspend fun pullRequests(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): Result<GithubPage<GithubIssueSearchResult>>
}
