package takagi.ru.monica.github.domain

interface GithubUserRepositoriesRepository {
    suspend fun repositories(
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRepository>>
}
