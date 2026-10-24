package takagi.ru.monica.github.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import takagi.ru.monica.github.domain.*
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class GithubResourceAccountBindingTest(private val operation: Operation) {
    enum class Operation { RUN, ENABLE, DISPATCH, CREATE_RELEASE, EDIT_RELEASE, DELETE_RELEASE, UPLOAD, DELETE_ASSET }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun operations() = Operation.entries.map { arrayOf(it) }
    }

    @Test fun independentBoundRepositoriesNeverFallBackToAnotherAccountsToken() = runTest {
        MockWebServer().use { server ->
            server.start()
            val store = AccountStore()
            val requests = GithubAuthenticatedRequests(store)
            val first = boundCall(server, requests, 1)
            val second = boundCall(server, requests, 2)
            server.enqueue(MockResponse().setResponseCode(500))
            assertStatus500(first())
            assertEquals("Bearer synthetic-account-1", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            store.select(2)
            assertTrue(first().exceptionOrNull() is GithubSignedOutException)
            server.enqueue(MockResponse().setResponseCode(500))
            assertStatus500(second())
            assertEquals("Bearer synthetic-account-2", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun queuedIoChecksAccountBeforeReadingAnyReplacementToken() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(500))
            val store = AccountStore()
            val call = boundCall(server, GithubAuthenticatedRequests(store), 1, StandardTestDispatcher(testScheduler))
            val pending = async(start = CoroutineStart.UNDISPATCHED) { call() }
            assertEquals(0, store.activeReads)
            assertEquals(0, store.tokenReads)
            store.select(2)
            advanceUntilIdle()
            assertTrue(pending.await().exceptionOrNull() is GithubSignedOutException)
            assertEquals(1, store.activeReads)
            assertEquals(0, store.tokenReads)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun invalidAnonymousMissingAndMismatchedCredentialsAreRejectedWithoutNetwork() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(500))
            val store = AccountStore()
            val requests = GithubAuthenticatedRequests(store)
            for (id in listOf(null, 0L, -1L, 3L)) {
                assertTrue(boundCall(server, requests, id)().exceptionOrNull() is GithubSignedOutException)
            }
            store.tokenOverride = "synthetic-account-2"
            assertTrue(boundCall(server, requests, 1)().exceptionOrNull() is GithubSignedOutException)
            store.tokenOverride = ""
            assertTrue(boundCall(server, requests, 1)().exceptionOrNull() is GithubSignedOutException)
            store.tokenOverride = null
            store.credentials = store.credentials.filterNot { it.account.id == 1L }
            assertTrue(boundCall(server, requests, 1)().exceptionOrNull() is GithubSignedOutException)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun boundAccountUsesItsRotatedCredentialWhenTheWorkerActuallyBuildsTheRequest() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(500))
            val store = AccountStore()
            val call = boundCall(server, GithubAuthenticatedRequests(store), 1, StandardTestDispatcher(testScheduler))
            val pending = async(start = CoroutineStart.UNDISPATCHED) { call() }
            store.credentials = store.credentials.map { if (it.account.id == 1L) it.copy(token = "synthetic-rotated-1") else it }
            advanceUntilIdle()
            assertStatus500(pending.await())
            assertEquals("Bearer synthetic-rotated-1", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            assertEquals(1, store.tokenReads)
            assertEquals(1, server.requestCount)
        }
    }

    private fun boundCall(server: MockWebServer, requests: GithubAuthenticatedRequests, id: Long?,
        dispatcher: CoroutineDispatcher = Dispatchers.IO): suspend () -> Result<*> {
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val actions = GithubActionsRepositoryImpl(requests, client, baseUrl = base, ioDispatcher = dispatcher).forAccount(id)
        val releases = GithubReleasesRepositoryImpl(requests, client, baseUrl = base, ioDispatcher = dispatcher).forAccount(id)
        val draft = GithubReleaseDraft.fromInput("v1", "Title", "Private", "main", true, false).getOrThrow()
        return {
            when (operation) {
                Operation.RUN -> actions.performRunAction("o", "r", 7, GithubWorkflowRunAction.RERUN)
                Operation.ENABLE -> actions.setWorkflowEnabled("o", "r", 7, false)
                Operation.DISPATCH -> actions.dispatchWorkflow("o", "r", 7, "main", emptyMap())
                Operation.CREATE_RELEASE -> releases.createRelease("o", "r", draft)
                Operation.EDIT_RELEASE -> releases.updateRelease("o", "r", 7, draft)
                Operation.DELETE_RELEASE -> releases.deleteRelease("o", "r", 7)
                Operation.UPLOAD -> releases.uploadAsset("o", "r", 7,
                    GithubReleaseAssetUpload.fromFile("private.bin", "", "application/octet-stream", 1) {
                        val input = ByteArrayInputStream(byteArrayOf(1))
                        object : GithubAssetInput {
                            override fun read(target: ByteArray, offset: Int, count: Int) = input.read(target, offset, count)
                            override fun close() = input.close()
                        }
                    }.getOrThrow())
                Operation.DELETE_ASSET -> releases.deleteAsset("o", "r", 7)
            }
        }
    }

    private fun assertStatus500(result: Result<*>) = assertEquals(500, (result.exceptionOrNull() as GithubApiException).statusCode)

    private class AccountStore : GithubTokenStore {
        private var selected: Long = 1
        var activeReads = 0
        var tokenReads = 0
        var tokenOverride: String? = null
        var credentials = (1L..2L).map { id ->
            GithubStoredCredential(GithubAccount(id, "same-login", null, null, "", "", 0, 0, 0), "synthetic-account-$id")
        }
        @Synchronized fun select(id: Long) { selected = id }
        override fun activeAccountId(): Long { check(Thread.holdsLock(this)); activeReads++; return selected }
        override fun storedCredentials(): List<GithubStoredCredential> { check(Thread.holdsLock(this)); return credentials }
        override fun read(): String { check(Thread.holdsLock(this)); tokenReads++; return tokenOverride ?: credentials.single { it.account.id == selected }.token }
        override fun write(token: String): Unit = error("unused")
        override fun clear(): Unit = error("unused")
    }
}
