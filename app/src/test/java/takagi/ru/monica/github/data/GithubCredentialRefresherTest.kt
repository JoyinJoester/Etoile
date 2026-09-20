package takagi.ru.monica.github.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubDeviceAccessToken

class GithubCredentialRefresherTest {
    private fun credential() = GithubStoredCredential(
        GithubAccount(1, "user", null, null, "https://example.com/a", "https://github.com/user", 0, 0, 0),
        "old_" + "a".repeat(30), "ghr_" + "b".repeat(30), 1000, 9999999)
    private fun rotated() = GithubDeviceAccessToken("new_" + "c".repeat(30), "bearer", emptySet(),
        "ghr_" + "d".repeat(30), 999999, 9999999)

    @Test fun concurrentRequestsRefreshOnce() = runTest {
        val store = Store(credential())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val refresher = GithubCredentialRefresher(store, {
            calls++
            entered.complete(Unit)
            release.await()
            Result.success(rotated())
        }, { 2000 })
        val first = async { refresher.token(1).getOrThrow() }
        entered.await()
        val second = async { refresher.token(1).getOrThrow() }
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, calls)
        assertEquals(2L, store.activeAccountId())
    }

    @Test fun removedAccountCannotBeResurrectedByRefresh() = runTest {
        val store = Store(credential())
        val refresher = GithubCredentialRefresher(store, {
            store.clear()
            Result.success(rotated())
        }, { 2000 })
        assertTrue(refresher.token(1).isFailure)
        assertTrue(store.storedCredentials().isEmpty())
    }

    @Test fun transientFailurePreservesCredentials() = runTest {
        val original = credential()
        val store = Store(original)
        val refresher = GithubCredentialRefresher(store, { Result.failure(java.io.IOException("offline")) }, { 2000 })
        assertTrue(refresher.token(1).isFailure)
        assertEquals(original, store.value)
    }

    @Test fun apiRequestUsesRotatedTokenAndPublicRequestsStayAnonymous() {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.start()
            val store = Store(credential())
            var refreshCount = 0
            val refresher = GithubCredentialRefresher(store, {
                refreshCount++
                Result.success(rotated())
            }, { 2000 })
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor(GithubCredentialInterceptor(refresher)).build()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("{}"))
            val request = GithubAuthenticatedRequests(store).builder(server.url("/user").toString()).build()
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals("Bearer " + rotated().accessToken, server.takeRequest().getHeader("Authorization"))
            assertEquals(1, refreshCount)
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("{}"))
            client.newCall(GithubRequestFactory.publicBuilder(server.url("/public").toString()).build())
                .execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertEquals(1, refreshCount)
        }
    }

    private class Store(var value: GithubStoredCredential?) : GithubTokenStore {
        override fun read() = value?.token
        override fun write(token: String) = Unit
        override fun clear() { value = null }
        override fun storedCredentials() = listOfNotNull(value)
        override fun activeAccountId() = 2L
        override fun rotate(expected: GithubStoredCredential, token: GithubDeviceAccessToken): Boolean {
            if (value != expected) return false
            value = expected.copy(token = token.accessToken, refreshToken = token.refreshToken,
                expiresAtEpochMillis = token.expiresAtEpochMillis,
                refreshExpiresAtEpochMillis = token.refreshExpiresAtEpochMillis)
            return true
        }
    }
}
