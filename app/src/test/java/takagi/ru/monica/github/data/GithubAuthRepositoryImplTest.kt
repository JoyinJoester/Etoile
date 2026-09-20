package takagi.ru.monica.github.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession

@OptIn(ExperimentalCoroutinesApi::class)
class GithubAuthRepositoryImplTest {

    @Test
    fun validTokenIsPersistedOnlyAfterGithubVerification() = runTest {
        val account = account()
        val store = FakeTokenStore()
        val remote = FakeAccountRemote { Result.success(account) }
        val repository = GithubAuthRepositoryImpl(store, remote)

        val result = repository.signInWithToken(TOKEN_ONE)

        assertTrue(result.isSuccess)
        assertEquals(TOKEN_ONE, store.read())
        assertEquals(listOf(account), repository.accounts.value)
        assertEquals(GithubSession.SignedIn(account), repository.session.value)
    }

    @Test
    fun signingInSecondAccountKeepsTheFirstCredentialAndActivatesTheSecond() = runTest {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val store = FakeTokenStore()
        val remote = FakeAccountRemote { token ->
            Result.success(if (token == TOKEN_ONE) first else second)
        }
        val repository = GithubAuthRepositoryImpl(store, remote)

        repository.signInWithToken(TOKEN_ONE)
        repository.signInWithToken(TOKEN_TWO)

        assertEquals(2L, store.activeAccountId())
        assertEquals(TOKEN_TWO, store.read())
        assertEquals(listOf(first, second), repository.accounts.value)
        assertEquals(GithubSession.SignedIn(second), repository.session.value)
    }

    @Test
    fun failedAdditionalAccountValidationKeepsTheCurrentSessionAndCacheUsable() = runTest {
        val first = account()
        val pendingValidation = CompletableDeferred<Result<GithubAccount>>()
        val store = FakeTokenStore()
        val cache = TestGithubCacheStore()
        val repository = GithubAuthRepositoryImpl(
            store,
            FakeAccountRemote { token ->
                if (token == TOKEN_ONE) Result.success(first) else pendingValidation.await()
            },
            cache
        )
        repository.signInWithToken(TOKEN_ONE)
        val cachedAccount = GithubCachedResponse("cached", null, null, 1L)
        cache.write("account", cachedAccount)

        val attempt = async { repository.signInWithToken(TOKEN_TWO) }
        runCurrent()

        assertEquals(GithubSession.SignedIn(first), repository.session.value)
        assertEquals(TOKEN_ONE, store.read())
        pendingValidation.complete(Result.failure(GithubAuthenticationException()))

        assertTrue(attempt.await().isFailure)
        assertEquals(GithubSession.SignedIn(first), repository.session.value)
        assertEquals(listOf(first), repository.accounts.value)
        assertEquals(TOKEN_ONE, store.read())
        assertEquals(cachedAccount, cache.read("account"))
    }

    @Test
    fun cancelledValidationNeverPersistsALateSuccessfulResponse() = runTest {
        val first = account()
        val second = account(id = 2, login = "octocat")
        val pendingValidation = CompletableDeferred<Result<GithubAccount>>()
        val store = FakeTokenStore()
        val cache = TestGithubCacheStore()
        val repository = GithubAuthRepositoryImpl(
            store,
            FakeAccountRemote { token ->
                if (token == TOKEN_ONE) Result.success(first)
                else withContext(NonCancellable) { pendingValidation.await() }
            },
            cache
        )
        repository.signInWithToken(TOKEN_ONE)
        val cachedAccount = GithubCachedResponse("cached", null, null, 1L)
        cache.write("account", cachedAccount)
        var returnedSuccess = false

        val attempt = launch {
            returnedSuccess = repository.signInWithToken(TOKEN_TWO).isSuccess
        }
        runCurrent()
        attempt.cancel()
        pendingValidation.complete(Result.success(second))
        attempt.join()

        assertTrue(attempt.isCancelled)
        assertFalse(returnedSuccess)
        assertEquals(GithubSession.SignedIn(first), repository.session.value)
        assertEquals(listOf(first), repository.accounts.value)
        assertEquals(TOKEN_ONE, store.read())
        assertEquals(cachedAccount, cache.read("account"))

        // The cancelled mutation must release its lock for an intentional retry.
        assertTrue(repository.signInWithToken(TOKEN_TWO).isSuccess)
        assertEquals(GithubSession.SignedIn(second), repository.session.value)
    }

