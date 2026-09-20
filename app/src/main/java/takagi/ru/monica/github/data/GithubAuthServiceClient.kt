package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.domain.GithubDeviceAccessToken
import takagi.ru.monica.github.domain.GithubDeviceFlowProtocolException

/** Client for Etoile's fixed GitHub App broker; never accepts or sends a client secret. */
class GithubAuthServiceClient(
    client: OkHttpClient,
    baseUrl: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val base = baseUrl.toHttpUrl().also {
        require(it.isHttps || it.host in setOf("localhost", "127.0.0.1"))
        require(it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
        require(it.encodedPath.endsWith('/'))
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchange(code: String, verifier: String): Result<GithubDeviceAccessToken> = call("v1/exchange",
        buildJsonObject { put("code", code); put("code_verifier", verifier) }.toString())

    suspend fun refresh(token: String): Result<GithubDeviceAccessToken> = call("v1/refresh",
        buildJsonObject { put("refresh_token", token) }.toString())

    private suspend fun call(path: String, body: String): Result<GithubDeviceAccessToken> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val request = Request.Builder().url(requireNotNull(base.resolve(path)))
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).readAuthResponse { response ->
                if (!response.isSuccessful) throw GithubApiException.of(response)
                val payload = json.decodeFromString(Payload.serializer(), response.body?.string().orEmpty())
                val access = validateGithubRefreshToken(payload.accessToken)
                    ?: throw GithubDeviceFlowProtocolException("missing_access_token")
                val refresh = validateGithubRefreshToken(payload.refreshToken)
                    ?: throw GithubDeviceFlowProtocolException("missing_refresh_token")
                if (!payload.tokenType.equals("bearer", true)) throw GithubDeviceFlowProtocolException("invalid_token_type")
                val now = nowEpochMillis()
                GithubDeviceAccessToken(access, "bearer", emptySet(), refresh,
                    githubTokenExpiry(now, payload.expiresIn) ?: throw GithubDeviceFlowProtocolException("missing_expiry"),
                    githubTokenExpiry(now, payload.refreshExpiresIn) ?: throw GithubDeviceFlowProtocolException("missing_refresh_expiry"),
                    authorizationSource = "github_app")
            }
        }
    }

    @Serializable private class Payload(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token_expires_in") val refreshExpiresIn: Long? = null
    )
}
