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
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositorySettings
import takagi.ru.monica.github.domain.GithubRepositoryFeatures
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubWebhookEdit
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure
import takagi.ru.monica.github.feature.repository.githubWriteFailure

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
    fun updateSettingsPatchesOnlyTheChangedFieldAndReadsTheSnapshot() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(SETTINGS_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val settings = repository
            .updateSettings("openai", "codex", GithubRepositorySettingsEdit(isPrivate = true))
            .getOrThrow()
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("/repos/openai/codex", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("{\"private\":true}", request.body.readUtf8())
        assertEquals(
            GithubRepositorySettings(
                isPrivate = true,
                isArchived = false,
                features = GithubRepositoryFeatures(hasIssues = true, hasWiki = false, hasProjects = true),
                description = "A coding agent"
            ),
            settings
        )
    }

    @Test
    fun everyFeatureToggleSendsOnlyItsOwnField() = runTest {
        val cases = listOf(
            GithubRepositorySettingsEdit(hasIssues = false) to "{\"has_issues\":false}",
            GithubRepositorySettingsEdit(hasWiki = true) to "{\"has_wiki\":true}",
            GithubRepositorySettingsEdit(hasProjects = false) to "{\"has_projects\":false}"
        )
        cases.forEach { (edit, expectedBody) ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(SETTINGS_JSON))
            val repository = repository(token = "test_token_12345678901234567890")

            repository.updateSettings("openai", "codex", edit).getOrThrow()
            val request = server.takeRequest()

            assertEquals("PATCH", request.method)
            assertEquals(expectedBody, request.body.readUtf8())
        }
    }

    @Test
    fun descriptionIsTheOnlyFieldSentAndIsEncodedAsJsonText() = runTest {
        val cases = listOf(
            // A quote has to arrive escaped, and an empty string means "clear it" rather than "leave it alone".
            "Rust \"core\" agent" to "{\"description\":\"Rust \\\"core\\\" agent\"}",
            "安卓 GitHub 客户端" to "{\"description\":\"安卓 GitHub 客户端\"}",
            "" to "{\"description\":\"\"}"
        )
        cases.forEach { (description, expectedBody) ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(SETTINGS_JSON))
            val repository = repository(token = "test_token_12345678901234567890")

            repository.updateSettings(
                "openai",
                "codex",
                GithubRepositorySettingsEdit(description = description)
            ).getOrThrow()
            val request = server.takeRequest()

            assertEquals("PATCH", request.method)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals(expectedBody, request.body.readUtf8())
        }
    }

    @Test
    fun aClearedDescriptionReadsBackAsNoDescription() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(BLANK_DESCRIPTION_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val settings = repository
            .updateSettings("openai", "codex", GithubRepositorySettingsEdit(description = ""))
            .getOrThrow()

        assertNull(settings.description)
    }

    @Test
    fun defaultBranchIsTheOnlyFieldSentAndReadsBackFromTheServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(DEFAULT_BRANCH_JSON))
        val repository = repository(token = "test_token_12345678901234567890")

        val settings = repository
            .updateSettings("openai", "codex", GithubRepositorySettingsEdit(defaultBranch = "release/1.2"))
            .getOrThrow()
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("{\"default_branch\":\"release/1.2\"}", request.body.readUtf8())
        assertEquals("release/1.2", settings.defaultBranch)
    }

    @Test
    fun aRejectedDefaultBranchKeepsTheStatusForClassifying() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("{\"message\":\"Validation Failed\"}"))
        val repository = repository(token = "test_token_12345678901234567890")

        val result = repository.updateSettings(
            "openai",
            "codex",
            GithubRepositorySettingsEdit(defaultBranch = "does-not-exist")
        )

        val error = result.exceptionOrNull() as GithubApiException
        assertEquals(422, error.statusCode)
    }

    @Test
    fun acceptedSettingsClearTheCacheAndRejectionsDoNot() = runTest {
        val cacheStore = TestGithubCacheStore()
        val repository = repository(
            token = "test_token_12345678901234567890",
            cacheStore = cacheStore
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"details-v1\"")
                .setBody(DETAILS_JSON)
        )
        repository.details("openai", "codex").getOrThrow()
        server.takeRequest()
        assertEquals(false, cacheStore.isEmpty())

        server.enqueue(MockResponse().setResponseCode(422))
        repository.updateSettings("openai", "codex", GithubRepositorySettingsEdit(isArchived = true))
        server.takeRequest()
        assertEquals(false, cacheStore.isEmpty())

        server.enqueue(MockResponse().setResponseCode(200).setBody(SETTINGS_JSON))
        repository.updateSettings("openai", "codex", GithubRepositorySettingsEdit(isArchived = true))
        assertEquals(true, cacheStore.isEmpty())
    }

    @Test
    fun rejectedSettingsKeepTheStatusAndQuotaSignals() = runTest {
        val repository = repository(token = "test_token_12345678901234567890")
        listOf(403 to false, 404 to false, 422 to false, 429 to true).forEach { (code, rateLimited) ->
            server.enqueue(MockResponse().setResponseCode(code))

            val error = repository
                .updateSettings("openai", "codex", GithubRepositorySettingsEdit(isArchived = true))
                .exceptionOrNull() as GithubApiException
            server.takeRequest()

            assertEquals(code, error.statusCode)
            assertEquals(rateLimited, error.rateLimited)
        }
    }

    @Test
    fun settingsNeverLeaveTheDeviceWithoutASession() = runTest {
        val repository = repository(token = null)

        val result = repository.updateSettings(
            "openai",
            "codex",
            GithubRepositorySettingsEdit(isArchived = true)
        )

        assertTrue(result.exceptionOrNull() is GithubSignedOutException)
        assertEquals(0, server.requestCount)
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

    @Test
    fun invitingACollaboratorSendsNothingButThePermission() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        val repository = repository(token = "test_token_12345678901234567890")

        val change = repository
            .setCollaborator("openai", "codex", invite("bob", GithubCollaboratorRole.WRITE))
            .getOrThrow()
        val request = server.takeRequest()

        assertEquals("PUT", request.method)
        assertEquals("/repos/openai/codex/collaborators/bob", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
        assertEquals("{\"permission\":\"push\"}", request.body.readUtf8())
        assertEquals(GithubCollaboratorChange.Invited, change)
    }

    @Test
    fun aCollaboratorAlreadyPresentIsReportedAsAnUpdateNotAnInvitation() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository(token = "test_token_12345678901234567890")

        val change = repository
            .setCollaborator("openai", "codex", invite("bob", GithubCollaboratorRole.READ))
            .getOrThrow()

        assertEquals(GithubCollaboratorChange.Updated, change)
    }

    @Test
    fun removalSendsAnEmptyDeleteAndAcceptsNoContent() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository(token = "test_token_12345678901234567890")

        repository.removeCollaborator("openai", "codex", "bob").getOrThrow()
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/collaborators/bob", request.path)
        assertEquals(0L, request.bodySize)
        assertEquals("Bearer test_token_12345678901234567890", request.getHeader("Authorization"))
    }

    @Test
    fun collaboratorWritesClearTheCacheOnlyWhenGithubAccepts() = runTest {
        val cacheStore = TestGithubCacheStore()
        val repository = repository(
            token = "test_token_12345678901234567890",
            cacheStore = cacheStore
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"details-v1\"")
                .setBody(DETAILS_JSON)
        )
        repository.details("openai", "codex").getOrThrow()
        server.takeRequest()
        assertEquals(false, cacheStore.isEmpty())

        server.enqueue(MockResponse().setResponseCode(403))
        repository.setCollaborator("openai", "codex", invite("bob", GithubCollaboratorRole.ADMIN))
        server.takeRequest()
        assertEquals(false, cacheStore.isEmpty())

        server.enqueue(MockResponse().setResponseCode(204))
        repository.removeCollaborator("openai", "codex", "bob")
        assertEquals(true, cacheStore.isEmpty())
    }

    @Test
    fun rejectedCollaboratorWritesKeepTheStatusAndQuotaSignals() = runTest {
        val repository = repository(token = "test_token_12345678901234567890")
        listOf(401 to false, 403 to false, 404 to false, 422 to false, 429 to true).forEach { (code, rateLimited) ->
            server.enqueue(MockResponse().setResponseCode(code))

            val error = repository
                .setCollaborator("openai", "codex", invite("bob", GithubCollaboratorRole.WRITE))
                .exceptionOrNull() as GithubApiException
            server.takeRequest()

            assertEquals(code, error.statusCode)
            assertEquals(rateLimited, error.rateLimited)
        }
    }

    @Test
    fun collaboratorWritesNeverLeaveTheDeviceWithoutASession() = runTest {
        val repository = repository(token = null)

        val invite = repository.setCollaborator("openai", "codex", invite("bob", GithubCollaboratorRole.WRITE))
        val remove = repository.removeCollaborator("openai", "codex", "bob")

        assertTrue(invite.exceptionOrNull() is GithubSignedOutException)
        assertTrue(remove.exceptionOrNull() is GithubSignedOutException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun updateWebhookSendsOnlyActiveAndMapsTheResponse() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":9,"name":"web","active":true,"events":["push"],"config":{"url":"https://example.test/hook"}}"""
            )
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val hook = repository.updateWebhook("openai", "codex", 9, GithubWebhookEdit(active = true)).getOrThrow()
        val request = server.takeRequest()

        assertEquals("PATCH", request.method)
        assertEquals("/repos/openai/codex/hooks/9", request.path)
        assertEquals("{\"active\":true}", request.body.readUtf8())
        assertEquals("https://example.test/hook", hook.url)
        assertTrue(hook.isActive)
    }

    @Test
    fun deleteWebhookSendsDeleteAndAcceptsNoContent() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository(token = "test_token_12345678901234567890")

        repository.deleteWebhook("openai", "codex", 9).getOrThrow()
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/repos/openai/codex/hooks/9", request.path)
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun webhookWriteFailuresKeepStatusForSharedClassification() = runTest {
        val repository = repository(token = "test_token_12345678901234567890")
        listOf(422 to RepositoryWriteFailure.InvalidInput, 403 to RepositoryWriteFailure.Forbidden).forEach { (code, expected) ->
            server.enqueue(MockResponse().setResponseCode(code))
            val error = repository.updateWebhook("openai", "codex", 9, GithubWebhookEdit(active = false)).exceptionOrNull()
            server.takeRequest()
            assertTrue(error is GithubApiException)
            assertEquals(expected, githubWriteFailure(error!!))
        }
    }

    @Test
    fun webhookDeliveriesMapTheLatestAttempts() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":77,"guid":"g-77","delivered_at":"2026-09-20T10:00:00Z","redelivery":false,"duration":120,"status":"ok","status_code":200,"event":"push","action":null}]"""
            )
        )
        val repository = repository(token = "test_token_12345678901234567890")

        val page = repository.webhookDeliveries("openai", "codex", 9).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/repos/openai/codex/hooks/9/deliveries?per_page=30&page=1", request.path)
        val delivery = page.items.single()
        assertEquals(77L, delivery.id)
        assertEquals("push", delivery.event)
        assertEquals("ok", delivery.status)
        assertEquals(200, delivery.statusCode)
        assertEquals(false, delivery.redelivery)
    }

    @Test
    fun redeliverWebhookPostsTheAttemptsEndpointAndAccepts202() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))
        val repository = repository(token = "test_token_12345678901234567890")

        repository.redeliverWebhook("openai", "codex", 9, 77).getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/repos/openai/codex/hooks/9/deliveries/77/attempts", request.path)
    }

    private fun invite(
        login: String,
        role: GithubCollaboratorRole
    ) = GithubCollaboratorInvite.fromInput(login, role).getOrThrow()

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

        val SETTINGS_JSON = """
            {
              "id": 11,
              "name": "codex",
              "full_name": "openai/codex",
              "html_url": "https://github.com/openai/codex",
              "description": "A coding agent",
              "private": true,
              "archived": false,
              "has_issues": true,
              "has_wiki": false,
              "has_projects": true
            }
        """.trimIndent()

        val BLANK_DESCRIPTION_JSON = """
            {
              "id": 11,
              "name": "codex",
              "full_name": "openai/codex",
              "html_url": "https://github.com/openai/codex",
              "description": "",
              "private": false,
              "archived": false
            }
        """.trimIndent()

        val DEFAULT_BRANCH_JSON = """
            {
              "id": 11,
              "name": "codex",
              "full_name": "openai/codex",
              "html_url": "https://github.com/openai/codex",
              "description": "A coding agent",
              "private": false,
              "archived": false,
              "default_branch": "release/1.2"
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
