package takagi.ru.monica.github.data

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubDiscussionsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class GithubDiscussionsTransportTest {
    @Test fun boundRepositoriesRejectAnonymousAndReplacementAccountsWithoutSelectingTheirTokens() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = AccountStore()
            val base = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(), server.url("/graphql").toString())
            val first = base.forAccount(1)
            val second = base.forAccount(2)
            val anonymous = base.forAccount(null)
            val callerThread = Thread.currentThread()
            server.enqueue(replyResponse())
            assertEquals("C1", first.reply("D1", "First account body").getOrThrow())
            assertEquals("Bearer account-one-test-token", server.takeRequest().getHeader("Authorization"))
            assertNotSame(callerThread, store.tokenThread)
            assertTrue(anonymous.reply("D1", "Anonymous body").exceptionOrNull() is GithubSignedOutException)
            assertTrue(anonymous.comments("D1").exceptionOrNull() is GithubSignedOutException)
            store.select(2)
            assertTrue(first.reply("D1", "Stale account body").exceptionOrNull() is GithubSignedOutException)
            assertTrue(first.comments("D1").exceptionOrNull() is GithubSignedOutException)
            assertEquals(1, server.requestCount)
            server.enqueue(replyResponse())
            assertEquals("C1", second.reply("D1", "Second account body").getOrThrow())
            assertEquals("Bearer account-two-test-token", server.takeRequest().getHeader("Authorization"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun accountIsVerifiedOnTheWorkerWhenTheTokenIsActuallyRead() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = AccountStore()
            val worker = StandardTestDispatcher(testScheduler)
            val repository = GithubDiscussionsRepositoryImpl(GithubAuthenticatedRequests(store), OkHttpClient(),
                server.url("/graphql").toString(), ioDispatcher = worker).forAccount(1)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reply("D1", "Queued old draft") }
            assertEquals(0, store.activeReads)
            store.select(2)
            advanceUntilIdle()
            assertTrue(pending.await().exceptionOrNull() is GithubSignedOutException)
            assertEquals(1, store.activeReads)
            assertEquals(0, store.tokenReads)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun matchingAccountCannotUseATokenThatBelongsToAnotherCredential() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = AccountStore().apply { tokenOverride = "account-two-test-token" }
            val repository = boundRepository(server, store)
            assertTrue(repository.reply("D1", "Private").exceptionOrNull() is GithubSignedOutException)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun mutationDoesNotRetryA503EvenWhenRetryAfterIsZero() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            // A replay would succeed, making both the returned result and the request count fail this test.
            server.enqueue(replyResponse())
            val repository = boundRepository(server)
            val result = repository.reply("D1", "Publish once")
            assertEquals(503, (result.exceptionOrNull() as GithubApiException).statusCode)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun mutationDoesNotRetryAfterTheServerReceivesAndDisconnects() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(replyResponse())
            assertTrue(boundRepository(server).reply("D1", "Publish once").isFailure)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun queriesAndMutationsRejectCrossOriginRedirectsWithoutSendingCredentialsOrBodies() = runTest {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.start(); destination.start()
                val repository = boundRepository(source)
                for (code in listOf(302, 307, 308)) {
                    for (mutation in listOf(true, false)) {
                        source.enqueue(MockResponse().setResponseCode(code).setHeader("Location", destination.url("/collect")))
                        destination.enqueue(replyResponse())
                        val result = if (mutation) repository.reply("D1", "Private mutation body") else repository.comments("D1")
                        assertEquals(code, (result.exceptionOrNull() as GithubApiException).statusCode)
                        assertNotNull(source.takeRequest(5, TimeUnit.SECONDS))
                        assertEquals(0, destination.requestCount)
                    }
                }
                assertEquals(6, source.requestCount)
            }
        }
    }

    @Test fun cancellingReadsAndMutationsCancelsTheirCallsAndPreservesCoroutineCancellation() = runTest {
        for (mutation in listOf(true, false)) {
            MockWebServer().use { server ->
                server.start()
                val cancelled = AtomicBoolean(false)
                val returned = AtomicBoolean(false)
                val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                    override fun canceled(call: Call) { cancelled.set(true) }
                }).build()
                val repository = boundRepository(server, client = client)
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val pending = launch(Dispatchers.Default) {
                    if (mutation) repository.reply("D1", "Cancelled draft") else repository.comments("D1")
                    returned.set(true)
                }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
                pending.cancelAndJoin()
                assertTrue(cancelled.get())
                assertTrue(pending.isCancelled)
                assertFalse(returned.get())
            }
        }
    }

    private fun boundRepository(
        server: MockWebServer,
        store: GithubTokenStore = AccountStore(),
        client: OkHttpClient = OkHttpClient()
    ): GithubDiscussionsRepository = GithubDiscussionsRepositoryImpl(
        GithubAuthenticatedRequests(store), client, server.url("/graphql").toString()
    ).forAccount(1)

    private fun replyResponse() = MockResponse().setBody("""{"data":{"addDiscussionComment":{"comment":{"id":"C1"}}}}""")

    /** The real store uses this monitor for mutations; each snapshot read must retain the same lock. */
    private class AccountStore : GithubTokenStore {
        private var selected: Long = 1
        var tokenOverride: String? = null
        var activeReads = 0
        var tokenReads = 0
        var tokenThread: Thread? = null
        private val credentials = listOf(
            GithubStoredCredential(account(1), "account-one-test-token"),
            GithubStoredCredential(account(2), "account-two-test-token")
        )
        @Synchronized fun select(id: Long) { selected = id }
        override fun activeAccountId(): Long {
            check(Thread.holdsLock(this))
            activeReads++
            return selected
        }
        override fun read(): String {
            check(Thread.holdsLock(this))
            tokenReads++
            tokenThread = Thread.currentThread()
            return tokenOverride ?: credentials.single { it.account.id == selected }.token
        }
        override fun storedCredentials(): List<GithubStoredCredential> {
            check(Thread.holdsLock(this))
            return credentials
        }
        override fun write(token: String): Unit = error("Not used")
        override fun clear(): Unit = error("Not used")
        companion object {
            // Deliberately use identical logins; transport authorization must compare numeric IDs.
            private fun account(id: Long) = GithubAccount(id, "same-login", null, null, "", "https://github.com/same-login", 0, 0, 0)
        }
    }
}
