package takagi.ru.monica.github.data

class GithubAuthenticatedRequests private constructor(
    private val tokenStore: GithubTokenStore,
    private val accountBinding: AccountBinding?
) {
    constructor(tokenStore: GithubTokenStore) : this(tokenStore, null)

    private data class AccountBinding(val id: Long?)

    /** A bound request must never fall back to whichever account becomes active later. */
    fun forAccount(accountId: Long?): GithubAuthenticatedRequests =
        GithubAuthenticatedRequests(tokenStore, AccountBinding(accountId))

    fun builder(url: String): okhttp3.Request.Builder {
        accountBinding?.let { return builderForAccount(url, it.id) }
        val token = tokenStore.read() ?: throw GithubSignedOutException()
        return accountBuilder(url, token)
    }

    /** Select the active account and its token as one snapshot, without falling back to another account. */
    fun builderForAccount(url: String, expectedAccountId: Long?): okhttp3.Request.Builder = synchronized(tokenStore) {
        GithubRequestFactory.authenticatedBuilder(url, tokenForAccount(expectedAccountId))
            .tag(GithubRequestAccount::class.java, GithubRequestAccount(requireNotNull(expectedAccountId)))
    }

    /** Caller holds the store monitor shared with account selection and credential updates. */
    private fun tokenForAccount(expectedAccountId: Long?): String {
        if (expectedAccountId == null || expectedAccountId <= 0 || tokenStore.activeAccountId() != expectedAccountId) {
            throw GithubSignedOutException()
        }
        val credential = tokenStore.storedCredentials().firstOrNull { it.account.id == expectedAccountId }
            ?: throw GithubSignedOutException()
        return tokenStore.read()?.takeIf { it.isNotBlank() && it == credential.token }
            ?: throw GithubSignedOutException()
    }

    fun optionalBuilder(url: String): okhttp3.Request.Builder {
        accountBinding?.let { return builderForAccount(url, it.id) }
        val token = tokenStore.read()
        return if (token == null) {
            GithubRequestFactory.publicBuilder(url)
        } else {
            accountBuilder(url, token)
        }
    }

    private fun accountBuilder(url: String, token: String): okhttp3.Request.Builder {
        val builder = GithubRequestFactory.authenticatedBuilder(url, token)
        tokenStore.storedCredentials().firstOrNull { it.token == token }?.let {
            builder.tag(GithubRequestAccount::class.java, GithubRequestAccount(it.account.id))
        }
        return builder
    }

    /** Returns a stable, non-secret scope for account-specific response cache keys. */
    fun cacheScope(): String = accountBinding?.let {
        synchronized(tokenStore) { sha256Hex(tokenForAccount(it.id)) }
    } ?: (tokenStore.read()?.let(::sha256Hex) ?: "public")
}

class GithubSignedOutException : IllegalStateException("GitHub session is not available")

/**
 * GitHub answers an exhausted primary or secondary rate limit with `403` as often as with
 * `429`, so the status code alone cannot tell "no permission" from "wait and retry".
 */
class GithubApiException(
    val statusCode: Int,
    val rateLimited: Boolean = false
) : IllegalStateException("GitHub request failed") {
    companion object {
        fun of(response: okhttp3.Response): GithubApiException {
            // Only 403/429 can be a spent quota; the rate-limit headers ride along on every answer.
            val throttled = response.code == 429 ||
                (response.code == 403 && (response.header("Retry-After") != null ||
                    response.header("X-RateLimit-Remaining")?.trim() == "0"))
            return GithubApiException(statusCode = response.code, rateLimited = throttled)
        }
    }
}
