package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import takagi.ru.monica.github.domain.GithubFileWrite
import takagi.ru.monica.github.domain.GithubFileWriteResult
import takagi.ru.monica.github.domain.GithubGitRef
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository

class GithubRepositoryContentsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.github.com/",
    private val maxTextFileBytes: Long = 512L * 1024L,
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubRepositoryContentsRepository {
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun write(owner: String, name: String, change: GithubFileWrite): Result<GithubFileWriteResult> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = buildJsonObject {
                put("message", change.message); put("branch", change.branch)
                change.expectedSha?.let { put("sha", it) }
                change.content?.let { put("content", Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))) }
            }.toString().toRequestBody("application/json".toMediaType())
            val builder = requests.builder(contentsEndpoint(owner, name, change.path, null))
            val request = (if (change.content == null) builder.delete(payload) else builder.put(payload)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.invalidateAfter {
                    val value = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    GithubFileWriteResult(value.getValue("commit").jsonObject.getValue("sha").jsonPrimitive.content,
                        value["content"]?.takeUnless { it is JsonNull }?.jsonObject?.get("sha")?.jsonPrimitive?.content)
                }
            }
        }
    }

    override suspend fun resolveRef(owner: String, name: String, ref: String): Result<String> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalizedRef = ref.trim()
            require(GithubGitRef.isValidBranchName(normalizedRef)) { "Invalid ref name" }
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("repos").addPathSegment(owner).addPathSegment(name)
                .addPathSegment("commits").addPathSegment(normalizedRef)
                .build().toString()
            val request = requests.optionalBuilder(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject.getValue("sha").jsonPrimitive.content
            }
        }
    }

    override suspend fun createBranch(
        owner: String,
        name: String,
        branch: String,
        fromSha: String
    ): Result<GithubBranch> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(GithubGitRef.isValidBranchName(branch)) { "Invalid branch name" }
            requireBaseCommit(fromSha)
            val value = postRef(owner, name, "refs/heads/$branch", fromSha)
            cacheStore.invalidateAfter {
                GithubBranch(
                    name = value["ref"]?.jsonPrimitive?.content.orEmpty().removePrefix("refs/heads/"),
                    sha = value["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty(),
                    isProtected = false
                )
            }
        }
    }

    override suspend fun deleteBranch(owner: String, name: String, branch: String): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(GithubGitRef.isValidBranchName(branch)) { "Invalid branch name" }
            deleteRef(owner, name, "heads", branch)
        }
    }

    override suspend fun renameBranch(owner: String, name: String, branch: String, newName: String): Result<GithubBranch> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(GithubGitRef.isValidBranchName(branch)) { "Invalid branch name" }
            require(GithubGitRef.isValidBranchName(newName)) { "Invalid branch name" }
            val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("repos").addPathSegment(owner)
                .addPathSegment(name).addPathSegment("branches")
            url.addPathSegment(branch).addPathSegment("rename")
            val body = buildJsonObject { put("new_name", newName) }.toString().toRequestBody("application/json".toMediaType())
            val request = requests.builder(url.build().toString()).post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val value = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                cacheStore.invalidateAfter {
                    GithubBranch(
                        name = value["name"]?.jsonPrimitive?.content ?: newName,
                        sha = value["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty(),
                        isProtected = value["protected"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                }
            }
        }
    }

    override suspend fun createTag(
        owner: String,
        name: String,
        tag: String,
        fromSha: String
    ): Result<GithubTag> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(GithubGitRef.isValidTagName(tag)) { "Invalid tag name" }
            requireBaseCommit(fromSha)
            val value = postRef(owner, name, "refs/tags/$tag", fromSha)
            cacheStore.invalidateAfter {
                GithubTag(
                    name = value["ref"]?.jsonPrimitive?.content.orEmpty().removePrefix("refs/tags/"),
                    sha = value["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty()
                )
            }
        }
    }

    override suspend fun deleteTag(owner: String, name: String, tag: String): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(GithubGitRef.isValidTagName(tag)) { "Invalid tag name" }
            deleteRef(owner, name, "tags", tag)
        }
    }

    override suspend fun branches(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubBranch>> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val url = refsEndpoint(owner, name, "branches", page, perPage)
                cachedGet.execute(
                    client = client,
                    cacheKey = GithubCacheKeys.endpoint("branches", requests.cacheScope(), url),
                    request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                    decode = { body, linkHeader ->
                        GithubPage(
                            items = json.decodeFromString(
                                ListSerializer(GithubBranchDto.serializer()), body
                            ).map(GithubBranchDto::toDomain),
                            nextPage = GithubPagination.nextPage(linkHeader)
                        )
                    }
                )
            }
        }

    override suspend fun tags(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubTag>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = refsEndpoint(owner, name, "tags", page, perPage)
            cachedGet.execute(
                client = client,
                cacheKey = GithubCacheKeys.endpoint("tags", requests.cacheScope(), url),
                request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubTagDto.serializer()), body
                        ).map(GithubTagDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun directory(
        owner: String,
        name: String,
        path: String,
        ref: String?
    ): Result<List<GithubContentItem>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = contentsEndpoint(owner, name, path, ref)
            cachedGet.execute(
                client = client,
                cacheKey = GithubCacheKeys.endpoint("contents-directory", requests.cacheScope(), url),
                request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                decode = { body, _ ->
                    json.decodeFromString(
                        ListSerializer(GithubContentDto.serializer()), body
                    ).map(GithubContentDto::toDomain)
                }
            )
        }
    }

    override suspend fun file(
        owner: String,
        name: String,
        path: String,
        ref: String?
    ): Result<GithubFileContent> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val request = requests.optionalBuilder(contentsEndpoint(owner, name, path, ref))
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val metadata = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val size = metadata["size"]?.jsonPrimitive?.longOrNull ?: 0L
                val sha = metadata["sha"]?.jsonPrimitive?.contentOrNull
                if (size > maxTextFileBytes) return@use GithubFileContent.TooLarge
                val encoded = metadata["content"]?.jsonPrimitive?.contentOrNull
                val bytes = encoded?.stripBase64LineBreaks()?.let { Base64.getDecoder().decode(it) }
                when {
                    bytes == null -> GithubFileContent.Binary
                    bytes.size > maxTextFileBytes -> GithubFileContent.TooLarge
                    bytes.any { it == 0.toByte() } -> GithubFileContent.Binary
                    else -> GithubFileContent.Text(bytes.toString(Charsets.UTF_8), sha)
                }
            }
        }
    }

    /** GitHub wraps base64 `content` every 60 characters; the line breaks are not part of the file. */
    private fun String.stripBase64LineBreaks(): String = filterNot(Char::isWhitespace)

    private fun contentsEndpoint(owner: String, name: String, path: String, ref: String?): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .addPathSegment("contents")
        path.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        ref?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("ref", it) }
        return builder.build().toString()
    }

    private fun refsEndpoint(owner: String, name: String, kind: String, page: Int, perPage: Int): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("repos")
        .addPathSegment(owner)
        .addPathSegment(name)
        .addPathSegment(kind)
        .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
        .addQueryParameter("page", page.coerceAtLeast(1).toString())
        .build()
        .toString()

    private fun refCreateEndpoint(owner: String, name: String): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("repos").addPathSegment(owner).addPathSegment(name)
        .addPathSegment("git").addPathSegment("refs").build().toString()

    private fun requireBaseCommit(sha: String) {
        require(COMMIT_SHA.matches(sha)) { "Invalid base commit" }
    }

    private fun postRef(owner: String, name: String, fullRef: String, sha: String): JsonObject {
        val payload = buildJsonObject {
            put("ref", fullRef); put("sha", sha)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = requests.builder(refCreateEndpoint(owner, name)).post(payload).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GithubApiException.of(response)
            return json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
        }
    }

    private fun deleteRef(owner: String, name: String, kind: String, refName: String) {
        val builder = baseUrl.toHttpUrl().newBuilder().addPathSegment("repos").addPathSegment(owner)
            .addPathSegment(name).addPathSegment("git").addPathSegment("refs").addPathSegment(kind)
        refName.split('/').forEach(builder::addPathSegment)
        val request = requests.builder(builder.build().toString()).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GithubApiException.of(response)
            cacheStore.invalidateAfter { Unit }
        }
    }

    private companion object {
        /** GitHub serves both SHA-1 and SHA-256 object names. */
        val COMMIT_SHA = Regex("([0-9a-fA-F]{40}|[0-9a-fA-F]{64})")
    }

    @Serializable
    private data class GithubBranchDto(
        val name: String,
        val commit: GithubBranchCommitDto,
        @SerialName("protected") val isProtected: Boolean = false
    ) {
        fun toDomain() = GithubBranch(name = name, sha = commit.sha, isProtected = isProtected)
    }

    @Serializable
    private data class GithubBranchCommitDto(val sha: String)

    @Serializable
    private data class GithubTagDto(
        val name: String,
        val commit: GithubBranchCommitDto
    ) {
        fun toDomain() = GithubTag(name = name, sha = commit.sha)
    }

    @Serializable
    private data class GithubContentDto(
        val name: String,
        val path: String,
        val sha: String,
        val size: Long = 0,
        val type: String,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("download_url") val downloadUrl: String? = null
    ) {
        fun toDomain() = GithubContentItem(
            name = name,
            path = path,
            sha = sha,
            size = size,
            type = when (type) {
                "dir" -> GithubContentType.DIRECTORY
                "file" -> GithubContentType.FILE
                "symlink" -> GithubContentType.SYMLINK
                "submodule" -> GithubContentType.SUBMODULE
                else -> GithubContentType.UNKNOWN
            },
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl
        )
    }
}
