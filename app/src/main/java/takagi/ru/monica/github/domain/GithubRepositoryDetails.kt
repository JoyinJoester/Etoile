package takagi.ru.monica.github.domain

data class GithubRepositoryDetails(
    val repository: GithubRepository,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val defaultBranch: String,
    val forks: Int,
    val watchers: Int,
    val openIssues: Int,
    val license: String?,
    val topics: List<String>,
    val isArchived: Boolean,
    val isFork: Boolean
)

data class GithubBranchProtection(
    val branch: String,
    val requiredStatusChecks: Int,
    val requiredApprovingReviews: Int?,
    val enforceAdmins: Boolean
)

enum class GithubCollaboratorRole { READ, TRIAGE, WRITE, MAINTAIN, ADMIN, UNKNOWN }

data class GithubCollaborator(
    val user: GithubUserSummary,
    val role: GithubCollaboratorRole
)

data class GithubRepositoryWebhook(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val events: List<String>,
    val lastResponseCode: Int?,
    val lastResponseStatus: String?,
    val lastResponseMessage: String?
)

interface GithubRepositoryDetailsRepository {
    suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails>
    suspend fun readme(owner: String, name: String, ref: String? = null): Result<String?>
    suspend fun branchProtection(owner: String, name: String, branch: String): Result<GithubBranchProtection?>
    suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>>
    suspend fun collaborators(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubCollaborator>>
    suspend fun webhooks(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRepositoryWebhook>>
}
