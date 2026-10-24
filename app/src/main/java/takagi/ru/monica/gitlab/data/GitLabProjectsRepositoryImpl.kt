package takagi.ru.monica.gitlab.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.gitlab.domain.*

internal class GitLabProjectsRepositoryImpl(
    client: OkHttpClient,
    private val credentials: GitLabCredentialProvider,
    private val instanceUrl: (String) -> HttpUrl = ::gitLabInstanceUrl
) : GitLabProjectsRepository {
    private val api = GitLabApiClient(client, credentials, instanceUrl)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun projects(accountKey: String, search: String, page: Int): Result<GitLabPage<GitLabProject>> = read {
        require(search.length <= 200)
        val response = get(accountKey, listOf("projects"), pageParams(page) + mapOf(
            "membership" to "true", "order_by" to "last_activity_at", "sort" to "desc",
            "search" to search.trim()))
        GitLabPage(json.decodeFromString<List<ProjectDto>>(response.body).map { it.domain() }, response.nextPage)
    }

    override suspend fun project(accountKey: String, projectId: Long): Result<GitLabProject> = read {
        json.decodeFromString<ProjectDto>(get(accountKey, projectPath(projectId)).body).domain().also { require(it.id == projectId) }
    }

    override suspend fun revision(accountKey: String, projectId: Long, ref: String): Result<GitLabRevision> = read {
        require(ref.isNotBlank() && ref !in listOf(".", "..") && ref.length <= 1024 && ref.none { it.isISOControl() })
        val commit = json.decodeFromString<CommitDto>(get(accountKey, projectPath(projectId) + listOf("repository", "commits", ref)).body)
        require(commit.id.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")))
        GitLabRevision(ref, commit.id)
    }

    override suspend fun tree(accountKey: String, projectId: Long, sha: String, path: String, page: Int): Result<GitLabPage<GitLabTreeEntry>> = read {
        validateSha(sha)
        validatePath(path, allowRoot = true)
        val response = get(accountKey, projectPath(projectId) + listOf("repository", "tree"),
            pageParams(page) + mapOf("ref" to sha, "path" to path))
        val entries = json.decodeFromString<List<TreeDto>>(response.body).map { entry ->
            validatePath(entry.path)
            require(entry.name.isNotBlank() && '/' !in entry.name && entry.name !in listOf(".", ".."))
            require(entry.path == (if (path.isEmpty()) entry.name else "$path/${entry.name}"))
            GitLabTreeEntry(entry.path, entry.name, when (entry.type) {
                "tree" -> GitLabEntryType.DIRECTORY
                "blob" -> GitLabEntryType.FILE
                "commit" -> GitLabEntryType.SUBMODULE
                else -> throw GitLabReadException(GitLabReadError.INVALID_RESPONSE)
            })
        }
        GitLabPage(entries, response.nextPage)
    }

    override suspend fun file(accountKey: String, projectId: Long, sha: String, path: String): Result<GitLabFile> = read {
        validateSha(sha)
        validatePath(path)
        // addPathSegment encodes the entire file path, including nested slashes, once.
        val dto = json.decodeFromString<FileDto>(get(accountKey,
            projectPath(projectId) + listOf("repository", "files", path), mapOf("ref" to sha)).body)
        require(dto.filePath == path)
        if (dto.encoding != "base64" || dto.size !in 0..MAX_FILE_BYTES || dto.content.length > 720_000) unsupported()
        val bytes = try { Base64.getDecoder().decode(dto.content.filterNot { it == '\n' || it == '\r' }) }
            catch (_: IllegalArgumentException) { throw GitLabReadException(GitLabReadError.INVALID_RESPONSE) }
        require(bytes.size.toLong() == dto.size)
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            catch (_: java.nio.charset.CharacterCodingException) { unsupported() }
        if (text.any { it.isISOControl() && it !in "\n\r\t" }) unsupported()
        GitLabFile(path, text, dto.size)
    }

    private suspend fun get(key: String, segments: List<String>, params: Map<String, String> = emptyMap()) =
        api.request(key, segments, params, oversized = if (segments.getOrNull(segments.lastIndex - 1) == "files")
            GitLabReadError.UNSUPPORTED_FILE else GitLabReadError.INVALID_RESPONSE)

    private suspend fun <T> read(block: suspend () -> T): Result<T> = api.result(block)

    private fun projectPath(id: Long): List<String> { require(id > 0); return listOf("projects", id.toString()) }
    private fun pageParams(page: Int): Map<String, String> { require(page > 0); return mapOf("page" to "$page", "per_page" to "30") }
    private fun validateSha(sha: String) { require(sha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))) }
    private fun validatePath(path: String, allowRoot: Boolean = false) {
        require((allowRoot && path.isEmpty()) || (path.isNotEmpty() && path.length <= 4096 &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." } && path.none { it.isISOControl() }))
    }
    private fun unsupported(): Nothing = throw GitLabReadException(GitLabReadError.UNSUPPORTED_FILE)

    @Serializable private data class ProjectDto(
        val id: Long, val name: String, @SerialName("path_with_namespace") val path: String,
        val description: String? = null, val visibility: String = "private",
        @SerialName("default_branch") val defaultBranch: String? = null,
        @SerialName("star_count") val stars: Long = 0, @SerialName("forks_count") val forks: Long = 0,
        val archived: Boolean = false
    ) {
        fun domain(): GitLabProject { require(id > 0 && path.isNotBlank()); return GitLabProject(
            id, name, path, description.orEmpty(), visibility, defaultBranch, stars, forks, archived) }
    }
    @Serializable private data class CommitDto(val id: String)
    @Serializable private data class TreeDto(val name: String, val path: String, val type: String)
    @Serializable private data class FileDto(@SerialName("file_path") val filePath: String,
        val encoding: String, val content: String, val size: Long)

    companion object {
        private const val MAX_FILE_BYTES = 524_288L
    }
}
