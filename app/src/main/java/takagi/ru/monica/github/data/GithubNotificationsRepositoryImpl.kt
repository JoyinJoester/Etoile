package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubNotificationReason
import takagi.ru.monica.github.domain.GithubNotificationsRepository
import takagi.ru.monica.github.domain.GithubPage
import java.time.Instant

class GithubNotificationsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubNotificationsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun notifications(
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubNotification>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = apiBaseUrl.newBuilder()
                .addPathSegment("notifications")
                .addQueryParameter("all", "false")
                .addQueryParameter("participating", "false")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("notifications", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.builder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(NotificationDto.serializer()),
                        body
                    ).map(NotificationDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun markRead(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val request = requests.builder(threadEndpoint(id))
                .patch(EMPTY_JSON)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.clear()
            }
        }
    }

    override suspend fun markDone(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            delete(threadEndpoint(id))
            cacheStore.clear()
        }
    }

    override suspend fun unsubscribeAndMarkDone(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            delete(threadEndpoint(id, "subscription"))
            delete(threadEndpoint(id))
            cacheStore.clear()
        }
    }

    override suspend fun markAllRead(): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val body = "{\"last_read_at\":\"${Instant.now()}\"}".toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint("notifications")).put(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.clear()
            }
        }
    }

    @Serializable
    private data class NotificationDto(
        val id: String,
        val reason: String,
        val unread: Boolean,
        @SerialName("updated_at") val updatedAt: String,
        val subject: SubjectDto,
        val repository: RepositoryDto
    ) {
        fun toDomain() = GithubNotification(
            id = id,
            reason = when (reason) {
                "review_requested" -> GithubNotificationReason.REVIEW_REQUESTED
                "mention", "team_mention" -> GithubNotificationReason.MENTION
                "assign" -> GithubNotificationReason.ASSIGN
                "author" -> GithubNotificationReason.AUTHOR
                "comment" -> GithubNotificationReason.COMMENT
                "invitation" -> GithubNotificationReason.INVITATION
                else -> GithubNotificationReason.OTHER
            },
            unread = unread,
            title = subject.title,
            subjectType = subject.type,
            repository = repository.fullName,
            repositoryUrl = repository.htmlUrl,
            updatedAt = updatedAt,
            subjectUrl = subject.webUrl(repository.htmlUrl)
        )
    }

    @Serializable
    private data class SubjectDto(
        val title: String,
        val type: String,
        @SerialName("url") val apiUrl: String? = null
    ) {
        fun webUrl(repositoryUrl: String): String? {
            val number = apiUrl
                ?.substringBefore('?')
                ?.trimEnd('/')
                ?.substringAfterLast('/')
                ?.toLongOrNull()
                ?: return null
            val path = when (type.lowercase()) {
                "pullrequest" -> "pull/$number"
                "issue" -> "issues/$number"
                "discussion" -> "discussions/$number"
                "release" -> "releases"
                else -> return null
            }
            return "${repositoryUrl.trimEnd('/')}/$path"
        }
    }

    @Serializable
    private data class RepositoryDto(
        @SerialName("full_name") val fullName: String,
        @SerialName("html_url") val htmlUrl: String
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_JSON = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun threadEndpoint(id: String, vararg suffix: String): String = apiBaseUrl.newBuilder()
        .addPathSegment("notifications")
        .addPathSegment("threads")
        .addPathSegment(id)
        .apply { suffix.forEach { addPathSegment(it) } }
        .build()
        .toString()

    private fun delete(url: String) {
        val request = requests.builder(url).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GithubApiException(response.code)
        }
    }
}
