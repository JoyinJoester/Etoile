package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubFileContent

class GithubRepositoryContentsRepositoryImplTest {
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
    fun directoryUsesEncodedPathAndMapsContentKinds() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(DIRECTORY_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val items = repository.directory("openai", "codex", "app/src main", "main").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/contents/app/src%20main?ref=main", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(GithubContentType.DIRECTORY, items.first().type)
        assertEquals(GithubContentType.FILE, items.last().type)
        assertEquals("Main.kt", items.last().name)
    }

    @Test
    fun directoryUsesEtagAndDecodesCachedBodyAfterNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"directory-v1\"")
                .setBody(DIRECTORY_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cacheStore = TestGithubCacheStore()
        val repository = repository(token = null, cacheStore = cacheStore)

        repository.directory("openai", "codex", "", "main").getOrThrow()
        server.takeRequest()
        val cached = repository.directory("openai", "codex", "", "main").getOrThrow()
        val validationRequest = server.takeRequest()

        assertEquals("\"directory-v1\"", validationRequest.getHeader("If-None-Match"))
        assertEquals(listOf("feature", "Main.kt"), cached.map { it.name })
    }

    @Test
    fun fileUsesRawMediaTypeAndClassifiesTextBinaryAndLargePayloads() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fun main() = Unit"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(byteArrayOf(0, 1, 2).toString(Charsets.ISO_8859_1)))
        server.enqueue(MockResponse().setResponseCode(200).setBody("x".repeat(600_000)))
        val repository = repository(token = null)

        val text = repository.file("openai", "codex", "Main.kt", "main").getOrThrow()
        val textRequest = server.takeRequest()
        val binary = repository.file("openai", "codex", "image.bin", "main").getOrThrow()
        server.takeRequest()
        val large = repository.file("openai", "codex", "large.txt", "main").getOrThrow()
        server.takeRequest()

        assertEquals("application/vnd.github.raw+json", textRequest.getHeader("Accept"))
        assertNull(textRequest.getHeader("Authorization"))
        assertEquals(GithubFileContent.Text("fun main() = Unit"), text)
        assertTrue(binary is GithubFileContent.Binary)
        assertTrue(large is GithubFileContent.TooLarge)
    }

    @Test
    fun branchesUseRepositoryEndpointAndMapProtectionStateAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/branches?page=2&per_page=2")}>; rel=\"next\"")
                .setBody(BRANCHES_JSON)
        )
        val repository = repository(token = null)

        val page = repository.branches("openai", "codex", page = 1, perPage = 2).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/branches?per_page=2&page=1", request.path)
        assertEquals(listOf("main", "release"), page.items.map { it.name })
        assertEquals(2, page.nextPage)
        assertTrue(page.items.last().isProtected)
    }

    @Test
    fun tagsUseNativeEndpointAndMapTagCommit() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TAGS_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.tags("openai", "codex", page = 2, perPage = 50).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/tags?per_page=50&page=2", request.path)
        assertEquals("v1.2.0", page.items.single().name)
        assertEquals("tag-sha", page.items.single().sha)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    private fun repository(
        token: String?,
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubRepositoryContentsRepositoryImpl(
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
        val DIRECTORY_JSON = """
            [
              {
                "name": "feature",
                "path": "app/src main/feature",
                "sha": "dir-sha",
                "size": 0,
                "type": "dir",
                "html_url": "https://github.com/openai/codex/tree/main/app/src%20main/feature",
                "download_url": null
              },
              {
                "name": "Main.kt",
                "path": "app/src main/Main.kt",
                "sha": "file-sha",
                "size": 120,
                "type": "file",
                "html_url": "https://github.com/openai/codex/blob/main/app/src%20main/Main.kt",
                "download_url": "https://raw.githubusercontent.com/openai/codex/main/app/src%20main/Main.kt"
              }
            ]
        """.trimIndent()

        val BRANCHES_JSON = """
            [
              { "name": "main", "commit": { "sha": "main-sha" }, "protected": false },
              { "name": "release", "commit": { "sha": "release-sha" }, "protected": true }
            ]
        """.trimIndent()

        val TAGS_JSON = """
            [
              { "name": "v1.2.0", "commit": { "sha": "tag-sha" } }
            ]
        """.trimIndent()
    }
}
