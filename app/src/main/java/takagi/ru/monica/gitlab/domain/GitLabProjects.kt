package takagi.ru.monica.gitlab.domain

data class GitLabProject(
    val id: Long,
    val name: String,
    val pathWithNamespace: String,
    val description: String,
    val visibility: String,
    val defaultBranch: String?,
    val stars: Long,
    val forks: Long,
    val archived: Boolean
)

data class GitLabPage<T>(val items: List<T>, val nextPage: Int?)
data class GitLabRevision(val name: String, val sha: String)
enum class GitLabEntryType { DIRECTORY, FILE, SUBMODULE }
data class GitLabTreeEntry(val path: String, val name: String, val type: GitLabEntryType)
data class GitLabFile(val path: String, val text: String, val size: Long)

enum class GitLabReadError { AUTHENTICATION, PERMISSION, NOT_FOUND, RATE_LIMIT, NETWORK, INVALID_RESPONSE, UNSUPPORTED_FILE, CONFLICT, INVALID_INPUT, LOG_TOO_LARGE }
class GitLabReadException(val reason: GitLabReadError) : Exception(reason.name)

/** Every operation is bound to an explicit instance/account identity. */
interface GitLabProjectsRepository {
    suspend fun projects(accountKey: String, search: String, page: Int = 1): Result<GitLabPage<GitLabProject>>
    suspend fun project(accountKey: String, projectId: Long): Result<GitLabProject>
    suspend fun revision(accountKey: String, projectId: Long, ref: String): Result<GitLabRevision>
    suspend fun tree(accountKey: String, projectId: Long, sha: String, path: String, page: Int = 1): Result<GitLabPage<GitLabTreeEntry>>
    suspend fun file(accountKey: String, projectId: Long, sha: String, path: String): Result<GitLabFile>
}
