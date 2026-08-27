package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary

class GithubPublicUserRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubPublicUserRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun user(login: String): Result<GithubPublicUser> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder().addPathSegment("users").addPathSegment(login).build().toString()
            cachedGet.execute(
                client = client,
                cacheKey = GithubCacheKeys.endpoint("public-user", requests.cacheScope(), url),
                request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                decode = { body, _ -> json.decodeFromString(PublicUserDto.serializer(), body).toDomain() }
            )
        }
    }

    override suspend fun viewerFollows(login: String): Result<Boolean> = withContext(Dispatchers.IO) {
        githubRunCatching {
            client.newCall(requests.builder(followingEndpoint(login)).get().build()).execute().use { response ->
                when (response.code) {
                    204 -> true
                    404 -> false
                    else -> throw GithubApiException(response.code)
                }
            }
        }
    }

    override suspend fun setFollowing(login: String, following: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val builder = requests.builder(followingEndpoint(login))
                val request = if (following) {
                    builder.put(EMPTY_BODY).build()
                } else {
                    builder.delete().build()
                }
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException(response.code)
                    cacheStore.invalidateAfter { following }
                }
            }
        }

    override suspend fun repositories(
        login: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepository>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("users")
                .addPathSegment(login)
                .addPathSegment("repos")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "desc")
                .build()
                .toString()
            cachedGet.execute(
                client = client,
                cacheKey = GithubCacheKeys.endpoint("public-user-repositories", requests.cacheScope(), url),
                request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubRepositoryDto.serializer()), body
                        ).map(GithubRepositoryDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun connections(
        login: String,
        kind: GithubUserConnectionKind,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubUserSummary>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val relationship = when (kind) {
                GithubUserConnectionKind.FOLLOWERS -> "followers"
                GithubUserConnectionKind.FOLLOWING -> "following"
            }
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("users")
                .addPathSegment(login)
                .addPathSegment(relationship)
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
                .toString()
            cachedGet.execute(
                client = client,
                cacheKey = GithubCacheKeys.endpoint(
                    "public-user-connections",
                    requests.cacheScope(),
                    url
                ),
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubUserDto.serializer()),
                            body
                        ).map(GithubUserDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    @Serializable
    private data class PublicUserDto(
        val id: Long,
        val login: String,
        val name: String? = null,
        val bio: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val company: String? = null,
        val location: String? = null,
        val blog: String? = null,
        @SerialName("public_repos") val publicRepositories: Int = 0,
        val followers: Int = 0,
        val following: Int = 0,
        val hireable: Boolean? = null
    ) {
        fun toDomain() = GithubPublicUser(
            id, login, name, bio, avatarUrl, htmlUrl, company, location, blog,
            publicRepositories, followers, following, hireable
        )
    }

    private fun followingEndpoint(login: String): String = apiBaseUrl.newBuilder()
        .addPathSegment("user")
        .addPathSegment("following")
        .addPathSegment(login)
        .build()
        .toString()

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
