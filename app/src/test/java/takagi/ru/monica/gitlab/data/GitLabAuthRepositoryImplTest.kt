package takagi.ru.monica.gitlab.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class GitLabAuthRepositoryImplTest {
    private val token = "glpat-example-token-value"
    private val user = """{"id":42,"username":"alice","name":"Alice","web_url":"https://gitlab.example/alice","state":"active"}"""
    private val oauthToken = """{"access_token":"oauth-access-token","token_type":"Bearer","refresh_token":"oauth-refresh-token","expires_in":7200}"""

    private fun repository(server: MockWebServer, store: MemoryStore, now: () -> Long = { 1000 }) =
        GitLabAuthRepositoryImpl(OkHttpClient(), store, now, instanceUrl = { it.toHttpUrl() })

    @Test fun encryptedStoreInitializationIsDeferredAndRestoreFailureCanBeRetried() = runTest {
        var attempts = 0
        val failure = IllegalStateException("Keystore temporarily unavailable")
        val store = GitLabEncryptedCredentialStore {
            attempts++
            throw failure
        }
        val repository = GitLabAuthRepositoryImpl(OkHttpClient(), store)
        assertEquals(0, attempts)
        assertSame(failure, repository.restore().exceptionOrNull())
        assertEquals(1, attempts)
        assertTrue(repository.accounts.value.accounts.isEmpty())
        assertSame(failure, repository.restore().exceptionOrNull())
        assertEquals(2, attempts)
    }

    @Test fun restoreRemovalAndBrowserPendingStorageRunOffTheCallingThread() = runTest {
        val callingThread = Thread.currentThread()
        val accesses = java.util.Collections.synchronizedList(mutableListOf<String>())
        var pending: GitLabPendingAuthorization? = null
        val store = object : GitLabCredentialStore {
            private fun record(method: String) {
                assertNotSame("Encrypted $method must not run on the calling thread", callingThread, Thread.currentThread())
                accesses += method
            }
            override fun read(): GitLabStoredAccounts { record("read"); return GitLabStoredAccounts() }
            override fun write(accounts: GitLabStoredAccounts) { record("write") }
            override fun pending(): GitLabPendingAuthorization? { record("pending"); return pending }
            override fun writePending(value: GitLabPendingAuthorization?) { record("writePending"); pending = value }
        }
        val repository = GitLabAuthRepositoryImpl(OkHttpClient(), store)
        repository.restore().getOrThrow()
        val authorization = repository.startBrowserSignIn("https://gitlab.com/", "client").getOrThrow().toHttpUrl()
        val denied = "etoile://gitlab-oauth?error=access_denied&state=${authorization.queryParameter("state")}"
        assertTrue(repository.completeBrowserSignIn(denied).isFailure)
        repository.cancelBrowserSignIn()
        repository.remove("missing-account").getOrThrow()
        assertTrue(accesses.containsAll(listOf("read", "write", "pending", "writePending")))
    }

    @Test fun slowEncryptedRestoreLeavesTheCallingDispatcherResponsive() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val ui = executor.asCoroutineDispatcher()
        val store = object : GitLabCredentialStore {
            override fun read(): GitLabStoredAccounts {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return GitLabStoredAccounts()
            }
            override fun write(accounts: GitLabStoredAccounts) = Unit
            override fun pending(): GitLabPendingAuthorization? = null
            override fun writePending(pending: GitLabPendingAuthorization?) = Unit
        }
        try {
            val repository = GitLabAuthRepositoryImpl(OkHttpClient(), store)
            val restoring = async(ui) { repository.restore() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals("responsive", executor.submit<String> { "responsive" }.get(2, TimeUnit.SECONDS))
            release.countDown()
            restoring.await().getOrThrow()
        } finally {
            release.countDown()
            ui.close()
        }
    }

    @Test fun browserPendingCommitCancelAndReplacementAreSerializedInOrder() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        var pending: GitLabPendingAuthorization? = null
        val store = object : GitLabCredentialStore {
            override fun read() = GitLabStoredAccounts()
            override fun write(accounts: GitLabStoredAccounts) = Unit
            override fun pending(): GitLabPendingAuthorization? = pending
            override fun writePending(value: GitLabPendingAuthorization?) {
                if (value?.clientId == "old-client") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                pending = value
                events += value?.clientId ?: "clear"
            }
        }
        val repository = GitLabAuthRepositoryImpl(OkHttpClient(), store)
        try {
            val old = async(Dispatchers.Default) { repository.startBrowserSignIn("https://gitlab.com/", "old-client") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cancel = async(start = CoroutineStart.UNDISPATCHED) { repository.cancelBrowserSignIn() }
            val replacement = async(start = CoroutineStart.UNDISPATCHED) { repository.startBrowserSignIn("https://gitlab.com/", "new-client") }
            release.countDown()
            old.await().getOrThrow(); cancel.await(); replacement.await().getOrThrow()
            assertEquals(listOf("old-client", "clear", "new-client"), events)
            assertEquals("new-client", pending?.clientId)
        } finally { release.countDown() }
    }

    @Test fun productionInstancePolicyRejectsInsecureOrAmbiguousAddresses() {
        for (input in listOf("http://gitlab.com", "https://user:password@gitlab.com", "https://gitlab.com/?token=x", "https://gitlab.com/#x", "https://gitlab.com/api/v4")) {
            assertTrue(input, runCatching { gitLabInstanceUrl(input) }.isFailure)
        }
        assertEquals("https://gitlab.com/", gitLabInstanceUrl("https://gitlab.com").toString())
        assertEquals("https://git.example/team/gitlab/", gitLabInstanceUrl("https://git.example/team/gitlab").toString())
    }

    @Test fun personalTokenIsVerifiedUsingGitLabHeadersBeforeBeingSaved() = runTest {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody(user))
            val store = MemoryStore()
            val repository = repository(server, store)
            val account = repository.signInWithToken(server.url("/").toString(), token).getOrThrow()
            val request = server.takeRequest()
            assertEquals("/api/v4/user", request.path)
            assertEquals(token, request.getHeader("PRIVATE-TOKEN"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-GitHub-Api-Version"))
            assertEquals(42L, account.id)
            assertEquals(account.key, repository.accounts.value.activeKey)
            assertEquals(token, store.read().credentials.single().accessToken)
        }
    }

    @Test fun rejectedTokensAndRedirectsAreNeverSavedOrForwarded() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            val repository = repository(server, store)
            for (code in listOf(401, 403, 302, 307)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", server.url("/collect")))
                assertTrue(repository.signInWithToken(server.url("/").toString(), token).isFailure)
            }
            assertEquals(4, server.requestCount)
            assertTrue(store.read().credentials.isEmpty())
        }
    }

    @Test fun sameUserIdOnDifferentInstancesProducesSeparateAccountsAndRemovalIsLocal() = runTest {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.start(); second.start()
            first.enqueue(MockResponse().setBody(user)); second.enqueue(MockResponse().setBody(user))
            val store = MemoryStore()
            val repository = repository(first, store)
            val one = repository.signInWithToken(first.url("/").toString(), token).getOrThrow()
            val two = repository.signInWithToken(second.url("/").toString(), token).getOrThrow()
            assertNotEquals(one.key, two.key)
            assertEquals(2, repository.accounts.value.accounts.size)
            repository.remove(one.key).getOrThrow()
            assertEquals(listOf(two), repository.accounts.value.accounts)
            assertEquals(1, first.requestCount)
            assertEquals(1, second.requestCount)
            val restored = repository(second, store)
            restored.restore().getOrThrow()
            assertEquals(listOf(two), restored.accounts.value.accounts)
        } }
    }

    @Test fun oauthUsesPkceStateAndPublicClientWithoutASecretAndSurvivesRecreation() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            val repository = repository(server, store)
            val authorization = repository.startBrowserSignIn(server.url("/").toString(), "public-client").getOrThrow().toHttpUrl()
            assertEquals("S256", authorization.queryParameter("code_challenge_method"))
            assertEquals("read_user read_api", authorization.queryParameter("scope"))
            assertEquals("etoile://gitlab-oauth", authorization.queryParameter("redirect_uri"))
            assertNotNull(authorization.queryParameter("code_challenge"))
            assertFalse(authorization.toString().contains(store.pending()!!.verifier))
            val callback = callback(store)
            server.enqueue(MockResponse().setBody(oauthToken)); server.enqueue(MockResponse().setBody(user))
            repository(server, store).completeBrowserSignIn(callback).getOrThrow()
            val exchange = server.takeRequest()
            val body = exchange.body.readUtf8()
            assertEquals("/oauth/token", exchange.path)
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("code_verifier="))
            assertFalse(body.contains("client_secret"))
            assertEquals("Bearer oauth-access-token", server.takeRequest().getHeader("Authorization"))
            assertEquals(7_201_000L, store.read().credentials.single().expiresAt)
            assertNull(store.pending())
            assertTrue(repository.completeBrowserSignIn(callback).isFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun collaborationScopeRequiresExplicitOptInAndDoesNotReplaceReadOnlyDefault() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server, MemoryStore())
            val readOnly = repository.startBrowserSignIn(server.url("/").toString(), "client").getOrThrow().toHttpUrl()
            assertEquals("read_user read_api", readOnly.queryParameter("scope"))
            val writable = repository.startBrowserSignIn(server.url("/").toString(), "client", allowWrites = true).getOrThrow().toHttpUrl()
            assertEquals("read_user read_api api", writable.queryParameter("scope"))
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun oauthCodeExchangeDoesNotReplayOnImmediateServiceRetry() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            val repository = repository(server, store)
            repository.startBrowserSignIn(server.url("/").toString(), "client").getOrThrow()
            val callback = callback(store)
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody(oauthToken))
            assertTrue(repository.completeBrowserSignIn(callback).isFailure)
            assertEquals(1, server.requestCount)
            assertTrue(store.read().credentials.isEmpty())
            assertNull(store.pending())
            assertTrue(repository.completeBrowserSignIn(callback).isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun invalidExpiredDuplicatedAndCancelledCallbacksNeverSendRequests() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            var time = 1000L
            val repository = repository(server, store) { time }
            repository.startBrowserSignIn(server.url("/").toString(), "client").getOrThrow()
            val valid = callback(store)
            for (invalid in listOf(valid.replace("gitlab-oauth", "oauth"), "$valid&state=duplicate", valid.replace("state=", "state=wrong"), valid.replace("?", "/unexpected?"))) {
                assertTrue(repository.completeBrowserSignIn(invalid).isFailure)
            }
            time += 600_001
            assertTrue(repository.completeBrowserSignIn(valid).isFailure)
            time = 1000
            repository.cancelBrowserSignIn()
            assertTrue(repository.completeBrowserSignIn(valid).isFailure)
            assertEquals(0, server.requestCount)
            assertTrue(store.read().credentials.isEmpty())
        }
    }

    @Test fun expiredOauthCredentialRefreshesAndPersistsRotatingTokens() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            val credential = GitLabCredential(42, server.url("/").toString(), "alice", "Alice", "", "old-access-token", "old-refresh-token", 999, "client")
            store.write(GitLabStoredAccounts(listOf(credential), credential.account().key))
            server.enqueue(MockResponse().setBody(oauthToken)); server.enqueue(MockResponse().setBody(user))
            repository(server, store).activate(credential.account().key).getOrThrow()
            val form = server.takeRequest().body.readUtf8()
            assertTrue(form.contains("grant_type=refresh_token"))
            assertTrue(form.contains("refresh_token=old-refresh-token"))
            assertFalse(form.contains("client_secret"))
            assertEquals("Bearer oauth-access-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("oauth-refresh-token", store.read().credentials.single().refreshToken)
        }
    }

    @Test fun credentialsAndPendingAuthorizationRedactSecretsInDiagnostics() {
        val credential = GitLabCredential(42, "https://gitlab.com/", "alice", "", "", token, "refresh-secret")
        assertFalse(credential.toString().contains(token))
        assertFalse(credential.toString().contains("refresh-secret"))
        val pending = GitLabPendingAuthorization("https://gitlab.com/", "client", "secret-state", "secret-verifier", 1000)
        assertFalse(pending.toString().contains("secret-state"))
        assertFalse(pending.toString().contains("secret-verifier"))
    }

    @Test fun cancellingAnInFlightLoginDoesNotSaveItsLateResponse() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(user).setBodyDelay(150, TimeUnit.MILLISECONDS))
            val store = MemoryStore()
            val repository = repository(server, store)
            val signingIn = launch(Dispatchers.Default) {
                repository.signInWithToken(server.url("/").toString(), token)
            }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            signingIn.cancelAndJoin()
            assertTrue(store.read().credentials.isEmpty())
            assertTrue(repository.accounts.value.accounts.isEmpty())
        }
    }

    @Test fun oauthExchangeDoesNotFollowRedirectsOrPersistAnUnverifiedIdentity() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStore()
            val repository = repository(server, store)
            repository.startBrowserSignIn(server.url("/").toString(), "client").getOrThrow()
            val callback = callback(store)
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/collect")))
            assertTrue(repository.completeBrowserSignIn(callback).isFailure)
            assertEquals(1, server.requestCount)
            assertTrue(store.read().credentials.isEmpty())

            repository.startBrowserSignIn(server.url("/").toString(), "client").getOrThrow()
            server.enqueue(MockResponse().setBody(oauthToken))
            server.enqueue(MockResponse().setResponseCode(401))
            assertTrue(repository.completeBrowserSignIn(callback(store)).isFailure)
            assertTrue(store.read().credentials.isEmpty())
            assertEquals(3, server.requestCount)
        }
    }

    private fun callback(store: MemoryStore) = "etoile://gitlab-oauth?code=valid-code&state=" + URLEncoder.encode(store.pending()!!.state, "UTF-8")

    private class MemoryStore : GitLabCredentialStore {
        private var accounts = GitLabStoredAccounts()
        private var authorization: GitLabPendingAuthorization? = null
        override fun read() = accounts
        override fun write(accounts: GitLabStoredAccounts) { this.accounts = accounts }
        override fun pending() = authorization
        override fun writePending(pending: GitLabPendingAuthorization?) { authorization = pending }
    }
}
