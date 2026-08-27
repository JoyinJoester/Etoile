package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryViewerState

class GithubRepositoryActionsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore
) : GithubRepositoryActionsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()

    override suspend fun viewerState(
        owner: String,
        name: String
    ): Result<GithubRepositoryViewerState> = withContext(Dispatchers.IO) {
        githubRunCatching {
            GithubRepositoryViewerState(
                isStarred = isStarred(owner, name),
                isWatching = isWatching(owner, name)
            )
        }
    }

    override suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val requestBuilder = requests.builder(starEndpoint(owner, name).toString())
            val request = if (starred) {
                requestBuilder.put(EMPTY_BODY).build()
            } else {
                requestBuilder.delete().build()
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter { starred }
            }
        }
    }

    override suspend fun setWatching(
        owner: String,
        name: String,
        watching: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val requestBuilder = requests.builder(subscriptionEndpoint(owner, name).toString())
            val request = if (watching) {
                val body = json.encodeToString(
                    SetSubscriptionRequest(subscribed = true, ignored = false)
                ).toRequestBody(JSON_MEDIA_TYPE)
                requestBuilder.put(body).build()
            } else {
                requestBuilder.delete().build()
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter { watching }
            }
        }
    }

    override suspend fun fork(owner: String, name: String): Result<GithubRepository> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val request = requests.builder(repositoryEndpoint(owner, name, "forks").toString())
                    .post(EMPTY_BODY)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException(response.code)
                    cacheStore.invalidateAfter {
                        json.decodeFromString(
                            GithubRepositoryDto.serializer(),
                            response.body?.string().orEmpty()
                        ).toDomain()
                    }
                }
            }
        }

    private fun isStarred(owner: String, name: String): Boolean {
        val request = requests.builder(starEndpoint(owner, name).toString()).get().build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                204 -> true
                404 -> false
                else -> throw GithubApiException(response.code)
            }
        }
    }

    private fun isWatching(owner: String, name: String): Boolean {
        val request = requests.builder(subscriptionEndpoint(owner, name).toString()).get().build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> false
                !response.isSuccessful -> throw GithubApiException(response.code)
                else -> json.decodeFromString(
                    SubscriptionResponse.serializer(),
                    response.body?.string().orEmpty()
                ).let { it.subscribed && !it.ignored }
            }
        }
    }

    private fun starEndpoint(owner: String, name: String): HttpUrl = apiBaseUrl.newBuilder()
        .addPathSegment("user")
        .addPathSegment("starred")
        .addPathSegment(owner)
        .addPathSegment(name)
        .build()

    private fun subscriptionEndpoint(owner: String, name: String): HttpUrl =
        repositoryEndpoint(owner, name, "subscription")

    private fun repositoryEndpoint(owner: String, name: String, vararg children: String): HttpUrl =
        apiBaseUrl.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .apply { children.forEach(::addPathSegment) }
            .build()

    @Serializable
    private data class SetSubscriptionRequest(
        val subscribed: Boolean,
        val ignored: Boolean
    )

    @Serializable
    private data class SubscriptionResponse(
        val subscribed: Boolean = false,
        val ignored: Boolean = false
    )

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
