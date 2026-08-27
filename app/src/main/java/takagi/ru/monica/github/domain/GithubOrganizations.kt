package takagi.ru.monica.github.domain

data class GithubOrganization(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val description: String?
)

interface GithubOrganizationsRepository {
    suspend fun myOrganizations(
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubOrganization>>
}
