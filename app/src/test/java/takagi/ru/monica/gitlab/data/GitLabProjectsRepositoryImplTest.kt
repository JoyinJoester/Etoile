package takagi.ru.monica.gitlab.data

import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.gitlab.domain.*

class GitLabProjectsRepositoryImplTest {
    private val sha = "a".repeat(40)
    private val project = """{"id":42,"name":"Etoile","path_with_namespace":"team/Etoile","default_branch":"main","description":null,"star_count":5,"forks_count":2}"""

    @Test fun membershipSearchUsesSubpathInstanceAndPrivateTokenWithNumericPaging() = runTest {
        MockWebServer().use { server ->
            val credential = credential(server)
            server.enqueue(MockResponse().setBody("[$project]").setHeader("X-Next-Page", "2"))
            val result = repository(server).projects(credential.account().key, "猫 & a/b", 1).getOrThrow()
            assertEquals("team/Etoile", result.items.single().pathWithNamespace)
            assertEquals(2, result.nextPage)
            val request = server.takeRequest()
            assertEquals("/gitlab/api/v4/projects", request.requestUrl!!.encodedPath)
            assertEquals("猫 & a/b", request.requestUrl!!.queryParameter("search"))
            assertEquals("true", request.requestUrl!!.queryParameter("membership"))
            assertEquals("30", request.requestUrl!!.queryParameter("per_page"))
            assertEquals("glpat-secret-token", request.getHeader("PRIVATE-TOKEN"))
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun oauthReadsUseBearerAndStripInjectedGithubInterceptors() = runTest {
        MockWebServer().use { server ->
            val credential = credential(server).copy(clientId = "public-client")
            val client = OkHttpClient.Builder().addInterceptor {
                error("GitHub interceptor must not run for GitLab")
            }.build()
            val repository = GitLabProjectsRepositoryImpl(client, GitLabCredentialProvider { credential }, { it.toHttpUrl() })
            server.enqueue(MockResponse().setBody(project))
            assertEquals(42L, repository.project(credential.account().key, 42).getOrThrow().id)
            val request = server.takeRequest()
            assertEquals("Bearer glpat-secret-token", request.getHeader("Authorization"))
            assertNull(request.getHeader("PRIVATE-TOKEN"))
        }
    }

    @Test fun filePathAndRefAreEncodedOnceAndTreeUsesPinnedSha() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(MockResponse().setBody("""{"id":"$sha"}"""))
            val revision = repository.revision(key, 42, "feature/中文").getOrThrow()
            assertEquals(sha, revision.sha)
            assertEquals("/gitlab/api/v4/projects/42/repository/commits/feature%2F%E4%B8%AD%E6%96%87", server.takeRequest().requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody("""[{"name":"a #%.kt","path":"src/a #%.kt","type":"blob"}]"""))
            assertEquals(GitLabEntryType.FILE, repository.tree(key, 42, sha, "src").getOrThrow().items.single().type)
            val tree = server.takeRequest().requestUrl!!
            assertEquals(sha, tree.queryParameter("ref"))
            assertEquals("src", tree.queryParameter("path"))
            server.enqueue(fileResponse("src/a #%.kt", "你好\nval x = 1\n"))
            assertEquals("你好\nval x = 1\n", repository.file(key, 42, sha, "src/a #%.kt").getOrThrow().text)
            val file = server.takeRequest().requestUrl!!
            assertEquals("/gitlab/api/v4/projects/42/repository/files/src%2Fa%20%23%25.kt", file.encodedPath)
            assertEquals(sha, file.queryParameter("ref"))
        }
    }

