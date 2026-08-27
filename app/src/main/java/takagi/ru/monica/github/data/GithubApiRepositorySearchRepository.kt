package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchType
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubGlobalSearchRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult

class GithubApiRepositorySearchRepository(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubRepositorySearchRepository, GithubGlobalSearchRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun search(
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepository>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("search")
                .addPathSegment("repositories")
                .addQueryParameter("q", query.trim())
                .addQueryParameter("per_page", perPage.coerceIn(1, 50).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("search-repositories", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(SearchResponse.serializer(), body)
                        .items
                        .map(GithubRepositoryDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun users(
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubUserSearchResult>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            searchPage("search-users", "users", query, page, perPage) { body, linkHeader ->
                val response = json.decodeFromString(GithubUsersSearchResponse.serializer(), body)
                GithubPage(response.items.map(GithubUserSearchDto::toDomain), GithubPagination.nextPage(linkHeader))
            }
        }
    }

    override suspend fun code(
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubCodeSearchResult>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            searchPage("search-code", "code", query, page, perPage) { body, linkHeader ->
                val response = json.decodeFromString(GithubCodeSearchResponse.serializer(), body)
                GithubPage(response.items.map(GithubCodeSearchDto::toDomain), GithubPagination.nextPage(linkHeader))
            }
        }
    }

    override suspend fun issues(
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueSearchResult>> = issueSearch(
        namespace = "search-issues",
        query = typedIssueQuery(query, "issue"),
        page = page,
        perPage = perPage
    )

    override suspend fun pullRequests(
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueSearchResult>> = issueSearch(
        namespace = "search-pull-requests",
        query = typedIssueQuery(query, "pr"),
        page = page,
        perPage = perPage
    )

    private suspend fun issueSearch(
        namespace: String,
        query: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueSearchResult>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            searchPage(namespace, "issues", query, page, perPage) { body, linkHeader ->
                val response = json.decodeFromString(GithubIssuesSearchResponse.serializer(), body)
                GithubPage(
                    items = response.items.mapNotNull(GithubIssueSearchDto::toDomain),
                    nextPage = GithubPagination.nextPage(linkHeader)
                )
            }
        }
    }

    private fun <T> searchPage(
        namespace: String,
        path: String,
        query: String,
        page: Int,
        perPage: Int,
        decode: (String, String?) -> T
    ): T {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment(path)
            .addQueryParameter("q", query.trim())
            .addQueryParameter("per_page", perPage.coerceIn(1, 50).toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
            .toString()
        return cachedGet.execute(
            client = client,
            cacheKey = GithubCacheKeys.endpoint(namespace, requests.cacheScope(), url),
            request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
            decode = decode
        )
    }

    @Serializable
    private data class SearchResponse(val items: List<GithubRepositoryDto> = emptyList())

    @Serializable
    private data class GithubUsersSearchResponse(val items: List<GithubUserSearchDto> = emptyList())

    @Serializable
    private data class GithubUserSearchDto(
        val id: Long,
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val type: String? = null
    ) {
        fun toDomain() = GithubUserSearchResult(id, login, avatarUrl, htmlUrl, type)
    }

    @Serializable
    private data class GithubCodeSearchResponse(val items: List<GithubCodeSearchDto> = emptyList())

    @Serializable
    private data class GithubCodeSearchDto(
        val name: String,
        val path: String,
        val sha: String,
        @SerialName("html_url") val htmlUrl: String,
        val repository: GithubCodeRepositoryDto
    ) {
        fun toDomain() = GithubCodeSearchResult(
            id = "$sha:$path",
            name = name,
            path = path,
            sha = sha,
            repositoryFullName = repository.fullName,
            htmlUrl = htmlUrl
        )
    }

    @Serializable
    private data class GithubCodeRepositoryDto(@SerialName("full_name") val fullName: String)

    @Serializable
    private data class GithubIssuesSearchResponse(val items: List<GithubIssueSearchDto> = emptyList())

    @Serializable
    private data class GithubIssueSearchDto(
        val id: Long,
        val number: Int,
        val title: String,
        val state: String,
        val draft: Boolean = false,
        val user: GithubUserDto? = null,
        val labels: List<GithubLabelDto> = emptyList(),
        val comments: Int = 0,
        @SerialName("repository_url") val repositoryUrl: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("pull_request") val pullRequest: JsonObject? = null
    ) {
        fun toDomain(): GithubIssueSearchResult? {
            val fullName = repositoryUrl.toGithubRepositoryFullName() ?: return null
            return GithubIssueSearchResult(
                id = id,
                number = number,
                title = title,
                state = if (state.equals("closed", ignoreCase = true)) {
                    GithubIssueState.CLOSED
                } else {
                    GithubIssueState.OPEN
                },
                type = if (pullRequest == null) {
                    GithubIssueSearchType.ISSUE
                } else {
                    GithubIssueSearchType.PULL_REQUEST
                },
                isDraft = draft,
                author = user.toDomainOrGhost(),
                labels = labels.map(GithubLabelDto::toDomain),
                comments = comments,
                repositoryFullName = fullName,
                createdAt = createdAt,
                updatedAt = updatedAt,
                htmlUrl = htmlUrl
            )
        }
    }

    private fun typedIssueQuery(query: String, type: String): String {
        val normalized = query
            .trim()
            .replace(ISSUE_TYPE_QUALIFIER, " ")
            .trim()
            .replace(WHITESPACE, " ")
        return if (normalized.isEmpty()) "is:$type" else "$normalized is:$type"
    }

    private companion object {
        val ISSUE_TYPE_QUALIFIER = Regex(
            """(?i)(^|\s)(?:is|type):(issue|pr|pullrequest|pull-request)(?=\s|$)"""
        )
        val WHITESPACE = Regex("""\s+""")
    }
}

private fun String.toGithubRepositoryFullName(): String? {
    val segments = toHttpUrlOrNull()?.pathSegments?.filter(String::isNotBlank) ?: return null
    val reposIndex = segments.indexOf("repos")
    if (reposIndex < 0 || segments.size <= reposIndex + 2) return null
    val owner = segments[reposIndex + 1]
    val name = segments[reposIndex + 2]
    return if (owner.isNotBlank() && name.isNotBlank()) "$owner/$name" else null
}
