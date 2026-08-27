package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryWebhook

class GithubRepositoryDetailsRepositoryImplTest {
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
    fun detailsUseOptionalAuthenticationAndMapRepositoryMetadata() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(DETAILS_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val details = repository.details("openai", "codex").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("openai/codex", details.repository.fullName)
        assertEquals("main", details.defaultBranch)
        assertEquals(42, details.forks)
        assertEquals(7, details.watchers)
        assertEquals(13, details.openIssues)
        assertEquals("MIT", details.license)
        assertEquals(listOf("ai", "developer-tools"), details.topics)
    }

    @Test
    fun readmeUsesRawMediaTypeAndTreatsMissingReadmeAsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("# Codex\nNative README"))
        server.enqueue(MockResponse().setResponseCode(404))
        val repository = repository(token = null)

        val readme = repository.readme("openai", "codex", "main").getOrThrow()
        val rawRequest = server.takeRequest()
        val missing = repository.readme("openai", "empty", null).getOrThrow()
        val missingRequest = server.takeRequest()

        assertEquals("# Codex\nNative README", readme)
        assertEquals("/repos/openai/codex/readme?ref=main", rawRequest.path)
        assertEquals("application/vnd.github.raw+json", rawRequest.getHeader("Accept"))
        assertNull(rawRequest.getHeader("Authorization"))
        assertNull(missing)
        assertEquals("/repos/openai/empty/readme", missingRequest.path)
    }

    @Test
    fun detailsUseEtagAndDecodeCachedBodyAfterNotModified() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"details-v1\"")
                .setBody(DETAILS_JSON)
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val cacheStore = TestGithubCacheStore()
        val repository = repository(token = null, cacheStore = cacheStore)

        repository.details("openai", "codex").getOrThrow()
        server.takeRequest()
        val cached = repository.details("openai", "codex").getOrThrow()
        val validationRequest = server.takeRequest()

        assertEquals("\"details-v1\"", validationRequest.getHeader("If-None-Match"))
        assertEquals("openai/codex", cached.repository.fullName)
    }

    @Test
    fun branchProtectionMapsChecksReviewsAndAdminEnforcement() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(BRANCH_PROTECTION_JSON)
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val protection = repository.branchProtection("openai", "codex", "main").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/branches/main/protection", request.path)
        assertEquals(3, protection?.requiredStatusChecks)
        assertEquals(2, protection?.requiredApprovingReviews)
        assertEquals(true, protection?.enforceAdmins)
    }

    @Test
    fun updateTopicsNormalizesAndSendsAuthenticatedPayload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"names\":[\"android\",\"kotlin\"]}"))
        val repository = repository(token = "test_token_12345678901234567890")

        val topics = repository.updateTopics(
            "openai",
            "codex",
            listOf(" Android ", "kotlin", "android", "")
        ).getOrThrow()
        val request = server.takeRequest()

        assertEquals(listOf("android", "kotlin"), topics)
        assertEquals("PUT", request.method)
        assertEquals("/repos/openai/codex/topics", request.path)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("{\"names\":[\"android\",\"kotlin\"]}", request.body.readUtf8())
    }

    @Test
    fun collaboratorsMapRolesAndPagination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Link", "<${server.url("/repos/openai/codex/collaborators?page=2")}>; rel=\"next\"")
                .setBody(COLLABORATORS_JSON)
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.collaborators("openai", "codex", page = 1, perPage = 30).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/collaborators?affiliation=all&per_page=30&page=1", request.path)
        assertEquals(2, page.nextPage)
        assertEquals("alice", page.items.first().user.login)
        assertEquals(GithubCollaboratorRole.ADMIN, page.items.first().role)
        assertEquals(GithubCollaboratorRole.WRITE, page.items.last().role)
    }

    @Test
    fun webhooksMapStatusEventsAndLastResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(WEBHOOKS_JSON)
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.webhooks("openai", "codex").getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/hooks?per_page=30&page=1", request.path)
        assertEquals(11L, page.items.single().id)
        assertEquals(true, page.items.single().isActive)
        assertEquals(listOf("push", "issues"), page.items.single().events)
        assertEquals(200, page.items.single().lastResponseCode)
        assertEquals("OK", page.items.single().lastResponseStatus)
    }

    private fun repository(
        token: String?,
        cacheStore: GithubCacheStore = NoOpGithubCacheStore
    ) = GithubRepositoryDetailsRepositoryImpl(
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
        val DETAILS_JSON = """
            {
              "id": 11,
              "name": "codex",
              "full_name": "openai/codex",
              "description": "A coding agent",
              "language": "Rust",
              "stargazers_count": 1000,
              "updated_at": "2026-08-16T00:00:00Z",
              "private": false,
              "html_url": "https://github.com/openai/codex",
              "owner": { "login": "openai", "avatar_url": "https://avatars.example/openai" },
              "default_branch": "main",
              "forks_count": 42,
              "subscribers_count": 7,
              "open_issues_count": 13,
              "license": { "name": "MIT License", "spdx_id": "MIT" },
              "topics": ["ai", "developer-tools"],
              "archived": false,
              "fork": false
            }
        """.trimIndent()

        val BRANCH_PROTECTION_JSON = """
            {
              "required_status_checks": {
                "contexts": ["build"],
                "checks": [{"context":"lint"},{"context":"tests"}]
              },
              "required_pull_request_reviews": {"required_approving_review_count": 2},
              "enforce_admins": {"enabled": true}
            }
        """.trimIndent()

        val COLLABORATORS_JSON = """
            [
              {
                "login": "alice",
                "avatar_url": "https://avatars.example/alice",
                "html_url": "https://github.com/alice",
                "role_name": "admin",
                "permissions": {"pull": true, "push": true, "admin": true}
              },
              {
                "login": "bob",
                "avatar_url": null,
                "html_url": "https://github.com/bob",
                "role_name": "write",
                "permissions": {"pull": true, "push": true, "admin": false}
              }
            ]
        """.trimIndent()

        val WEBHOOKS_JSON = """
            [
              {
                "id": 11,
                "name": "web",
                "active": true,
                "events": ["push", "issues"],
                "last_response": {"code": 200, "status": "OK", "message": "delivered"}
              }
            ]
        """.trimIndent()
    }
}
