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
    val isHireable: Boolean?,
    /** ISO-8601 注册时间，用于本地推导"开源老兵"类成就。 */
    val createdAt: String? = null,
    val type: String = "User"
) {
    val isOrganization: Boolean get() = type.equals("Organization", ignoreCase = true)
}

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

    suspend fun blockedUsers(
        page: Int = 1,
        perPage: Int = 50
    ): Result<GithubPage<GithubUserSummary>>

    suspend fun viewerBlocks(login: String): Result<Boolean>

    suspend fun setBlocked(login: String, blocked: Boolean): Result<Unit>
}
