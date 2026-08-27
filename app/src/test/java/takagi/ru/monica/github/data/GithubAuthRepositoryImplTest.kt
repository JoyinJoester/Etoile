package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession

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
        private val result: (String) -> Result<GithubAccount>
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
