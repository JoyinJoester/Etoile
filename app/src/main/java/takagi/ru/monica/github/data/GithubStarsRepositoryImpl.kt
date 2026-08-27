package takagi.ru.monica.github.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubStarCategory
import takagi.ru.monica.github.domain.GithubStarCategoryStore
import takagi.ru.monica.github.domain.GithubStarsRepository

class GithubStarsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubStarsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun starredRepositories(
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepository>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("user")
                .addPathSegment("starred")
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "desc")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("starred", requests.cacheScope(), url.toString())
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
}

class GithubPreferencesStarCategoryStore(context: Context) : GithubStarCategoryStore {
    private val preferences = context.applicationContext.getSharedPreferences("etoile_github_star_categories", Context.MODE_PRIVATE)

    override fun category(repositoryId: Long): GithubStarCategory =
        preferences.getString(repositoryId.toString(), null)
            ?.let { stored -> GithubStarCategory.entries.firstOrNull { it.name == stored } }
            ?: GithubStarCategory.ALL

    override fun setCategory(repositoryId: Long, category: GithubStarCategory) {
        val editor = preferences.edit()
        if (category == GithubStarCategory.ALL) editor.remove(repositoryId.toString())
        else editor.putString(repositoryId.toString(), category.name)
        editor.apply()
    }
}
