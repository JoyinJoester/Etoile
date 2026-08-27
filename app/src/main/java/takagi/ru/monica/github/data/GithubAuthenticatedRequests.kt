package takagi.ru.monica.github.data

class GithubAuthenticatedRequests(
    private val tokenStore: GithubTokenStore
) {
    fun builder(url: String): okhttp3.Request.Builder {
        val token = tokenStore.read() ?: throw GithubSignedOutException()
        return GithubRequestFactory.authenticatedBuilder(url, token)
    }

    fun optionalBuilder(url: String): okhttp3.Request.Builder {
        val token = tokenStore.read()
        return if (token == null) {
            GithubRequestFactory.publicBuilder(url)
        } else {
            GithubRequestFactory.authenticatedBuilder(url, token)
        }
    }

    /** Returns a stable, non-secret scope for account-specific response cache keys. */
    fun cacheScope(): String = tokenStore.read()?.let(::sha256Hex) ?: "public"
}

class GithubSignedOutException : IllegalStateException("GitHub session is not available")
class GithubApiException(val statusCode: Int) : IllegalStateException("GitHub request failed")
