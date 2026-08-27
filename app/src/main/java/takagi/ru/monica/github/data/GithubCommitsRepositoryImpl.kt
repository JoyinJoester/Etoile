package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubCommit
import takagi.ru.monica.github.domain.GithubCommitDetails
import takagi.ru.monica.github.domain.GithubCommitsRepository
import takagi.ru.monica.github.domain.GithubPage

class GithubCommitsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubCommitsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun commits(
        owner: String,
        name: String,
        ref: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubCommit>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalizedRef = normalizeReference(ref)
            val url = endpoint(owner, name, "commits").newBuilder()
                .addQueryParameter("sha", normalizedRef)
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("commits", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubCommitDto.serializer()),
                            body
                        ).map(GithubCommitDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun commit(
        owner: String,
        name: String,
        sha: String
    ): Result<GithubCommitDetails> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalizedSha = normalizeReference(sha)
            val url = endpoint(owner, name, "commits", normalizedSha).toString()
            val cacheKey = GithubCacheKeys.endpoint("commit-detail", requests.cacheScope(), url)
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, _ ->
                    json.decodeFromString(GithubCommitDto.serializer(), body).toDetails()
                }
            )
        }
    }

    private fun normalizeReference(value: String): String {
        val normalized = value.trim()
        require(
            normalized.isNotEmpty() &&
                normalized.length <= MAX_REFERENCE_LENGTH &&
                normalized.none(Char::isISOControl)
        )
        return normalized
    }

    private fun endpoint(owner: String, name: String, vararg segments: String): HttpUrl =
        apiBaseUrl.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .apply { segments.forEach(::addPathSegment) }
            .build()

    private companion object {
        const val MAX_REFERENCE_LENGTH = 255
    }
}
