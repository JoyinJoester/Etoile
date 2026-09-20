package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAssetInput
import takagi.ru.monica.github.domain.GithubReleaseAssetUpload
import takagi.ru.monica.github.domain.GithubReleaseDraft

class GithubReleasesRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun releasesMapAssetsAndLinkPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/releases?page=2")}>; rel=\"next\"")
                .setBody("[$RELEASE_JSON]")
        )
        val repository = repository()

        val page = repository.releases("openai", "codex", page = 1, perPage = 30).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/releases", request.requestUrl?.encodedPath)
        assertEquals("1", request.requestUrl?.queryParameter("page"))
        assertEquals("30", request.requestUrl?.queryParameter("per_page"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(2, page.nextPage)
        assertEquals("v1.2.0", page.items.single().tagName)
        assertTrue(page.items.single().isPrerelease)
        assertEquals("etoile-arm64.apk", page.items.single().assets.single().name)
        assertEquals(24_576L, page.items.single().assets.single().sizeBytes)
    }

    @Test
    fun releaseDetailUsesOptionalAuthenticationAndMapsDraftState() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RELEASE_JSON))
        val repository = repository(token = null)

        val release = repository.release("openai", "codex", 42).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/releases/42", request.requestUrl?.encodedPath)
        assertEquals(null, request.getHeader("Authorization"))
        assertFalse(release.isDraft)
        assertEquals("alice", release.author.login)
        assertEquals(8, release.assets.single().downloadCount)
    }

    @Test
    fun releaseByTagEncodesTheTagAsOneApiPathSegment() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RELEASE_JSON))
        val repository = repository()

        val release = repository.releaseByTag("openai", "codex", "preview/1.2").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/releases/tags/preview%2F1.2", request.requestUrl?.encodedPath)
        assertEquals("v1.2.0", release.tagName)
    }

    @Test
    fun releasesReuseCachedBodyWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"releases-v1\"")
                .setBody("[$RELEASE_JSON]")
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val repository = repository(cacheStore = TestGithubCacheStore())

        val first = repository.releases("openai", "codex").getOrThrow()
        val second = repository.releases("openai", "codex").getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"releases-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun createReleasePostsTheDraftToTheReleasesEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(RELEASE_JSON))
        val repository = repository()

        val release = repository.createRelease("openai", "codex", draft()).getOrThrow()
        val request = server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/releases", request.requestUrl?.encodedPath)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("v1.2.0", body.getValue("tag_name").jsonPrimitive.content)
        assertEquals("Etoile 1.2", body.getValue("name").jsonPrimitive.content)
        assertEquals("Notes", body.getValue("body").jsonPrimitive.content)
        assertEquals("release/1.x", body.getValue("target_commitish").jsonPrimitive.content)
        assertTrue(body.getValue("draft").jsonPrimitive.boolean)
        assertFalse(body.getValue("prerelease").jsonPrimitive.boolean)
        assertEquals(42L, release.id)
    }

    @Test
    fun updateReleasePatchesTheReleaseByIdAndOmitsAnAbsentTarget() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RELEASE_JSON))
        val repository = repository()

        repository.updateRelease("openai", "codex", 42, draft(targetCommitish = null)).getOrThrow()
        val request = server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals("PATCH", request.method)
        assertEquals("/repos/openai/codex/releases/42", request.requestUrl?.encodedPath)
        assertTrue(!body.containsKey("target_commitish"))
        assertEquals("v1.2.0", body.getValue("tag_name").jsonPrimitive.content)
    }

    @Test
    fun deleteReleaseSendsAnAuthenticatedDelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository()

        assertTrue(repository.deleteRelease("openai", "codex", 42).isSuccess)
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/releases/42", request.requestUrl?.encodedPath)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    @Test
    fun releaseWritesNeverLeaveTheDeviceWithoutASession() = runTest {
        val repository = repository(token = null)

        val created = repository.createRelease("openai", "codex", draft())
        val deleted = repository.deleteRelease("openai", "codex", 42)
        val uploaded = repository.uploadAsset("openai", "codex", 42, upload(ByteArray(8)))

        assertTrue(created.exceptionOrNull() is GithubSignedOutException)
        assertTrue(deleted.exceptionOrNull() is GithubSignedOutException)
        assertTrue(uploaded.exceptionOrNull() is GithubSignedOutException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun releaseWriteInvalidatesTheCachedResponses() = runTest {
        val cacheStore = TestGithubCacheStore()
        val repository = repository(cacheStore = cacheStore)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"releases-v1\"")
                .setBody("[$RELEASE_JSON]")
        )
        assertTrue(repository.releases("openai", "codex").getOrThrow().items.isNotEmpty())
        server.takeRequest()
        assertTrue(!cacheStore.isEmpty())

        server.enqueue(MockResponse().setResponseCode(201).setBody(RELEASE_JSON))
        repository.createRelease("openai", "codex", draft()).getOrThrow()

        assertTrue(cacheStore.isEmpty())
    }

    @Test
    fun uploadAssetStreamsTheFileAsTheRequestBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(ASSET_JSON))
        val repository = repository()
        val payload = ByteArray(300) { (it % 251).toByte() }
        val opened = mutableListOf<FakeAssetInput>()

        val asset = repository.uploadAsset(
            "openai",
            "codex",
            42,
            GithubReleaseAssetUpload.fromFile(
                fileName = "Etoile-arm64.apk",
                label = "Android arm64",
                contentType = "application/vnd.android.package-archive",
                contentLength = payload.size.toLong(),
                open = { FakeAssetInput(payload).also { opened += it } }
            ).getOrThrow()
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/releases/42/assets", request.requestUrl?.encodedPath)
        assertEquals("Etoile-arm64.apk", request.requestUrl?.queryParameter("name"))
        assertEquals("Android arm64", request.requestUrl?.queryParameter("label"))
        assertEquals("application/vnd.android.package-archive", request.getHeader("Content-Type"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertArrayEquals(payload, request.body.readByteArray())
        assertEquals("etoile-arm64.apk", asset.name)
        assertEquals(24_576L, asset.sizeBytes)
        assertTrue(opened.single().closed)
    }

    @Test
    fun failedUploadKeepsTheGithubStatusCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Validation Failed"}"""))
        val repository = repository()

        val result = repository.uploadAsset("openai", "codex", 42, upload(ByteArray(4)))
        val request = server.takeRequest()

        assertEquals(422, (result.exceptionOrNull() as GithubApiException).statusCode)
        assertEquals("POST", request.method)
    }

    @Test
    fun deleteAssetSendsAnAuthenticatedDelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository()

        assertTrue(repository.deleteAsset("openai", "codex", 99).isSuccess)
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/releases/assets/99", request.requestUrl?.encodedPath)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    private fun upload(payload: ByteArray) = GithubReleaseAssetUpload.fromFile(
        fileName = "Etoile-arm64.apk",
        label = "",
        contentType = null,
        contentLength = payload.size.toLong(),
        open = { FakeAssetInput(payload) }
    ).getOrThrow()

    private class FakeAssetInput(private val payload: ByteArray) : GithubAssetInput {
        var closed = false
            private set
        private var position = 0

        override fun read(target: ByteArray, offset: Int, count: Int): Int {
            if (position >= payload.size) return -1
            val read = minOf(count, payload.size - position)
            payload.copyInto(target, offset, position, position + read)
            position += read
            return read
        }

        override fun close() {
            closed = true
        }
    }

    private fun draft(targetCommitish: String? = "release/1.x") = GithubReleaseDraft.fromInput(
        tagName = "v1.2.0",
        title = "Etoile 1.2",
        body = "Notes",
        targetCommitish = targetCommitish,
        isDraft = true,
        isPrerelease = false
    ).getOrThrow()

    private fun repository(
        token: String? = "test_token_12345678901234567890",
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubReleasesRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore(token)),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString(),
        cacheStore = cacheStore
    )

    private class FakeTokenStore(private val token: String?) : GithubTokenStore {
        override fun read() = token
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val ASSET_JSON = """
            {
              "id": 99,
              "name": "etoile-arm64.apk",
              "label": "Android arm64",
              "content_type": "application/vnd.android.package-archive",
              "size": 24576,
              "download_count": 8,
              "created_at": "2026-08-16T00:10:00Z",
              "browser_download_url": "https://github.com/openai/codex/releases/download/v1.2.0/etoile-arm64.apk"
            }
        """.trimIndent()

        val RELEASE_JSON = """
            {
              "id": 42,
              "tag_name": "v1.2.0",
              "target_commitish": "main",
              "name": "Etoile 1.2",
              "body": "## Highlights\nFast and polished.",
              "draft": false,
              "prerelease": true,
              "created_at": "2026-08-15T00:00:00Z",
              "published_at": "2026-08-16T00:00:00Z",
              "html_url": "https://github.com/openai/codex/releases/tag/v1.2.0",
              "author": {
                "login": "alice",
                "avatar_url": "https://github.com/alice.png",
                "html_url": "https://github.com/alice"
              },
              "assets": [
                $ASSET_JSON
              ]
            }
        """.trimIndent()
    }
}
