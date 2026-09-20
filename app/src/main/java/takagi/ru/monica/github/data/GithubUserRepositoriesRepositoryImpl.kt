package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryCreateDraft
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository

class GithubUserRepositoriesRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubUserRepositoriesRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun repositories(
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepository>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("user")
                .addPathSegment("repos")
                .addQueryParameter("affiliation", "owner,collaborator,organization_member")
                .addQueryParameter("visibility", "all")
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "desc")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("user-repositories", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.builder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubRepositoryDto.serializer()),
                        body
                    ).map(GithubRepositoryDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun create(draft: GithubRepositoryCreateDraft): Result<GithubRepository> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val payload = buildJsonObject {
                    put("name", draft.name)
                    draft.description?.let { put("description", it) }
                    put("private", draft.isPrivate)
                    put("auto_init", draft.autoInit)
                }
                val url = apiBaseUrl.newBuilder()
                    .addPathSegment("user")
                    .addPathSegment("repos")
                    .build()
                val request = requests.builder(url.toString())
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException.of(response)
                    val created = json.decodeFromString(
                        GithubRepositoryDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                    cacheStore.clear()
                    created
                }
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
