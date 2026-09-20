package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import takagi.ru.monica.github.domain.GithubActionsLog
import takagi.ru.monica.github.domain.GithubActionsRepository
import takagi.ru.monica.github.domain.GithubArtifactOutput
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowArtifact
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowRunAction

class GithubActionsRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val maxLogBytes: Long = DEFAULT_MAX_LOG_BYTES,
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubActionsRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        require(maxLogBytes > 0)
    }

    override suspend fun workflows(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflow>> = workflowsInternal(owner, name, page, perPage, forceRefresh = false)

    override suspend fun refreshWorkflows(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflow>> = workflowsInternal(owner, name, page, perPage, forceRefresh = true)

    private suspend fun workflowsInternal(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
        forceRefresh: Boolean
    ): Result<GithubPage<GithubWorkflow>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "workflows").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("actions-workflows", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val payload = json.decodeFromString(
                        GithubWorkflowsResponseDto.serializer(),
                        body
                    )
                    GithubPage(
                        items = payload.workflows.map(GithubWorkflowDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                },
                maxAgeMillis = if (forceRefresh) 0L else cacheStore.defaultMaxAgeMillis
            )
        }
    }

    override suspend fun workflowRuns(
        owner: String,
        name: String,
        workflowId: Long,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflowRun>> = workflowRunsInternal(
        owner, name, workflowId, page, perPage, forceRefresh = false
    )

    override suspend fun refreshWorkflowRuns(
        owner: String,
        name: String,
        workflowId: Long,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflowRun>> = workflowRunsInternal(
        owner, name, workflowId, page, perPage, forceRefresh = true
    )

    private suspend fun workflowRunsInternal(
        owner: String,
        name: String,
        workflowId: Long,
        page: Int,
        perPage: Int,
        forceRefresh: Boolean
    ): Result<GithubPage<GithubWorkflowRun>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "workflows", workflowId.toString(), "runs").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("actions-runs", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val payload = json.decodeFromString(
                        GithubWorkflowRunsResponseDto.serializer(),
                        body
                    )
                    GithubPage(
                        items = payload.workflowRuns.map(GithubWorkflowRunDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                },
                maxAgeMillis = if (forceRefresh) 0L else cacheStore.defaultMaxAgeMillis
            )
        }
    }

    override suspend fun workflowRun(
        owner: String,
        name: String,
        runId: Long
    ): Result<GithubWorkflowRun> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "runs", runId.toString()).toString()
            val cacheKey = GithubCacheKeys.endpoint("actions-runs", requests.cacheScope(), url)
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, _ ->
                    json.decodeFromString(
                        GithubWorkflowRunDto.serializer(),
                        body
                    ).toDomain()
                }
            )
        }
    }

    override suspend fun jobs(
        owner: String,
        name: String,
        runId: Long,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflowJob>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "runs", runId.toString(), "jobs").newBuilder()
                .addQueryParameter("filter", "latest")
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("actions-jobs", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val payload = json.decodeFromString(
                        GithubWorkflowJobsResponseDto.serializer(),
                        body
                    )
                    GithubPage(
                        items = payload.jobs.map(GithubWorkflowJobDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun job(
        owner: String,
        name: String,
        jobId: Long
    ): Result<GithubWorkflowJob> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "jobs", jobId.toString()).toString()
            val cacheKey = GithubCacheKeys.endpoint("actions-jobs", requests.cacheScope(), url)
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url).get().withCacheValidator(etag).build()
                },
                decode = { body, _ ->
                    json.decodeFromString(
                        GithubWorkflowJobDto.serializer(),
                        body
                    ).toDomain()
                }
            )
        }
    }

    override suspend fun jobLog(
        owner: String,
        name: String,
        jobId: Long
    ): Result<GithubActionsLog> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val apiRequest = requests.optionalBuilder(
                endpoint(owner, name, "actions", "jobs", jobId.toString(), "logs").toString()
            ).get().build()

            val redirectUrl = noRedirectClient.newCall(apiRequest).execute().use { response ->
                if (response.isSuccessful) return@githubRunCatching readLog(response.body)
                if (response.code !in 300..399) throw GithubApiException.of(response)
                resolveDownloadRedirect(response.header("Location"))
            }
            val downloadRequest = Request.Builder()
                .url(redirectUrl)
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .get()
                .build()
            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                readLog(response.body)
            }
        }
    }

    override suspend fun artifacts(
        owner: String,
        name: String,
        runId: Long,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubWorkflowArtifact>> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val url = endpoint(owner, name, "actions", "runs", runId.toString(), "artifacts").newBuilder()
                .addQueryParameter("per_page", perPage.coerceIn(1, 100).toString())
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
            val cacheKey = GithubCacheKeys.endpoint("actions-artifacts", requests.cacheScope(), url.toString())
            cachedGet.execute(
                client = client,
                cacheKey = cacheKey,
                request = { etag ->
                    requests.optionalBuilder(url.toString()).get().withCacheValidator(etag).build()
                },
                decode = { body, linkHeader ->
                    val payload = json.decodeFromString(
                        GithubWorkflowArtifactsResponseDto.serializer(),
                        body
                    )
                    GithubPage(
                        items = payload.artifacts.map(GithubWorkflowArtifactDto::toDomain),
                        nextPage = GithubPagination.nextPage(linkHeader)
                    )
                }
            )
        }
    }

    override suspend fun downloadArtifact(
        owner: String,
        name: String,
        artifactId: Long,
        open: () -> GithubArtifactOutput
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val apiRequest = requests.optionalBuilder(
                endpoint(owner, name, "actions", "artifacts", artifactId.toString(), "zip").toString()
            ).get().build()

            val redirectUrl = noRedirectClient.newCall(apiRequest).execute().use { response ->
                if (response.isSuccessful) return@githubRunCatching writeArtifact(response.body, open)
                if (response.code !in 300..399) throw GithubApiException.of(response)
                resolveDownloadRedirect(response.header("Location"))
            }
            val downloadRequest = Request.Builder()
                .url(redirectUrl)
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .get()
                .build()
            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                writeArtifact(response.body, open)
            }
        }
    }

    override suspend fun performRunAction(
        owner: String,
        name: String,
        runId: Long,
        action: GithubWorkflowRunAction
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val path = when (action) {
                GithubWorkflowRunAction.RERUN -> "rerun"
                GithubWorkflowRunAction.CANCEL -> "cancel"
            }
            val request = requests.builder(
                endpoint(owner, name, "actions", "runs", runId.toString(), path).toString()
            ).post("{}".toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.clear()
                Unit
            }
        }
    }

    override suspend fun setWorkflowEnabled(
        owner: String,
        name: String,
        workflowId: Long,
        enabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val action = if (enabled) "enable" else "disable"
            val request = requests.builder(
                endpoint(owner, name, "actions", "workflows", workflowId.toString(), action).toString()
            ).put("{}".toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.clear()
                Unit
            }
        }
    }

    override suspend fun dispatchWorkflow(
        owner: String,
        name: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val normalizedRef = ref.trim()
            require(normalizedRef.isNotBlank()) { "Workflow ref must not be blank" }
            require(inputs.keys.none(String::isBlank)) { "Workflow input names must not be blank" }
            require(inputs.size <= MAX_DISPATCH_INPUTS) {
                "GitHub accepts at most $MAX_DISPATCH_INPUTS workflow_dispatch inputs"
            }
            val payload = buildJsonObject {
                put("ref", normalizedRef)
                if (inputs.isNotEmpty()) {
                    put("inputs", buildJsonObject {
                        inputs.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                    })
                }
            }
            val request = requests.builder(
                endpoint(owner, name, "actions", "workflows", workflowId.toString(), "dispatches").toString()
            ).post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.clear()
                Unit
            }
        }
    }

    private fun resolveDownloadRedirect(location: String?): HttpUrl {
        val redirect = location?.toHttpUrlOrNull() ?: location?.let(apiBaseUrl::resolve)
            ?: throw IllegalStateException("GitHub did not provide a download redirect")
        if (apiBaseUrl.isHttps && !redirect.isHttps) {
            throw IllegalStateException("Insecure GitHub download redirect")
        }
        return redirect
    }

    private fun writeArtifact(body: ResponseBody?, open: () -> GithubArtifactOutput) {
        val source = body?.source() ?: throw IllegalStateException("GitHub did not return the artifact")
        val output = open()
        try {
            val buffer = Buffer()
            while (true) {
                if (source.read(buffer, ARTIFACT_CHUNK_BYTES) == -1L) break
                val chunk = buffer.readByteArray()
                output.write(chunk, 0, chunk.size)
            }
        } finally {
            output.close()
        }
    }

    private fun readLog(body: ResponseBody?): GithubActionsLog {
        val source = body?.source() ?: return GithubActionsLog(text = "", isTruncated = false)
        val sink = Buffer()
        var remaining = maxLogBytes
        while (remaining > 0) {
            val read = source.read(sink, minOf(LOG_READ_CHUNK_BYTES, remaining))
            if (read == -1L) break
            remaining -= read
        }
        val truncated = !source.exhausted()
        return GithubActionsLog(
            text = sink.readString(Charsets.UTF_8),
            isTruncated = truncated
        )
    }

    private fun endpoint(owner: String, name: String, vararg segments: String): HttpUrl =
        apiBaseUrl.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(name)
            .apply { segments.forEach(::addPathSegment) }
            .build()

    private companion object {
        const val MAX_DISPATCH_INPUTS = 25
        const val DEFAULT_MAX_LOG_BYTES = 256L * 1024L
        const val LOG_READ_CHUNK_BYTES = 8L * 1024L
        const val ARTIFACT_CHUNK_BYTES = 32L * 1024L
        const val DOWNLOAD_USER_AGENT = "Etoile-GitHub-Client"
    }
}
