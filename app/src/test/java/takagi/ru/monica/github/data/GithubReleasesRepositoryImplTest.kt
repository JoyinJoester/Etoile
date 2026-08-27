package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
              ]
            }
        """.trimIndent()
    }
}
