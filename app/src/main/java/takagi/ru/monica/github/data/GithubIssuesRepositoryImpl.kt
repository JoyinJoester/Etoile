package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubReactionCounts
import takagi.ru.monica.github.domain.GithubReactionToggle
import takagi.ru.monica.github.domain.GithubUserSummary

class GithubIssuesRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubIssuesRepository {
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun issues(
        owner: String,
        name: String,
        query: GithubIssueListQuery,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssue>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "issues")
                .newBuilder()
                .addQueryParameter("state", query.state.name.lowercase())
                .addQueryParameter("sort", query.sort.name.lowercase())
                .addQueryParameter("direction", query.direction.name.lowercase())
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("issues", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubIssueDto.serializer()),
                        body
                    ).filter { it.pullRequest == null }.map(GithubIssueDto::toDomain)
                    GithubPage(items = items, nextPage = GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun issue(owner: String, name: String, number: Int): Result<GithubIssue> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val url = endpoint(owner, name, "issues", number.toString()).toString()
                val cacheKey = GithubCacheKeys.endpoint("issues", requests.cacheScope(), url)
                cachedGet.execute(
                    client = client,
                    cacheKey = cacheKey,
                    request = { etag ->
                        requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                    },
                    decode = { body, _ ->
                        json.decodeFromString(
                            GithubIssueDto.serializer(),
                            body
                        ).toDomain()
                    }
                )
            }
        }

    override suspend fun comments(
        owner: String,
        name: String,
        number: Int,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueComment>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "issues", number.toString(), "comments")
                .newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("issue-comments", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubIssueCommentDto.serializer()),
                        body
                    ).map(GithubIssueCommentDto::toDomain)
                    GithubPage(items = items, nextPage = GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun createIssue(
        owner: String,
        name: String,
        draft: GithubIssueDraft
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(CreateIssueRequest(draft.title, draft.body))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues").toString())
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun updateIssue(
        owner: String,
        name: String,
        number: Int,
        draft: GithubIssueDraft
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(CreateIssueRequest(draft.title, draft.body))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueDto.serializer(), response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun addComment(
        owner: String,
        name: String,
        number: Int,
        draft: GithubIssueCommentDraft
    ): Result<GithubIssueComment> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(CreateCommentRequest(draft.body)).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues", number.toString(), "comments").toString())
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueCommentDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun toggleCommentReaction(
        owner: String,
        name: String,
        commentId: Long,
        content: GithubReactionContent,
        viewerLogin: String
    ): Result<GithubReactionToggle> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val reactionsUrl = endpoint(
                owner,
                name,
                "issues",
                "comments",
                commentId.toString(),
                "reactions"
            )
            val reactionsRequest = requests.builder(reactionsUrl.toString()).get().build()
            val viewerReaction = client.newCall(reactionsRequest).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                json.decodeFromString(
                    ListSerializer(GithubReactionDto.serializer()),
                    response.body?.string().orEmpty()
                ).firstOrNull { reaction ->
                    reaction.content == content.apiValue &&
                        reaction.user?.login?.equals(viewerLogin, ignoreCase = true) == true
                }
            }

            if (viewerReaction != null) {
                val request = requests.builder(
                    reactionsUrl.newBuilder()
                        .addPathSegment(viewerReaction.id.toString())
                        .build()
                        .toString()
                ).delete().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException(response.code)
                    cacheStore.invalidateAfter {
                        GithubReactionToggle(content, active = false, reactionId = viewerReaction.id)
                    }
                }
            } else {
                val body = json.encodeToString(CreateReactionRequest(content.apiValue))
                    .toRequestBody(JSON_MEDIA_TYPE)
                val request = requests.builder(reactionsUrl.toString()).post(body).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw GithubApiException(response.code)
                    cacheStore.invalidateAfter {
                        val created = json.decodeFromString(
                            GithubReactionDto.serializer(),
                            response.body?.string().orEmpty()
                        )
                        GithubReactionToggle(content, active = true, reactionId = created.id)
                    }
                }
            }
        }
    }

    override suspend fun updateIssueState(
        owner: String,
        name: String,
        number: Int,
        state: GithubIssueState
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(UpdateIssueStateRequest(state.name.lowercase()))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun updateIssueLock(
        owner: String,
        name: String,
        number: Int,
        locked: Boolean
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "issues", number.toString(), "lock").toString()
            val request = if (locked) {
                requests.builder(url)
                    .put("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            } else {
                requests.builder(url).delete().build()
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
            }
            cacheStore.clear()
            issue(owner, name, number).getOrThrow()
        }
    }

    override suspend fun labels(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueLabel>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "labels").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("issue-labels", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubLabelDto.serializer()), body
                        ).map(GithubLabelDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun updateIssueLabels(
        owner: String,
        name: String,
        number: Int,
        labels: List<String>
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalized = labels.map(String::trim).filter(String::isNotBlank).distinct().take(100)
            val payload = json.encodeToString(UpdateLabelsRequest(normalized))
            val request = requests.builder(endpoint(owner, name, "issues", number.toString(), "labels").toString())
                .put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
            }
            cacheStore.clear()
            issue(owner, name, number).getOrThrow()
        }
    }

    override suspend fun assignees(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubUserSummary>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "assignees").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("issue-assignees", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubUserDto.serializer()), body
                        ).map(GithubUserDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun updateIssueAssignees(
        owner: String,
        name: String,
        number: Int,
        assignees: List<String>
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalized = assignees.map(String::trim).filter(String::isNotBlank).distinct().take(10)
            val payload = json.encodeToString(UpdateAssigneesRequest(normalized)).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueDto.serializer(), response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun milestones(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubIssueMilestone>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "milestones").newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("sort", "due_on")
                .addQueryParameter("direction", "asc")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("issue-milestones", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    GithubPage(
                        items = json.decodeFromString(
                            ListSerializer(GithubMilestoneDto.serializer()), body
                        ).map(GithubMilestoneDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun updateIssueMilestone(
        owner: String,
        name: String,
        number: Int,
        milestoneNumber: Int?
    ): Result<GithubIssue> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(UpdateMilestoneRequest(milestoneNumber))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "issues", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubIssueDto.serializer(), response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    private fun endpoint(owner: String, name: String, vararg segments: String) =
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .apply { segments.forEach(::addPathSegment) }
            .build()

    @Serializable
    private data class CreateIssueRequest(val title: String, val body: String?)

    @Serializable
    private data class CreateCommentRequest(val body: String)

    @Serializable
    private data class CreateReactionRequest(val content: String)

    @Serializable
    private data class UpdateIssueStateRequest(val state: String)

    @Serializable
    private data class GithubIssueDto(
        val id: Long,
        val number: Int,
        val title: String,
        val body: String? = null,
        val state: String,
        val user: GithubUserDto? = null,
        val labels: List<GithubLabelDto> = emptyList(),
        val assignees: List<GithubUserDto> = emptyList(),
        val milestone: GithubMilestoneDto? = null,
        val comments: Int = 0,
        val locked: Boolean = false,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("closed_at") val closedAt: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("pull_request") val pullRequest: JsonObject? = null
    ) {
        fun toDomain() = GithubIssue(
            id = id,
            number = number,
            title = title,
            body = body,
            state = if (state == "closed") GithubIssueState.CLOSED else GithubIssueState.OPEN,
            author = user.toDomainOrGhost(),
            labels = labels.map(GithubLabelDto::toDomain),
            assignees = assignees.map(GithubUserDto::toDomain),
            comments = comments,
            isLocked = locked,
            createdAt = createdAt,
            updatedAt = updatedAt,
            closedAt = closedAt,
            htmlUrl = htmlUrl,
            milestone = milestone?.toDomain()
        )
    }

    @Serializable
    private data class GithubIssueCommentDto(
        val id: Long,
        val body: String = "",
        val user: GithubUserDto? = null,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("html_url") val htmlUrl: String,
        val reactions: GithubReactionCountsDto = GithubReactionCountsDto()
    ) {
        fun toDomain() = GithubIssueComment(
            id = id,
            body = body,
            author = user.toDomainOrGhost(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            htmlUrl = htmlUrl,
            reactions = reactions.toDomain()
        )
    }

    @Serializable
    private data class GithubReactionDto(
        val id: Long,
        val content: String,
        val user: ReactionUserDto? = null
    )

    @Serializable
    private data class UpdateLabelsRequest(val labels: List<String>)

    @Serializable
    private data class UpdateAssigneesRequest(val assignees: List<String>)

    @Serializable
    private data class UpdateMilestoneRequest(val milestone: Int?)

    @Serializable
    private data class GithubMilestoneDto(
        val number: Int,
        val title: String,
        val description: String? = null,
        @SerialName("open_issues") val openIssues: Int = 0,
        @SerialName("closed_issues") val closedIssues: Int = 0,
        @SerialName("due_on") val dueOn: String? = null
    ) {
        fun toDomain() = GithubIssueMilestone(
            number = number,
            title = title,
            description = description,
            openIssues = openIssues,
            closedIssues = closedIssues,
            dueOn = dueOn
        )
    }

    @Serializable
    private data class ReactionUserDto(val login: String)

    @Serializable
    private data class GithubReactionCountsDto(
        @SerialName("+1") val plusOne: Int = 0,
        @SerialName("-1") val minusOne: Int = 0,
        val laugh: Int = 0,
        val confused: Int = 0,
        val heart: Int = 0,
        val hooray: Int = 0,
        val rocket: Int = 0,
        val eyes: Int = 0
    ) {
        fun toDomain() = GithubReactionCounts(
            mapOf(
                GithubReactionContent.PLUS_ONE to plusOne,
                GithubReactionContent.MINUS_ONE to minusOne,
                GithubReactionContent.LAUGH to laugh,
                GithubReactionContent.CONFUSED to confused,
                GithubReactionContent.HEART to heart,
                GithubReactionContent.HOORAY to hooray,
                GithubReactionContent.ROCKET to rocket,
                GithubReactionContent.EYES to eyes
            ).filterValues { it > 0 }
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
