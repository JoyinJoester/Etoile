package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class GithubRepositoryDetailsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubRepositoryDetailsRepository {
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun details(owner: String, name: String): Result<GithubRepositoryDetails> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val url = repositoryEndpoint(owner, name)
                val cacheKey = GithubCacheKeys.endpoint("repository-details", requests.cacheScope(), url)
                cachedGet.execute(
                    client = client,
                    cacheKey = cacheKey,
                    request = { etag ->
                        requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                    },
                    decode = { body, _ ->
                        json.decodeFromString(
                            GithubRepositoryDto.serializer(),
                            body
                        ).toDetails()
                    }
                )
            }
        }

    override suspend fun readme(owner: String, name: String, ref: String?): Result<String?> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val request = requests.optionalBuilder(repositoryEndpoint(owner, name, "readme", ref))
                    .header("Accept", "application/vnd.github.raw+json")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> null
                        !response.isSuccessful -> throw GithubApiException(response.code)
                        else -> response.body?.string().orEmpty()
                    }
                }
            }
        }

    override suspend fun branchProtection(owner: String, name: String, branch: String): Result<GithubBranchProtection?> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val url = repositoryEndpoint(owner, name).toHttpUrl().newBuilder()
                    .addPathSegment("branches")
                    .addPathSegment(branch)
                    .addPathSegment("protection")
                    .build()
                    .toString()
                val cacheKey = GithubCacheKeys.endpoint("branch-protection", requests.cacheScope(), url)
                cachedGet.execute(
                    client = client,
                    cacheKey = cacheKey,
                    request = { etag -> requests.optionalBuilder(url).get().withCacheValidator(etag).build() },
                    decode = { body, _ ->
                        json.decodeFromString(BranchProtectionDto.serializer(), body).toDomain(branch)
                    }
                )
            }.recoverCatching { error ->
                if (error is GithubApiException && error.statusCode == 404) null else throw error
            }
        }

    override suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val normalized = topics
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map(String::lowercase)
                    .distinct()
                    .take(20)
                val payload = buildJsonObject {
                    putJsonArray("names") {
                        normalized.forEach { add(JsonPrimitive(it)) }
                    }
                }
                val url = repositoryEndpoint(owner, name, "topics")
                val request = requests.builder(url)
                    .header("Accept", "application/vnd.github+json")
                    .put(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException(response.code)
                    val body = response.body?.string().orEmpty()
                    val result = json.decodeFromString(TopicsResponseDto.serializer(), body)
                    cacheStore.clear()
                    result.names
                }
            }
        }

    override suspend fun collaborators(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubCollaborator>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(name)
                .addPathSegment("collaborators")
                .addQueryParameter("affiliation", "all")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("repository-collaborators", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.builder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(CollaboratorDto.serializer()), body
                        ).map(CollaboratorDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun webhooks(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRepositoryWebhook>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(name)
                .addPathSegment("hooks")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("repository-webhooks", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.builder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(WebhookDto.serializer()), body
                        ).map(WebhookDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    private fun repositoryEndpoint(
        owner: String,
        name: String,
        child: String? = null,
        ref: String? = null
    ): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
        child?.let(builder::addPathSegment)
        ref?.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("ref", it) }
        return builder.build().toString()
    }

    @Serializable
    private data class BranchProtectionDto(
        @SerialName("required_status_checks") val requiredStatusChecks: RequiredStatusChecksDto? = null,
        @SerialName("required_pull_request_reviews") val requiredPullRequestReviews: RequiredReviewsDto? = null,
        @SerialName("enforce_admins") val enforceAdmins: EnforceAdminsDto? = null
    ) {
        fun toDomain(branch: String) = GithubBranchProtection(
            branch = branch,
            requiredStatusChecks = requiredStatusChecks?.let { it.contexts.size + it.checks.size } ?: 0,
            requiredApprovingReviews = requiredPullRequestReviews?.requiredApprovingReviewCount,
            enforceAdmins = enforceAdmins?.enabled == true
        )
    }

    @Serializable
    private data class RequiredStatusChecksDto(
        val contexts: List<String> = emptyList(),
        val checks: List<StatusCheckDto> = emptyList()
    )

    @Serializable
    private data class StatusCheckDto(val context: String? = null)

    @Serializable
    private data class RequiredReviewsDto(
        @SerialName("required_approving_review_count") val requiredApprovingReviewCount: Int? = null
    )

    @Serializable
    private data class EnforceAdminsDto(val enabled: Boolean = false)

    @Serializable
    private data class TopicsResponseDto(val names: List<String> = emptyList())

    @Serializable
    private data class CollaboratorDto(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("role_name") val roleName: String? = null,
        val permissions: CollaboratorPermissionsDto? = null
    ) {
        fun toDomain() = GithubCollaborator(
            user = GithubUserSummary(login, avatarUrl, htmlUrl),
            role = when {
                roleName == "admin" || permissions?.admin == true -> GithubCollaboratorRole.ADMIN
                roleName == "maintain" || permissions?.maintain == true -> GithubCollaboratorRole.MAINTAIN
                roleName == "write" || permissions?.push == true -> GithubCollaboratorRole.WRITE
                roleName == "triage" || permissions?.triage == true -> GithubCollaboratorRole.TRIAGE
                roleName == "read" || permissions?.pull == true -> GithubCollaboratorRole.READ
                else -> GithubCollaboratorRole.UNKNOWN
            }
        )
    }

    @Serializable
    private data class CollaboratorPermissionsDto(
        val pull: Boolean = false,
        val triage: Boolean = false,
        val push: Boolean = false,
        val maintain: Boolean = false,
        val admin: Boolean = false
    )

    @Serializable
    private data class WebhookDto(
        val id: Long,
        val name: String = "web",
        val active: Boolean = false,
        val events: List<String> = emptyList(),
        @SerialName("last_response") val lastResponse: WebhookLastResponseDto? = null
    ) {
        fun toDomain() = GithubRepositoryWebhook(
            id = id,
            name = name,
            isActive = active,
            events = events,
            lastResponseCode = lastResponse?.code,
            lastResponseStatus = lastResponse?.status,
            lastResponseMessage = lastResponse?.message
        )
    }

    @Serializable
    private data class WebhookLastResponseDto(
        val code: Int? = null,
        val status: String? = null,
        val message: String? = null
    )
}