    @Test
    fun switchAccountVerifiesStoredCredentialBeforeActivation() = runTest {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val store = FakeTokenStore(
            credentials = listOf(
                GithubStoredCredential(first, TOKEN_ONE),
                GithubStoredCredential(second, TOKEN_TWO)
            ),
            activeAccountId = 1L
        )
        val remote = FakeAccountRemote { token ->
            Result.success(if (token == TOKEN_ONE) first else second)
        }
        val cache = TestGithubCacheStore().apply {
            write("account", GithubCachedResponse("cached", null, null, 1L))
        }
        val repository = GithubAuthRepositoryImpl(store, remote, cache)
        repository.restore()

        val result = repository.switchAccount(2L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(TOKEN_ONE, TOKEN_TWO), remote.tokens)
        assertEquals(2L, store.activeAccountId())
        assertEquals(GithubSession.SignedIn(second), repository.session.value)
        assertNull(cache.read("account"))
    }

    @Test
    fun failedSwitchLeavesTheCurrentAccountAndTokenActive() = runTest {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val store = FakeTokenStore(
            credentials = listOf(
                GithubStoredCredential(first, TOKEN_ONE),
                GithubStoredCredential(second, TOKEN_TWO)
            ),
            activeAccountId = 1L
        )
        val remote = FakeAccountRemote { token ->
            if (token == TOKEN_ONE) Result.success(first)
            else Result.failure(IllegalStateException("offline"))
        }
        val repository = GithubAuthRepositoryImpl(store, remote)
        repository.restore()

        val result = repository.switchAccount(2L)

        assertTrue(result.isFailure)
        assertEquals(1L, store.activeAccountId())
        assertEquals(TOKEN_ONE, store.read())
        assertEquals(GithubSession.SignedIn(first), repository.session.value)
    }

    @Test
    fun signingOutCurrentAccountRestoresAnotherSavedAccount() = runTest {
        val first = account(id = 1, login = "joyins")
        val second = account(id = 2, login = "octocat")
        val store = FakeTokenStore(
            credentials = listOf(
                GithubStoredCredential(first, TOKEN_ONE),
                GithubStoredCredential(second, TOKEN_TWO)
            ),
            activeAccountId = 2L
        )
        val remote = FakeAccountRemote { token ->
            Result.success(if (token == TOKEN_ONE) first else second)
        }
        val repository = GithubAuthRepositoryImpl(store, remote)
        repository.restore()

        repository.signOut()

        assertEquals(1L, store.activeAccountId())
        assertEquals(listOf(first), repository.accounts.value)
        assertEquals(GithubSession.SignedIn(first), repository.session.value)
    }

    @Test
    fun legacySingleTokenIsMigratedAfterSuccessfulRestore() = runTest {
        val account = account()
        val store = FakeTokenStore(legacyToken = TOKEN_ONE)
        val repository = GithubAuthRepositoryImpl(
            store,
            FakeAccountRemote { Result.success(account) }
        )

        val result = repository.restore()

        assertTrue(result.isSuccess)
        assertNull(store.legacyToken)
        assertEquals(listOf(GithubStoredCredential(account, TOKEN_ONE)), store.storedCredentials())
        assertEquals(1L, store.activeAccountId())
    }

