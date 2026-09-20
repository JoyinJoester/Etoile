package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.github.domain.GithubDeviceAccessToken
import takagi.ru.monica.github.domain.GithubDeviceFlowProtocolException

/** Transport only; callers must atomically persist both rotated tokens before using them. */
class GithubTokenRefreshRepository(
    private val client: OkHttpClient,
    private val clientId: String,
    private val clientSecret: String,
    endpoint: String = "https://github.com/login/oauth/access_token",
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val endpoint = endpoint.toHttpUrl().also {
        require(it.isHttps || it.host in setOf("localhost", "127.0.0.1"))
        require(it.username.isEmpty() && it.password.isEmpty())
    }

    suspend fun refresh(refreshToken: String): Result<GithubDeviceAccessToken> = withContext(Dispatchers.IO) {
        githubRunCatching {
            validateGithubRefreshToken(refreshToken)
            require(clientId.isNotBlank() && clientSecret.isNotBlank())
            val request = Request.Builder().url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "Etoile-GitHub-Client")
                .post(FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken).build())
                .build()
            client.newCall(request).readAuthResponse { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val payload = json.decodeFromString(Response.serializer(), response.body?.string().orEmpty())
                if (payload.error != null) throw GithubDeviceFlowProtocolException("refresh_rejected")
                val access = payload.accessToken
                if (access == null || access.length !in 20..255 || access.any { it.isWhitespace() || it.isISOControl() } ||
                    !payload.tokenType.equals("bearer", true)) {
                    throw GithubDeviceFlowProtocolException("invalid_refresh_response")
                }
                val rotated = validateGithubRefreshToken(payload.refreshToken)
                    ?: throw GithubDeviceFlowProtocolException("missing_refresh_token")
                val now = nowEpochMillis()
                GithubDeviceAccessToken(access, "bearer", emptySet(), rotated,
                    githubTokenExpiry(now, payload.expiresIn)
                        ?: throw GithubDeviceFlowProtocolException("missing_token_expiry"),
                    githubTokenExpiry(now, payload.refreshExpiresIn)
                        ?: throw GithubDeviceFlowProtocolException("missing_refresh_expiry"))
            }
        }
    }

    @Serializable
    private class Response(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token_expires_in") val refreshExpiresIn: Long? = null,
        val error: String? = null
    )
}
