package takagi.ru.monica.github.data

class GithubAuthenticatedRequests(
    private val tokenStore: GithubTokenStore
) {
    fun builder(url: String): okhttp3.Request.Builder {
        val token = tokenStore.read() ?: throw GithubSignedOutException()
        return accountBuilder(url, token)
    }

    fun optionalBuilder(url: String): okhttp3.Request.Builder {
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
    fun cacheScope(): String = tokenStore.read()?.let(::sha256Hex) ?: "public"
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
