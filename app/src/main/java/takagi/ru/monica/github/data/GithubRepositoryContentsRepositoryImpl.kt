package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
    cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubRepositoryContentsRepository {
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

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
                .header("Accept", "application/vnd.github.raw+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                val body = response.body ?: return@use GithubFileContent.Text("")
                if (body.contentLength() > maxTextFileBytes) return@use GithubFileContent.TooLarge
                val bytes = body.bytes()
                when {
                    bytes.size > maxTextFileBytes -> GithubFileContent.TooLarge
                    bytes.any { it == 0.toByte() } -> GithubFileContent.Binary
                    else -> GithubFileContent.Text(bytes.toString(Charsets.UTF_8))
                }
            }
        }
    }

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
