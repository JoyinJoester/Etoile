package takagi.ru.monica.github.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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
import takagi.ru.monica.github.domain.GithubWebAuthRepository
import takagi.ru.monica.github.domain.GithubWebAuthorization
import takagi.ru.monica.github.domain.GithubWebOAuthDeniedException
import takagi.ru.monica.github.domain.GithubWebOAuthInvalidCallbackException
import takagi.ru.monica.github.domain.GithubWebOAuthNotConfiguredException

data class GithubPendingOAuth(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMillis: Long
) {
    override fun toString(): String = "GithubPendingOAuth(<redacted>)"
}

interface GithubPendingOAuthStore {
    fun read(): GithubPendingOAuth?
    fun write(value: GithubPendingOAuth)
    fun clear()
}

class GithubEncryptedPendingOAuthStore(context: Context, namespace: String = "oauth") : GithubPendingOAuthStore {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFERENCES_NAME + "_" + namespace,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): GithubPendingOAuth? {
        val state = preferences.getString(KEY_STATE, null) ?: return null
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: return null
        val createdAt = preferences.getLong(KEY_CREATED_AT, 0L)
        return GithubPendingOAuth(state, verifier, createdAt)
    }

    override fun write(value: GithubPendingOAuth) {
        preferences.edit()
            .putString(KEY_STATE, value.state)
            .putString(KEY_VERIFIER, value.codeVerifier)
            .putLong(KEY_CREATED_AT, value.createdAtEpochMillis)
            .commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "etoile_github_oauth_pending"
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "code_verifier"
        const val KEY_CREATED_AT = "created_at"
    }
}

