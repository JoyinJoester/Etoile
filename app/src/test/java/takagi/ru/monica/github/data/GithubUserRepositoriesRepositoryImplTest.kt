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
import takagi.ru.monica.github.domain.GithubRepositoryCreateDraft

class GithubUserRepositoriesRepositoryImplTest {
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
    fun repositoriesIncludePrivateAffiliationsAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/user/repos?page=2")}>; rel=\"next\"")
                .setBody(REPOSITORIES_JSON)
        )
        val repository = GithubUserRepositoriesRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )

        val page = repository.repositories(page = 1, perPage = 30).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/user/repos", request.requestUrl?.encodedPath)
        assertEquals("owner,collaborator,organization_member", request.requestUrl?.queryParameter("affiliation"))
        assertEquals("all", request.requestUrl?.queryParameter("visibility"))
        assertEquals("updated", request.requestUrl?.queryParameter("sort"))
        assertEquals("desc", request.requestUrl?.queryParameter("direction"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(2, page.nextPage)
        assertTrue(page.items.single().isPrivate)
    }

    @Test
    fun userRepositoriesUseCachedPageWhenGithubReturnsNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"repos-v1\"")
                .setBody(REPOSITORIES_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cache = TestGithubCacheStore()
        val repository = GithubUserRepositoriesRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )

        val first = repository.repositories().getOrThrow()
        val second = repository.repositories().getOrThrow()

        assertEquals(first.items, second.items)
        server.takeRequest()
        assertEquals("\"repos-v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun creatingARepositoryPostsOnlyTheFieldsTheFormSet() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(CREATED_JSON))
        val repository = userRepositoriesRepository()
        val draft = GithubRepositoryCreateDraft.fromInput(
            name = "  demo-app  ",
            description = "安卓 \"demo\" 客户端",
            isPrivate = true,
            autoInit = false
        ).getOrThrow()

        val created = repository.create(draft).getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/user/repos", request.requestUrl?.encodedPath)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals(
            "{\"name\":\"demo-app\",\"description\":\"安卓 \\\"demo\\\" 客户端\",\"private\":true,\"auto_init\":false}",
            request.body.readUtf8()
        )
        assertEquals("joyins/demo-app", created.fullName)
        assertTrue(created.isPrivate)
    }

    @Test
    fun aBlankDescriptionIsLeftOutOfThePayloadEntirely() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(CREATED_JSON))
        val repository = userRepositoriesRepository()
        val draft = GithubRepositoryCreateDraft.fromInput("demo-app", "   ", false, true).getOrThrow()

        repository.create(draft).getOrThrow()

        assertEquals(
            """{"name":"demo-app","private":false,"auto_init":true}""",
            server.takeRequest().body.readUtf8()
        )
    }

    @Test
    fun aRefusedNameKeepsItsStatusCodeSoTheScreenCanNameTheReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"name already exists"}"""))
        val repository = userRepositoriesRepository()
        val draft = GithubRepositoryCreateDraft.fromInput("demo-app", null, false, false).getOrThrow()

        val error = repository.create(draft).exceptionOrNull() as GithubApiException

        assertEquals(422, error.statusCode)
    }

    @Test
    fun creatingARepositoryDropsCachedPagesSoListsReload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(REPOSITORIES_JSON))
        server.enqueue(MockResponse().setResponseCode(201).setBody(CREATED_JSON))
        val cache = TestGithubCacheStore()
        val repository = GithubUserRepositoriesRepositoryImpl(
            requests = GithubAuthenticatedRequests(FakeTokenStore()),
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            cacheStore = cache
        )
        repository.repositories().getOrThrow()
        assertFalse(cache.isEmpty())

        repository.create(GithubRepositoryCreateDraft.fromInput("demo-app", null, false, false).getOrThrow())
            .getOrThrow()

        assertTrue(cache.isEmpty())
    }

    private fun userRepositoriesRepository() = GithubUserRepositoriesRepositoryImpl(
        requests = GithubAuthenticatedRequests(FakeTokenStore()),
        client = OkHttpClient(),
        baseUrl = server.url("/").toString()
    )

    private class FakeTokenStore : GithubTokenStore {
        override fun read() = "test_token_12345678901234567890"
        override fun write(token: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        val REPOSITORIES_JSON = """
            [
              {
                "id": 20,
                "name": "private-app",
                "full_name": "joyins/private-app",
                "description": "Private project",
                "language": "Kotlin",
                "stargazers_count": 3,
                "updated_at": "2026-08-16T00:00:00Z",
                "private": true,
                "html_url": "https://github.com/joyins/private-app"
              }
            ]
        """.trimIndent()

        val CREATED_JSON = """
            {
              "id": 21,
              "name": "demo-app",
              "full_name": "joyins/demo-app",
              "description": null,
              "private": true,
              "html_url": "https://github.com/joyins/demo-app"
            }
        """.trimIndent()
    }
}
