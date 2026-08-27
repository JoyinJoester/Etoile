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
import takagi.ru.monica.github.domain.GithubCommitFileStatus

class GithubCommitsRepositoryImplTest {
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
    fun commitsRequestTheSelectedRefAndMapVerification() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/commits?page=2")}>; rel=\"next\"")
                .setBody("[$COMMIT_JSON]")
        )
        val repository = repository()

        val page = repository.commits("openai", "codex", "feature/ui", page = 1, perPage = 30).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/commits", request.requestUrl?.encodedPath)
        assertEquals("feature/ui", request.requestUrl?.queryParameter("sha"))
        assertEquals("1", request.requestUrl?.queryParameter("page"))
        assertEquals("30", request.requestUrl?.queryParameter("per_page"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(2, page.nextPage)
        assertEquals("Ship native commit history", page.items.single().title)
        assertEquals("abcdef1", page.items.single().shortSha)
        assertTrue(page.items.single().isVerified)
        assertEquals("alice", page.items.single().authorLogin)
    }

    @Test
    fun commitDetailMapsStatsRenamesAndPatchWithoutRequiringSignIn() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(COMMIT_JSON))
        val repository = repository(token = null)

        val detail = repository.commit("openai", "codex", SHA).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/commits/$SHA", request.requestUrl?.encodedPath)
        assertEquals(null, request.getHeader("Authorization"))
        assertEquals(12, detail.additions)
        assertEquals(3, detail.deletions)
        assertEquals(15, detail.totalChanges)
        assertEquals(GithubCommitFileStatus.RENAMED, detail.files.single().status)
        assertEquals("old/README.md", detail.files.single().previousFilename)
        assertTrue(detail.files.single().patch?.contains("+New line") == true)
        assertFalse(detail.commit.message.isBlank())
    }

    @Test
    fun commitsReuseCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"commits-v1\"")
                .setBody("[$COMMIT_JSON]")
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val repository = repository(cacheStore = TestGithubCacheStore())

        val first = repository.commits("openai", "codex", "main").getOrThrow()
        val second = repository.commits("openai", "codex", "main").getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"commits-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    private fun repository(
        token: String? = "test_token_12345678901234567890",
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubCommitsRepositoryImpl(
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
        const val SHA = "abcdef1234567890abcdef1234567890abcdef12"
        val COMMIT_JSON = """
            {
              "sha": "$SHA",
              "commit": {
                "author": {
                  "name": "Alice Example",
                  "email": "alice@example.com",
                  "date": "2026-08-16T01:00:00Z"
                },
                "committer": {
                  "name": "GitHub",
                  "email": "noreply@github.com",
                  "date": "2026-08-16T01:05:00Z"
                },
                "message": "Ship native commit history\n\nIncludes file details.",
                "verification": {
                  "verified": true,
                  "reason": "valid"
                }
              },
              "author": {
                "login": "alice",
                "avatar_url": "https://github.com/alice.png",
                "html_url": "https://github.com/alice"
              },
              "committer": null,
              "html_url": "https://github.com/openai/codex/commit/$SHA",
              "stats": {
                "total": 15,
                "additions": 12,
                "deletions": 3
              },
              "files": [
                {
                  "sha": "file-sha",
                  "filename": "README.md",
                  "previous_filename": "old/README.md",
                  "status": "renamed",
                  "additions": 12,
                  "deletions": 3,
                  "changes": 15,
                  "blob_url": "https://github.com/openai/codex/blob/$SHA/README.md",
                  "raw_url": "https://github.com/openai/codex/raw/$SHA/README.md",
                  "patch": "@@ -1 +1,2 @@\n-Old line\n+New line"
                }
              ]
            }
        """.trimIndent()
    }
}