    @Test
    fun invalidActiveCredentialIsRemovedWithoutDiscardingOtherAccounts() = runTest {
        val valid = account(id = 1, login = "joyins")
        val invalid = account(id = 2, login = "expired")
        val store = FakeTokenStore(
            credentials = listOf(
                GithubStoredCredential(valid, TOKEN_ONE),
                GithubStoredCredential(invalid, TOKEN_TWO)
            ),
            activeAccountId = 2L
        )
        val remote = FakeAccountRemote { token ->
            if (token == TOKEN_TWO) Result.failure(GithubAuthenticationException())
            else Result.success(valid)
        }
        val repository = GithubAuthRepositoryImpl(store, remote)

        val result = repository.restore()

        assertTrue(result.isSuccess)
        assertEquals(listOf(valid), repository.accounts.value)
        assertEquals(1L, store.activeAccountId())
        assertEquals(GithubSession.SignedIn(valid), repository.session.value)
    }

    @Test
    fun malformedTokenNeverReachesNetworkOrStorage() = runTest {
        val store = FakeTokenStore()
        val remote = FakeAccountRemote { Result.success(account()) }
        val repository = GithubAuthRepositoryImpl(store, remote)

        val result = repository.signInWithToken("short token")

        assertTrue(result.isFailure)
        assertEquals(0, remote.tokens.size)
        assertNull(store.read())
    }

    @Test
    fun rejectedStoredTokenIsRemovedDuringRestore() = runTest {
        val store = FakeTokenStore(legacyToken = TOKEN_ONE)
        val remote = FakeAccountRemote { Result.failure(GithubAuthenticationException()) }
        val cache = TestGithubCacheStore()
        cache.write("account", GithubCachedResponse("cached", null, null, 1L))
        val repository = GithubAuthRepositoryImpl(store, remote, cache)

        val result = repository.restore()

        assertTrue(result.isFailure)
        assertTrue(store.cleared)
        assertNull(cache.read("account"))
        assertSame(GithubSession.SignedOut, repository.session.value)
    }

    @Test
    fun transientRestoreFailureKeepsEncryptedTokenForRetry() = runTest {
        val store = FakeTokenStore(legacyToken = TOKEN_ONE)
        val remote = FakeAccountRemote { Result.failure(IllegalStateException("offline")) }
        val repository = GithubAuthRepositoryImpl(store, remote)

        repository.restore()

        assertFalse(store.cleared)
        assertEquals(TOKEN_ONE, store.read())
        assertEquals(GithubSession.Error(recoverable = true), repository.session.value)
    }

    @Test
    fun oauthLoginPersistsRefreshCredentialsOnlyAfterAccountVerification() = runTest {
        val store = FakeTokenStore()
        val remote = FakeAccountRemote { Result.success(account()) }
        val repository = GithubAuthRepositoryImpl(store, remote)
        val oauth = takagi.ru.monica.github.domain.GithubDeviceAccessToken(
            TOKEN_ONE, "bearer", emptySet(), "ghr_" + "a".repeat(40), 100_000L, 200_000L
        )
        assertTrue(repository.signInWithOAuth(oauth).isSuccess)
        val saved = store.storedCredentials().single()
        assertEquals(oauth.refreshToken, saved.refreshToken)
        assertEquals(oauth.expiresAtEpochMillis, saved.expiresAtEpochMillis)
        assertEquals(oauth.refreshExpiresAtEpochMillis, saved.refreshExpiresAtEpochMillis)
        assertFalse(saved.toString().contains(oauth.refreshToken!!))
    }

    @Test
    fun rejectedOAuthLoginDoesNotPersistRefreshCredentials() = runTest {
        val store = FakeTokenStore()
        val repository = GithubAuthRepositoryImpl(store,
            FakeAccountRemote { Result.failure(GithubAuthenticationException()) })
        val oauth = takagi.ru.monica.github.domain.GithubDeviceAccessToken(
            TOKEN_ONE, "bearer", emptySet(), "ghr_" + "a".repeat(40)
        )
        assertTrue(repository.signInWithOAuth(oauth).isFailure)
        assertTrue(store.storedCredentials().isEmpty())
    }

