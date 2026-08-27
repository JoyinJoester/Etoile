package takagi.ru.monica.github.domain

enum class GithubStarCategory { ALL, ANDROID, KOTLIN, TOOLS }

interface GithubStarsRepository {
    suspend fun starredRepositories(
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubRepository>>
}

interface GithubStarCategoryStore {
    fun category(repositoryId: Long): GithubStarCategory
    fun setCategory(repositoryId: Long, category: GithubStarCategory)
}
