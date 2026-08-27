package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleasesRepository

class GithubReleasesRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubReleasesRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun releases(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRelease>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "releases").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint(
                namespace = "releases",
                scope = requests.cacheScope(),
                url = url.toString()
            )
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubReleaseDto.serializer()),
                            body
                        ).map(GithubReleaseDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun release(
        owner: String,
        name: String,
        releaseId: Long
    ): Result<GithubRelease> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(releaseId > 0)
            val url = endpoint(owner, name, "releases", releaseId.toString()).toString()
            val cacheKey = GithubCacheKeys.endpoint(
                namespace = "release-detail",
                scope = requests.cacheScope(),
                url = url
            )
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, _ ->
                    json.decodeFromString(GithubReleaseDto.serializer(), body).toDomain()
                }
            )
        }
    }

    override suspend fun releaseByTag(
        owner: String,
        name: String,
        tagName: String
    ): Result<GithubRelease> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalizedTag = tagName.trim()
            require(
                normalizedTag.isNotEmpty() &&
                    normalizedTag.length <= MAX_TAG_LENGTH &&
                    normalizedTag.none(Char::isISOControl)
            )
            val url = endpoint(owner, name, "releases", "tags", normalizedTag).toString()
            val cacheKey = GithubCacheKeys.endpoint(
                namespace = "release-detail",
                scope = requests.cacheScope(),
                url = url
            )
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, _ ->
                    json.decodeFromString(GithubReleaseDto.serializer(), body).toDomain()
                }
            )
        }
    }

    private fun endpoint(owner: String, name: String, vararg segments: String): HttpUrl =
        apiBaseUrl.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .apply { segments.forEach(::addPathSegment) }
            .build()

    private companion object {
        const val MAX_TAG_LENGTH = 255
    }
}
