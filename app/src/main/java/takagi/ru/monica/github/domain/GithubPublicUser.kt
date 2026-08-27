package takagi.ru.monica.github.domain

data class GithubPublicUser(
    val id: Long,
    val login: String,
    val name: String?,
    val bio: String?,
    val avatarUrl: String?,
    val htmlUrl: String,
    val company: String?,
    val location: String?,
    val blog: String?,
    val publicRepositories: Int,
    val followers: Int,
    val following: Int,
    val isHireable: Boolean?
)

enum class GithubUserConnectionKind { FOLLOWERS, FOLLOWING }

interface GithubPublicUserRepository {
    suspend fun user(login: String): Result<GithubPublicUser>

    suspend fun viewerFollows(login: String): Result<Boolean>

    suspend fun setFollowing(login: String, following: Boolean): Result<Boolean>

    suspend fun repositories(
        login: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubRepository>>

    suspend fun connections(
        login: String,
        kind: GithubUserConnectionKind,
        page: Int = 1,
        perPage: Int = 50
    ): Result<GithubPage<GithubUserSummary>>
}
