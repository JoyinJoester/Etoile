package takagi.ru.monica.github.domain

data class GithubRepositoryViewerState(
    val isStarred: Boolean,
    val isWatching: Boolean
)

interface GithubRepositoryActionsRepository {
    suspend fun viewerState(owner: String, name: String): Result<GithubRepositoryViewerState>
    suspend fun setStarred(owner: String, name: String, starred: Boolean): Result<Boolean>
    suspend fun setWatching(owner: String, name: String, watching: Boolean): Result<Boolean>
    suspend fun fork(owner: String, name: String): Result<GithubRepository>
}