    @Test
    fun restoreRefreshesBeforeFetchingAccount() = runTest {
        val credential = GithubStoredCredential(account(), TOKEN_ONE, "ghr_" + "a".repeat(40), 1000, 999999)
        val store = FakeTokenStore(credentials = listOf(credential), activeAccountId = 1)
        val refresher = GithubCredentialRefresher(store, {
            Result.success(takagi.ru.monica.github.domain.GithubDeviceAccessToken(
                TOKEN_TWO, "bearer", emptySet(), "ghr_" + "b".repeat(40), 1000000, 9999999))
        }, { 2000 })
        val remote = FakeAccountRemote { Result.success(account()) }
        val repository = GithubAuthRepositoryImpl(store, remote, credentialRefresher = refresher)
        assertTrue(repository.restore().isSuccess)
        assertEquals(listOf(TOKEN_TWO), remote.tokens)
    }

    private class FakeTokenStore(
        legacyToken: String? = null,
        credentials: List<GithubStoredCredential> = emptyList(),
        activeAccountId: Long? = null
    ) : GithubTokenStore {
        var legacyToken: String? = legacyToken
        private val credentialsById = LinkedHashMap<Long, GithubStoredCredential>().apply {
            credentials.forEach { put(it.account.id, it) }
        }
        private var activeId: Long? = activeAccountId
        var cleared = false

        override fun read(): String? = activeId?.let(credentialsById::get)?.token ?: legacyToken

        override fun write(token: String) {
            legacyToken = token
        }

        override fun clear() {
            legacyToken = null
            credentialsById.clear()
            activeId = null
            cleared = true
        }

        override fun storedCredentials(): List<GithubStoredCredential> = credentialsById.values.toList()

        override fun save(account: GithubAccount, token: String) {
            credentialsById[account.id] = GithubStoredCredential(account, token)
            activeId = account.id
            legacyToken = null
        }

        override fun saveOAuth(account: GithubAccount, token: takagi.ru.monica.github.domain.GithubDeviceAccessToken) {
            save(account, token.accessToken)
            credentialsById[account.id] = GithubStoredCredential(account, token.accessToken,
                token.refreshToken, token.expiresAtEpochMillis, token.refreshExpiresAtEpochMillis)
        }

        override fun rotate(expected: GithubStoredCredential, token: takagi.ru.monica.github.domain.GithubDeviceAccessToken): Boolean {
            if (credentialsById[expected.account.id] != expected) return false
            credentialsById[expected.account.id] = expected.copy(token = token.accessToken,
                refreshToken = token.refreshToken, expiresAtEpochMillis = token.expiresAtEpochMillis,
                refreshExpiresAtEpochMillis = token.refreshExpiresAtEpochMillis)
            return true
        }

        override fun activate(accountId: Long): Boolean {
            if (accountId !in credentialsById) return false
            activeId = accountId
            return true
        }

        override fun remove(accountId: Long): Boolean {
            val removed = credentialsById.remove(accountId) != null
            if (activeId == accountId) activeId = credentialsById.keys.firstOrNull()
            return removed
        }

        override fun activeAccountId(): Long? = activeId
    }

    private class FakeAccountRemote(
        private val result: suspend (String) -> Result<GithubAccount>
    ) : GithubAccountRemoteDataSource {
        val tokens = mutableListOf<String>()

        override suspend fun authenticatedUser(token: String): Result<GithubAccount> {
            tokens += token
            return result(token)
        }
    }

    private fun account(id: Long = 1, login: String = "joyins") = GithubAccount(
        id = id,
        login = login,
        name = login.replaceFirstChar(Char::uppercase),
        bio = "Building Etoile",
        avatarUrl = "https://avatars.githubusercontent.com/u/$id",
        htmlUrl = "https://github.com/$login",
        publicRepositories = 24,
        followers = 26,
        following = 18
    )

    private companion object {
        const val TOKEN_ONE = "github_pat_11111111111111111111"
        const val TOKEN_TWO = "github_pat_22222222222222222222"
    }
}
