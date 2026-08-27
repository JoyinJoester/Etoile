package takagi.ru.monica.github.domain

data class GithubRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val updatedAt: String?,
    val isPrivate: Boolean,
    val htmlUrl: String
)

interface GithubRepositorySearchRepository {
    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): Result<GithubPage<GithubRepository>>
}
