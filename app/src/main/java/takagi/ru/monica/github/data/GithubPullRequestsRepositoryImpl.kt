package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubMergeMethod
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubMergeResult
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestListQuery
import takagi.ru.monica.github.domain.GithubPullRequestRef
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubReviewState
import takagi.ru.monica.github.domain.GithubRequestedReviewersUpdate

class GithubPullRequestsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubPullRequestsRepository {
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    override suspend fun pullRequests(
        owner: String,
        name: String,
        query: GithubPullRequestListQuery,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubPullRequest>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "pulls").newBuilder()
                .addQueryParameter("state", query.state.name.lowercase())
                .addQueryParameter("sort", query.sort.name.lowercase())
                .addQueryParameter("direction", query.direction.name.lowercase())
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("pull-requests", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubPullRequestDto.serializer()),
                        body
                    ).map(GithubPullRequestDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun pullRequest(owner: String, name: String, number: Int): Result<GithubPullRequest> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val url = endpoint(owner, name, "pulls", number.toString()).toString()
                val cacheKey = GithubCacheKeys.endpoint("pull-requests", requests.cacheScope(), url)
                cachedGet.execute(
                    client = client,
                    cacheKey = cacheKey,
                    request = { etag ->
                        requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                    },
                    decode = { body, _ ->
                        json.decodeFromString(
                            GithubPullRequestDto.serializer(),
                            body
                        ).toDomain()
                    }
                )
            }
        }

    override suspend fun files(
        owner: String,
        name: String,
        number: Int,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubPullRequestFile>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "pulls", number.toString(), "files").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("pull-request-files", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubPullRequestFileDto.serializer()),
                        body
                    ).map(GithubPullRequestFileDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun reviews(
        owner: String,
        name: String,
        number: Int,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubPullRequestReview>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "pulls", number.toString(), "reviews").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("pull-request-reviews", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubPullRequestReviewDto.serializer()),
                        body
                    ).map(GithubPullRequestReviewDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun reviewComments(
        owner: String,
        name: String,
        number: Int,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubPullRequestReviewComment>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "pulls", number.toString(), "comments").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint(
                "pull-request-review-comments",
                requests.cacheScope(),
                url.toString()
            )
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val items = json.decodeFromString(
                        ListSerializer(GithubPullRequestReviewCommentDto.serializer()),
                        body
                    ).map(GithubPullRequestReviewCommentDto::toDomain)
                    GithubPage(items, GithubPagination.nextPage(linkHeader))
                }
            )
        }
    }

    override suspend fun submitReview(
        owner: String,
        name: String,
        number: Int,
        draft: GithubPullRequestReviewDraft
    ): Result<GithubPullRequestReview> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(
                SubmitReviewRequest(body = draft.body, event = draft.event.name)
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "pulls", number.toString(), "reviews").toString())
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubPullRequestReviewDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun merge(
        owner: String,
        name: String,
        number: Int,
        draft: GithubMergeDraft
    ): Result<GithubMergeResult> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(
                MergeRequest(
                    sha = draft.expectedHeadSha,
                    mergeMethod = draft.method.name.lowercase(),
                    commitTitle = draft.commitTitle,
                    commitMessage = draft.commitMessage
                )
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "pulls", number.toString(), "merge").toString())
                .put(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(MergeResponse.serializer(), response.body?.string().orEmpty()).toDomain()
                }
            }
        }
    }

    override suspend fun updateState(
        owner: String,
        name: String,
        number: Int,
        state: GithubPullRequestState
    ): Result<GithubPullRequest> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(UpdateStateRequest(state.name.lowercase())).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "pulls", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubPullRequestDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun updateContent(
        owner: String,
        name: String,
        number: Int,
        draft: GithubPullRequestDraft
    ): Result<GithubPullRequest> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val payload = json.encodeToString(
                UpdateContentRequest(title = draft.title, body = draft.body)
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = requests.builder(endpoint(owner, name, "pulls", number.toString()).toString())
                .patch(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException(response.code)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubPullRequestDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun updateRequestedReviewers(
        owner: String,
        name: String,
        number: Int,
        update: GithubRequestedReviewersUpdate
    ): Result<GithubPullRequest> = withContext(Dispatchers.IO) {
        githubRunCatching {
            var latest: GithubPullRequest? = null
            if (update.toRemove.isNotEmpty()) {
                latest = writeRequestedReviewers(
                    owner = owner,
                    name = name,
                    number = number,
                    reviewers = update.toRemove.sorted(),
                    remove = true
                )
            }
            if (update.toAdd.isNotEmpty()) {
                latest = writeRequestedReviewers(
                    owner = owner,
                    name = name,
                    number = number,
                    reviewers = update.toAdd.sorted(),
                    remove = false
                )
            }
            latest ?: pullRequest(owner, name, number).getOrThrow()
        }
    }

    private fun writeRequestedReviewers(
        owner: String,
        name: String,
        number: Int,
        reviewers: List<String>,
        remove: Boolean
    ): GithubPullRequest {
        val payload = json.encodeToString(RequestedReviewersRequest(reviewers)).toRequestBody(JSON_MEDIA_TYPE)
        val builder = requests.builder(
            endpoint(owner, name, "pulls", number.toString(), "requested_reviewers").toString()
        )
        val request = if (remove) builder.delete(payload).build() else builder.post(payload).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GithubApiException(response.code)
            cacheStore.invalidateAfter {
                json.decodeFromString(
                    GithubPullRequestDto.serializer(),
                    response.body?.string().orEmpty()
                ).toDomain()
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
    private data class GithubPullRequestDto(
        val id: Long,
        val number: Int,
        val title: String,
        val body: String? = null,
        val state: String,
        val draft: Boolean = false,
        val merged: Boolean = false,
        val locked: Boolean = false,
        val milestone: GithubPullRequestMilestoneDto? = null,
        val mergeable: Boolean? = null,
        @SerialName("mergeable_state") val mergeableState: String? = null,
        val user: GithubUserDto? = null,
        val labels: List<GithubLabelDto> = emptyList(),
        val assignees: List<GithubUserDto> = emptyList(),
        @SerialName("requested_reviewers") val requestedReviewers: List<GithubUserDto> = emptyList(),
        val head: GithubPullRequestRefDto,
        val base: GithubPullRequestRefDto,
        val comments: Int = 0,
        @SerialName("review_comments") val reviewComments: Int = 0,
        val commits: Int = 0,
        val additions: Int = 0,
        val deletions: Int = 0,
        @SerialName("changed_files") val changedFiles: Int = 0,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("closed_at") val closedAt: String? = null,
        @SerialName("merged_at") val mergedAt: String? = null,
        @SerialName("html_url") val htmlUrl: String
    ) {
        fun toDomain() = GithubPullRequest(
            id = id,
            number = number,
            title = title,
            body = body,
            state = if (state.equals("closed", ignoreCase = true)) {
                GithubPullRequestState.CLOSED
            } else {
                GithubPullRequestState.OPEN
            },
            isDraft = draft,
            isMerged = merged || mergedAt != null,
            mergeable = mergeable,
            mergeableState = mergeableState,
            author = user.toDomainOrGhost(),
            labels = labels.map(GithubLabelDto::toDomain),
            assignees = assignees.map(GithubUserDto::toDomain),
            requestedReviewers = requestedReviewers.map(GithubUserDto::toDomain),
            head = head.toDomain(),
            base = base.toDomain(),
            comments = comments,
            reviewComments = reviewComments,
            commits = commits,
            additions = additions,
            deletions = deletions,
            changedFiles = changedFiles,
            createdAt = createdAt,
            updatedAt = updatedAt,
            closedAt = closedAt,
            mergedAt = mergedAt,
            htmlUrl = htmlUrl,
            isLocked = locked,
            milestone = milestone?.toDomain()
        )
    }

    @Serializable
    private data class GithubPullRequestRefDto(
        val label: String,
        val ref: String,
        val sha: String,
        val repo: GithubPullRequestRepositoryDto? = null
    ) {
        fun toDomain() = GithubPullRequestRef(label, ref, sha, repo?.fullName)
    }

    @Serializable
    private data class GithubPullRequestRepositoryDto(
        @SerialName("full_name") val fullName: String
    )

    @Serializable
    private data class GithubPullRequestMilestoneDto(
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
    private data class GithubPullRequestFileDto(
        val sha: String,
        val filename: String,
        val status: String,
        val additions: Int = 0,
        val deletions: Int = 0,
        val changes: Int = 0,
        @SerialName("blob_url") val blobUrl: String,
        @SerialName("raw_url") val rawUrl: String? = null,
        val patch: String? = null
    ) {
        fun toDomain() = GithubPullRequestFile(
            sha, filename, status, additions, deletions, changes, patch, blobUrl, rawUrl
        )
    }

    @Serializable
    private data class GithubPullRequestReviewDto(
        val id: Long,
        val body: String? = null,
        val state: String,
        val user: GithubUserDto? = null,
        @SerialName("submitted_at") val submittedAt: String? = null,
        @SerialName("html_url") val htmlUrl: String
    ) {
        fun toDomain() = GithubPullRequestReview(
            id = id,
            body = body.orEmpty(),
            state = when (state.uppercase()) {
                "PENDING" -> GithubReviewState.PENDING
                "COMMENTED" -> GithubReviewState.COMMENTED
                "APPROVED" -> GithubReviewState.APPROVED
                "CHANGES_REQUESTED" -> GithubReviewState.CHANGES_REQUESTED
                "DISMISSED" -> GithubReviewState.DISMISSED
                else -> GithubReviewState.UNKNOWN
            },
            author = user.toDomainOrGhost(),
            submittedAt = submittedAt,
            htmlUrl = htmlUrl
        )
    }

    @Serializable
    private data class GithubPullRequestReviewCommentDto(
        val id: Long,
        val body: String,
        val path: String,
        val line: Int? = null,
        @SerialName("start_line") val startLine: Int? = null,
        val side: String? = null,
        @SerialName("diff_hunk") val diffHunk: String = "",
        val user: GithubUserDto? = null,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("html_url") val htmlUrl: String
    ) {
        fun toDomain() = GithubPullRequestReviewComment(
            id = id,
            body = body,
            path = path,
            line = line,
            startLine = startLine,
            side = side,
            diffHunk = diffHunk,
            author = user.toDomainOrGhost(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            htmlUrl = htmlUrl
        )
    }

    @Serializable
    private data class SubmitReviewRequest(val body: String?, val event: String)

    @Serializable
    private data class MergeRequest(
        val sha: String,
        @SerialName("merge_method") val mergeMethod: String,
        @SerialName("commit_title") val commitTitle: String? = null,
        @SerialName("commit_message") val commitMessage: String? = null
    )

    @Serializable
    private data class UpdateStateRequest(val state: String)

    @Serializable
    private data class UpdateContentRequest(val title: String, val body: String?)

    @Serializable
    private data class RequestedReviewersRequest(val reviewers: List<String>)

    @Serializable
    private data class MergeResponse(val sha: String? = null, val merged: Boolean, val message: String) {
        fun toDomain() = GithubMergeResult(sha, merged, message)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
