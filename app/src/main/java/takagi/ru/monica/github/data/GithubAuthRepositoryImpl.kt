package takagi.ru.monica.github.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubAuthRepository
import takagi.ru.monica.github.domain.GithubSession

class GithubAuthRepositoryImpl(
    private val tokenStore: GithubTokenStore,
    private val accountApi: GithubAccountRemoteDataSource,
    private val cacheStore: GithubCacheStore = NoOpGithubCacheStore
) : GithubAuthRepository {
    private val mutationMutex = Mutex()
    private val _session = MutableStateFlow<GithubSession>(GithubSession.Loading)
    override val session: StateFlow<GithubSession> = _session.asStateFlow()
    private val _accounts = MutableStateFlow(storedAccounts())
    override val accounts: StateFlow<List<GithubAccount>> = _accounts.asStateFlow()

    override suspend fun restore(): Result<Unit> = mutationMutex.withLock {
        restoreLocked()
    }

    override suspend fun signInWithToken(token: String): Result<GithubAccount> = mutationMutex.withLock {
        val normalized = token.trim()
        if (!isValidTokenInput(normalized)) {
            return@withLock Result.failure(IllegalArgumentException("Invalid token format"))
        }
        val previousSession = stableSessionBeforeMutation()
        _session.value = GithubSession.Loading
        accountApi.authenticatedUser(normalized).fold(
            onSuccess = { account ->
                cacheStore.clear()
                tokenStore.save(account, normalized)
                refreshAccounts()
                _session.value = GithubSession.SignedIn(account)
                Result.success(account)
            },
            onFailure = { error ->
                _session.value = previousSession
                Result.failure(error)
            }
        )
    }

    override suspend fun switchAccount(accountId: Long): Result<GithubAccount> = mutationMutex.withLock {
        val current = _session.value as? GithubSession.SignedIn
        if (current?.account?.id == accountId) return@withLock Result.success(current.account)
        val credential = tokenStore.storedCredentials().firstOrNull { it.account.id == accountId }
            ?: return@withLock Result.failure(IllegalArgumentException("Unknown GitHub account"))
        val previousSession = stableSessionBeforeMutation()
        accountApi.authenticatedUser(credential.token).fold(
            onSuccess = { verifiedAccount ->
                if (verifiedAccount.id != credential.account.id) {
                    tokenStore.remove(credential.account.id)
                }
                tokenStore.save(verifiedAccount, credential.token)
                cacheStore.clear()
                refreshAccounts()
                _session.value = GithubSession.SignedIn(verifiedAccount)
                Result.success(verifiedAccount)
            },
            onFailure = { error ->
                if (error is GithubAuthenticationException) {
                    tokenStore.remove(credential.account.id)
                    refreshAccounts()
                }
                _session.value = previousSession
                Result.failure(error)
            }
        )
    }

    override suspend fun removeAccount(accountId: Long): Result<Unit> = mutationMutex.withLock {
        removeAccountLocked(accountId)
    }

    override suspend fun signOut() {
        mutationMutex.withLock {
            val accountId = (_session.value as? GithubSession.SignedIn)?.account?.id
                ?: tokenStore.activeAccountId()
            if (accountId == null) {
                tokenStore.clear()
                cacheStore.clear()
                refreshAccounts()
                _session.value = GithubSession.SignedOut
            } else {
                removeAccountLocked(accountId)
            }
        }
    }

    private suspend fun restoreLocked(): Result<Unit> {
        val credentials = tokenStore.storedCredentials()
        val activeCredential = tokenStore.activeAccountId()?.let { activeId ->
            credentials.firstOrNull { it.account.id == activeId }
        } ?: credentials.firstOrNull()
        val token = activeCredential?.token ?: tokenStore.read()
        if (token == null) {
            refreshAccounts()
            _session.value = GithubSession.SignedOut
            return Result.success(Unit)
        }
        _session.value = GithubSession.Loading
        return accountApi.authenticatedUser(token).fold(
            onSuccess = { account ->
                if (activeCredential != null && activeCredential.account.id != account.id) {
                    tokenStore.remove(activeCredential.account.id)
                }
                tokenStore.save(account, token)
                refreshAccounts()
                _session.value = GithubSession.SignedIn(account)
                Result.success(Unit)
            },
            onFailure = { error ->
                if (error is GithubAuthenticationException) {
                    if (activeCredential == null) {
                        tokenStore.clear()
                    } else {
                        tokenStore.remove(activeCredential.account.id)
                    }
                    cacheStore.clear()
                    refreshAccounts()
                    if (tokenStore.read() != null) {
                        restoreLocked()
                    } else {
                        _session.value = GithubSession.SignedOut
                        Result.failure(error)
                    }
                } else {
                    _session.value = GithubSession.Error(recoverable = true)
                    Result.failure(error)
                }
            }
        )
    }

    private suspend fun removeAccountLocked(accountId: Long): Result<Unit> {
        val exists = tokenStore.storedCredentials().any { it.account.id == accountId }
        val removingCurrent = (_session.value as? GithubSession.SignedIn)?.account?.id == accountId ||
            tokenStore.activeAccountId() == accountId
        if (!exists) return Result.failure(IllegalArgumentException("Unknown GitHub account"))
        tokenStore.remove(accountId)
        cacheStore.clear()
        refreshAccounts()
        if (!removingCurrent) return Result.success(Unit)
        return restoreLocked()
    }

    private fun refreshAccounts() {
        _accounts.value = storedAccounts()
    }

    private fun storedAccounts(): List<GithubAccount> =
        tokenStore.storedCredentials().map(GithubStoredCredential::account)

    private fun stableSessionBeforeMutation(): GithubSession = when (val current = _session.value) {
        GithubSession.Loading -> GithubSession.SignedOut
        else -> current
    }

    private fun isValidTokenInput(token: String): Boolean =
        token.length in 20..255 && token.none(Char::isWhitespace)
}