class GithubWebOAuthRepository(
    private val client: OkHttpClient,
    clientId: String,
    clientSecret: String,
    callbackUri: String,
    private val pendingStore: GithubPendingOAuthStore,
    scopes: Set<String> = DEFAULT_SCOPES,
    private val json: Json = Json { ignoreUnknownKeys = true },
    authorizationUrl: String = "https://github.com/login/oauth/authorize",
    private val accessTokenUrl: String = "https://github.com/login/oauth/access_token",
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val randomBytes: (Int) -> ByteArray = { size -> ByteArray(size).also(SecureRandom()::nextBytes) },
    private val exchangeCode: (suspend (String, String) -> Result<GithubDeviceAccessToken>)? = null
) : GithubWebAuthRepository {
    private val normalizedClientId = clientId.trim()
    private val normalizedClientSecret = clientSecret.trim()
    private val normalizedCallbackUri = callbackUri.trim()
    private val callback = runCatching { URI(normalizedCallbackUri) }.getOrNull()
    private val authorizeEndpoint = authorizationUrl.toHttpUrl()
    private val normalizedScopes = scopes.map(String::trim).filter(String::isNotEmpty).toSortedSet()

    override val isConfigured: Boolean =
        normalizedClientId.length in 10..255 &&
            (normalizedClientSecret.length in 20..255 || exchangeCode != null) &&
            callback?.let(::isValidCallbackUri) == true

    override fun isCallbackUrl(url: String): Boolean {
        val candidate = runCatching { URI(url) }.getOrNull() ?: return false
        val expected = callback ?: return false
        return candidate.scheme.equals(expected.scheme, ignoreCase = true) &&
            candidate.host.equals(expected.host, ignoreCase = true) &&
            candidate.port == expected.port &&
            candidate.path.orEmpty() == expected.path.orEmpty() &&
            candidate.userInfo == null &&
            candidate.fragment == null
    }

    override fun start(): Result<GithubWebAuthorization> = runCatching {
        if (!isConfigured) throw GithubWebOAuthNotConfiguredException()
        val state = randomToken(32)
        val verifier = randomToken(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        pendingStore.write(GithubPendingOAuth(state, verifier, nowEpochMillis()))
        val url = authorizeEndpoint.newBuilder()
            .addQueryParameter("client_id", normalizedClientId)
            .addQueryParameter("redirect_uri", normalizedCallbackUri)
            .addQueryParameter("scope", normalizedScopes.joinToString(" "))
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
        GithubWebAuthorization(url)
    }

    override suspend fun exchangeCallback(url: String): Result<GithubDeviceAccessToken> =
        withContext(Dispatchers.IO) {
            githubRunCatching {
                if (!isConfigured || !isCallbackUrl(url)) throw GithubWebOAuthInvalidCallbackException()
                val callbackUrl = URI(url)
                val parameters = callbackUrl.rawQuery.orEmpty().split('&')
                    .filter(String::isNotBlank)
                    .associate { part ->
                        val pieces = part.split('=', limit = 2)
                        java.net.URLDecoder.decode(pieces[0], Charsets.UTF_8.name()) to
                            java.net.URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
                    }
                val pending = pendingStore.read() ?: throw GithubWebOAuthInvalidCallbackException()
                val age = nowEpochMillis() - pending.createdAtEpochMillis
                val returnedState = parameters["state"] ?: throw GithubWebOAuthInvalidCallbackException()
                if (age !in 0..PENDING_MAX_AGE_MILLIS || !constantTimeEquals(returnedState, pending.state)) {
                    pendingStore.clear()
                    throw GithubWebOAuthInvalidCallbackException()
                }
                if (parameters["error"] == "access_denied") {
                    pendingStore.clear()
                    throw GithubWebOAuthDeniedException()
                }
                val code = parameters["code"]?.takeIf { it.length in 8..255 && it.none(Char::isWhitespace) }
                    ?: throw GithubWebOAuthInvalidCallbackException()
                pendingStore.clear()

                exchangeCode?.let { return@githubRunCatching it(code, pending.codeVerifier).getOrThrow() }

                val body = FormBody.Builder()
                    .add("client_id", normalizedClientId)
                    .add("client_secret", normalizedClientSecret)
                    .add("code", code)
                    .add("redirect_uri", normalizedCallbackUri)
                    .add("code_verifier", pending.codeVerifier)
                    .build()
                val request = Request.Builder()
                    .url(accessTokenUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Etoile-GitHub-Client")
                    .post(body)
                    .build()
                client.newCall(request).readAuthResponse { response ->
                    if (!response.isSuccessful) throw GithubApiException.of(response)
                    json.decodeFromString(TokenResponse.serializer(), response.body?.string().orEmpty()).toDomain(nowEpochMillis())
                }
            }
        }

    override fun cancel() = pendingStore.clear()

    private fun randomToken(size: Int): String = base64Url(randomBytes(size))

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val scope: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token_expires_in") val refreshExpiresIn: Long? = null,
        val error: String? = null
    ) {
        fun toDomain(now: Long): GithubDeviceAccessToken {
            val token = accessToken?.takeIf { it.length in 20..255 && it.none(Char::isWhitespace) }
                ?: throw GithubWebOAuthInvalidCallbackException()
            if (!tokenType.equals("bearer", ignoreCase = true)) throw GithubWebOAuthInvalidCallbackException()
            return GithubDeviceAccessToken(
                accessToken = token,
                tokenType = "bearer",
                refreshToken = validateGithubRefreshToken(refreshToken),
                expiresAtEpochMillis = githubTokenExpiry(now, expiresIn),
                refreshExpiresAtEpochMillis = githubTokenExpiry(now, refreshExpiresIn),
                scopes = scope.orEmpty().split(',', ' ').map(String::trim).filter(String::isNotEmpty).toSet()
            )
        }
    }

    private companion object {
        const val PENDING_MAX_AGE_MILLIS = 10 * 60 * 1000L
        val DEFAULT_SCOPES = setOf("notifications", "read:user", "repo", "user:follow")

        fun isValidCallbackUri(uri: URI): Boolean =
            uri.scheme.equals("etoile", ignoreCase = true) &&
                uri.host.equals("oauth", ignoreCase = true) &&
                uri.userInfo == null && uri.fragment == null

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun constantTimeEquals(first: String, second: String): Boolean = MessageDigest.isEqual(
            first.toByteArray(Charsets.UTF_8),
            second.toByteArray(Charsets.UTF_8)
        )
    }
}
