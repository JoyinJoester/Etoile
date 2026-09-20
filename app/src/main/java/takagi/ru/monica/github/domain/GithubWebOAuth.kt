package takagi.ru.monica.github.domain

data class GithubWebAuthorization(
    val authorizationUrl: String
) {
    override fun toString(): String = "GithubWebAuthorization(authorizationUrl=<redacted>)"
}

interface GithubWebAuthRepository {
    val isConfigured: Boolean
    fun isCallbackUrl(url: String): Boolean
    fun start(): Result<GithubWebAuthorization>
    suspend fun exchangeCallback(url: String): Result<GithubDeviceAccessToken>
    fun cancel()
}

object UnavailableGithubWebAuthRepository : GithubWebAuthRepository {
    override val isConfigured: Boolean = false
    override fun isCallbackUrl(url: String): Boolean = false
    override fun start(): Result<GithubWebAuthorization> =
        Result.failure(GithubWebOAuthNotConfiguredException())
    override suspend fun exchangeCallback(url: String): Result<GithubDeviceAccessToken> =
        Result.failure(GithubWebOAuthNotConfiguredException())
    override fun cancel() = Unit
}

class GithubWebOAuthNotConfiguredException : IllegalStateException("GitHub web OAuth is not configured")
class GithubWebOAuthInvalidCallbackException : IllegalStateException("GitHub OAuth callback is invalid")
class GithubWebOAuthDeniedException : IllegalStateException("GitHub OAuth authorization was denied")

