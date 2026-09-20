package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.domain.GithubReleaseAssetUpload
import takagi.ru.monica.github.domain.GithubReleaseDraft
import takagi.ru.monica.github.domain.GithubReleasesRepository
import java.util.concurrent.TimeUnit

class GithubReleasesRepositoryImpl(
    private val requests: GithubAuthenticatedRequests,
    private val client: OkHttpClient = GithubNetwork.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    baseUrl: String = "https://api.github.com/",
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore,
    cacheStatusReporter: GithubCacheStatusReporter = NoOpGithubCacheStatusReporter
) : GithubReleasesRepository {
    private val apiBaseUrl = baseUrl.toHttpUrl()
    private val cachedGet = GithubCachedGetExecutor(cacheStore, cacheStatusReporter)

    // The shared call timeout is tuned for metadata requests; an APK body needs minutes, not seconds.
    private val uploadClient = client.newBuilder()
        .callTimeout(UPLOAD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    override suspend fun releases(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRelease>> = releasesInternal(owner, name, page, perPage, forceRefresh = false)

    override suspend fun refreshReleases(
        owner: String,
        name: String,
        page: Int,
        perPage: Int
    ): Result<GithubPage<GithubRelease>> = releasesInternal(owner, name, page, perPage, forceRefresh = true)

    private suspend fun releasesInternal(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
        forceRefresh: Boolean
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
                },
                maxAgeMillis = if (forceRefresh) 0L else cacheStore.defaultMaxAgeMillis
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

    override suspend fun createRelease(
        owner: String,
        name: String,
        draft: GithubReleaseDraft
    ): Result<GithubRelease> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val request = requests.builder(endpoint(owner, name, "releases").toString())
                .post(payload(draft))
                .build()
            writeRelease(request)
        }
    }

    override suspend fun updateRelease(
        owner: String,
        name: String,
        releaseId: Long,
        draft: GithubReleaseDraft
    ): Result<GithubRelease> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(releaseId > 0)
            // Publishing a draft can be the moment its tag is created, so the target has to travel
            // with the patch even though an existing tag will not move.
            val request = requests.builder(endpoint(owner, name, "releases", releaseId.toString()).toString())
                .patch(payload(draft))
                .build()
            writeRelease(request)
        }
    }

    override suspend fun deleteRelease(
        owner: String,
        name: String,
        releaseId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(releaseId > 0)
            val request = requests.builder(endpoint(owner, name, "releases", releaseId.toString()).toString())
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.invalidateAfter { Unit }
            }
        }
    }

    override suspend fun uploadAsset(
        owner: String,
        name: String,
        releaseId: Long,
        asset: GithubReleaseAssetUpload
    ): Result<GithubReleaseAsset> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(releaseId > 0)
            val request = requests.builder(uploadEndpoint(owner, name, releaseId, asset).toString())
                .post(AssetBody(asset))
                .build()
            uploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.invalidateAfter {
                    json.decodeFromString(
                        GithubReleaseAssetDto.serializer(),
                        response.body?.string().orEmpty()
                    ).toDomain()
                }
            }
        }
    }

    override suspend fun deleteAsset(
        owner: String,
        name: String,
        assetId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        githubRunCatching {
            require(assetId > 0)
            val request = requests.builder(endpoint(owner, name, "releases", "assets", assetId.toString()).toString())
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                cacheStore.invalidateAfter { Unit }
            }
        }
    }

    private fun uploadEndpoint(
        owner: String,
        name: String,
        releaseId: Long,
        asset: GithubReleaseAssetUpload
    ): HttpUrl = endpoint(owner, name, "releases", releaseId.toString(), "assets").newBuilder()
        .apply { if (apiBaseUrl.host == API_HOST) host(UPLOAD_HOST) }
        .addQueryParameter("name", asset.fileName)
        .apply { if (asset.label.isNotEmpty()) addQueryParameter("label", asset.label) }
        .build()

    /** Calls [GithubReleaseAssetUpload.open] per attempt, so a redirected or retried call never replays an exhausted stream. */
    private class AssetBody(private val asset: GithubReleaseAssetUpload) : RequestBody() {
        override fun contentType() = asset.contentType.toMediaType()

        override fun contentLength() = asset.contentLength

        override fun writeTo(sink: BufferedSink) {
            val input = asset.open()
            try {
                val buffer = ByteArray(ASSET_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                }
            } finally {
                input.close()
            }
        }
    }

    private fun payload(draft: GithubReleaseDraft): RequestBody =
        buildJsonObject {
            put("tag_name", draft.tagName)
            put("name", draft.title)
            put("body", draft.body)
            put("draft", draft.isDraft)
            put("prerelease", draft.isPrerelease)
            draft.targetCommitish?.let { put("target_commitish", it) }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

    private fun writeRelease(request: Request): GithubRelease =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GithubApiException.of(response)
            cacheStore.invalidateAfter {
                json.decodeFromString(
                    GithubReleaseDto.serializer(),
                    response.body?.string().orEmpty()
                ).toDomain()
            }
        }

    private companion object {
        const val MAX_TAG_LENGTH = 255
        const val API_HOST = "api.github.com"
        const val UPLOAD_HOST = "uploads.github.com"
        const val UPLOAD_CALL_TIMEOUT_MINUTES = 10L
        const val ASSET_BUFFER_SIZE = 32 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
