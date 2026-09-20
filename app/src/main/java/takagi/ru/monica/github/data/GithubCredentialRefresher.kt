package takagi.ru.monica.github.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.github.domain.GithubDeviceAccessToken
import java.util.concurrent.ConcurrentHashMap

/** A single instance is shared by all requests; each account has its own refresh lock. */
class GithubCredentialRefresher(
    private val store: GithubTokenStore,
    private val refresh: suspend (String) -> Result<GithubDeviceAccessToken>,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val appRefresh: (suspend (String) -> Result<GithubDeviceAccessToken>)? = null
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun token(accountId: Long): Result<String> = locks.getOrPut(accountId) { Mutex() }.withLock {
        githubRunCatching {
            val credential = store.storedCredentials().firstOrNull { it.account.id == accountId }
                ?: throw GithubAuthenticationException()
            val now = nowEpochMillis()
            val expiry = credential.expiresAtEpochMillis
            if (expiry == null || (expiry > now && expiry - now > 60_000)) return@githubRunCatching credential.token
            val refreshToken = credential.refreshToken ?: throw GithubAuthenticationException()
            if (credential.refreshExpiresAtEpochMillis?.let { it <= now } == true) {
                throw GithubAuthenticationException()
            }
            val refreshChannel = when (credential.authorizationSource) {
                "oauth" -> refresh
                "github_app" -> appRefresh ?: throw GithubAuthenticationException()
                else -> throw GithubAuthenticationException()
            }
            val rotated = refreshChannel(refreshToken).getOrThrow()
            if (rotated.authorizationSource != credential.authorizationSource) throw GithubAuthenticationException()
            if (!store.rotate(credential, rotated)) {
                // The user may have removed or reauthorized the account while the request ran.
                throw GithubAuthenticationException()
            }
            rotated.accessToken
        }
    }
}