    @Test fun nextLinkNeverChangesInstanceOrEndpointAndNonSequentialPagesAreIgnored() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(MockResponse().setBody("[]").setHeader("Link", "<https://evil.example/collect?page=2>; rel=\"next\""))
            assertNull(repository.projects(key, "", 1).getOrThrow().nextPage)
            val first = server.takeRequest().requestUrl!!
            server.enqueue(MockResponse().setBody("[]").setHeader("Link", "<${first.newBuilder().setQueryParameter("page", "2").build()}>; rel=\"next\""))
            assertEquals(2, repository.projects(key, "", 1).getOrThrow().nextPage)
            server.enqueue(MockResponse().setBody("[]").setHeader("X-Next-Page", "9999"))
            assertNull(repository.projects(key, "", 1).getOrThrow().nextPage)
            server.enqueue(MockResponse().setBody("[]").setHeader("X-Next-Page", ""))
            assertNull(repository.projects(key, "", 1).getOrThrow().nextPage)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun permissionsMissingResourcesAndRateLimitsHaveSafeErrorsWithoutResponseBodies() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            for ((status, error) in listOf(401 to GitLabReadError.AUTHENTICATION, 403 to GitLabReadError.PERMISSION,
                404 to GitLabReadError.NOT_FOUND, 429 to GitLabReadError.RATE_LIMIT, 500 to GitLabReadError.NETWORK)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("glpat-response-secret"))
                val failure = repository.project(key, 42).exceptionOrNull() as GitLabReadException
                assertEquals(error, failure.reason)
                assertFalse(failure.toString().contains("secret"))
            }
        }
    }

    @Test fun redirectsNeverForwardTokensAndMismatchedAccountsNeverRequest() = runTest {
        MockWebServer().use { server -> MockWebServer().use { target ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.url("/collect")))
            assertTrue(repository.projects(key, "", 1).isFailure)
            assertEquals(0, target.requestCount)
            assertTrue(repository.projects("different-instance#42", "", 1).isFailure)
            assertEquals(1, server.requestCount)
        } }
    }

    @Test fun binaryOversizedMalformedAndWrongPathFilesAreRejected() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(fileResponse("a", "hello\u0000binary"))
            assertEquals(GitLabReadError.UNSUPPORTED_FILE, (repository.file(key, 42, sha, "a").exceptionOrNull() as GitLabReadException).reason)
            server.enqueue(MockResponse().setBody("""{"file_path":"a","encoding":"base64","size":524289,"content":""}"""))
            assertEquals(GitLabReadError.UNSUPPORTED_FILE, (repository.file(key, 42, sha, "a").exceptionOrNull() as GitLabReadException).reason)
            server.enqueue(MockResponse().setBody("""{"file_path":"a","encoding":"base64","size":1,"content":"/w=="}"""))
            assertEquals(GitLabReadError.UNSUPPORTED_FILE, (repository.file(key, 42, sha, "a").exceptionOrNull() as GitLabReadException).reason)
            server.enqueue(fileResponse("another-file", "hello"))
            assertEquals(GitLabReadError.INVALID_RESPONSE, (repository.file(key, 42, sha, "a").exceptionOrNull() as GitLabReadException).reason)
        }
    }

    @Test fun traversalAndMutableRefsForContentNeverReachNetwork() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            assertTrue(repository.tree(key, 42, sha, "../secret").isFailure)
            assertTrue(repository.file(key, 42, "main", "file").isFailure)
            assertTrue(repository.file(key, 42, sha, "/etc/passwd").isFailure)
            assertTrue(repository.project(key, -1).isFailure)
            assertTrue(repository.revision(key, 42, "..").isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun treeCannotInjectSiblingEntriesAndInvalidCommitCannotBecomeRef() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(MockResponse().setBody("""[{"name":"secret","path":"other/secret","type":"blob"}]"""))
            assertTrue(repository.tree(key, 42, sha, "src").isFailure)
            server.enqueue(MockResponse().setBody("""{"id":"main"}"""))
            assertTrue(repository.revision(key, 42, "main").isFailure)
            server.enqueue(MockResponse().setBody(project))
            assertTrue(repository.project(key, 999).isFailure)
        }
    }

    @Test fun reorderedNextLinkQueryIsAcceptedAndOversizedResponsesAreBounded() = runTest {
        MockWebServer().use { server ->
            val repository = repository(server)
            val key = credential(server).account().key
            server.enqueue(MockResponse().setBody("[]"))
            repository.projects(key, "kotlin", 1).getOrThrow()
            val initial = server.takeRequest().requestUrl!!
            val next = initial.newBuilder().query(null).apply {
                initial.queryParameterNames.reversed().forEach { name -> addQueryParameter(name, if (name == "page") "2" else initial.queryParameter(name)) }
            }.build()
            server.enqueue(MockResponse().setBody("[]").setHeader("Link", "<$next>; rel=\"next\""))
            assertEquals(2, repository.projects(key, "kotlin", 1).getOrThrow().nextPage)
            server.enqueue(MockResponse().setChunkedBody(" ".repeat(2_097_153), 4096))
            assertEquals(GitLabReadError.INVALID_RESPONSE, (repository.projects(key, "", 1).exceptionOrNull() as GitLabReadException).reason)
        }
    }

    @Test fun projectReadsRefreshExpiredOauthAndPersistRotatingTokenBeforeReading() = runTest {
        MockWebServer().use { server ->
            val credential = credential(server).copy(clientId = "client", refreshToken = "old-refresh", expiresAt = 1)
            val inactive = credential.copy(id = 43)
            val store = MemoryStore(GitLabStoredAccounts(listOf(credential, inactive), credential.account().key))
            val auth = GitLabAuthRepositoryImpl(OkHttpClient(), store, now = { 1000 }, instanceUrl = { it.toHttpUrl() })
            auth.restore().getOrThrow()
            val repository = GitLabProjectsRepositoryImpl(OkHttpClient(), auth, { it.toHttpUrl() })
            assertTrue(repository.project(inactive.account().key, 42).isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"access_token":"new-access-token","refresh_token":"new-refresh-token","token_type":"bearer","expires_in":7200}"""))
            server.enqueue(MockResponse().setBody(project))
            repository.project(credential.account().key, 42).getOrThrow()
            assertEquals("/gitlab/oauth/token", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("Bearer new-access-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("new-refresh-token", store.value.credentials.first { it.id == 42L }.refreshToken)
            auth.remove(credential.account().key).getOrThrow()
            assertTrue(repository.project(credential.account().key, 42).isFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun cancellingAReadCancelsTheUnderlyingHttpCall() = runTest {
        MockWebServer().use { server ->
            val cancelled = AtomicBoolean(false)
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.set(true) }
            }).build()
            val credential = credential(server)
            val repository = GitLabProjectsRepositoryImpl(client, GitLabCredentialProvider { credential }, { it.toHttpUrl() })
            server.enqueue(MockResponse().setBody(project).setBodyDelay(2, TimeUnit.SECONDS))
            val job = launch(Dispatchers.Default) { repository.project(credential.account().key, 42) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(cancelled.get())
        }
    }

    private fun credential(server: MockWebServer) = GitLabCredential(42, server.url("/gitlab/").toString(), "alice", "Alice", "", "glpat-secret-token")
    private fun repository(server: MockWebServer): GitLabProjectsRepositoryImpl {
        val value = credential(server)
        return GitLabProjectsRepositoryImpl(OkHttpClient(), GitLabCredentialProvider { value }, { it.toHttpUrl() })
    }
    private fun fileResponse(path: String, text: String): MockResponse {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val content = Base64.getEncoder().encodeToString(bytes)
        return MockResponse().setBody("""{"file_path":"$path","encoding":"base64","size":${bytes.size},"content":"$content"}""")
    }
    private class MemoryStore(var value: GitLabStoredAccounts) : GitLabCredentialStore {
        override fun read() = value
        override fun write(accounts: GitLabStoredAccounts) { value = accounts }
        override fun pending(): GitLabPendingAuthorization? = null
        override fun writePending(pending: GitLabPendingAuthorization?) = Unit
    }
}
