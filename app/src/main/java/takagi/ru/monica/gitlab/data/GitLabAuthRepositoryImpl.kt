package takagi.ru.monica.gitlab.data

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.github.data.githubRunCatching
import takagi.ru.monica.gitlab.domain.*

fun gitLabInstanceUrl(value: String): HttpUrl {
    val url = value.trim().toHttpUrl()
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
    require(url.encodedPathSegments.none { it.equals("api", true) || it.equals("oauth", true) })
    return if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
}

class GitLabAuthRepositoryImpl(
    client: OkHttpClient,
    private val store: GitLabCredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val instanceUrl: (String) -> HttpUrl = ::gitLabInstanceUrl
) : GitLabAuthRepository, GitLabCredentialProvider {
    // Neither a reverse proxy nor a compromised instance can redirect credentials elsewhere.
    private val client = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.authenticator(okhttp3.Authenticator.NONE).cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .cache(null).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val pendingMutex = Mutex()
    private val mutableAccounts = MutableStateFlow(GitLabAccounts())
    override val accounts = mutableAccounts.asStateFlow()

    override suspend fun restore(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val stored = store.read()
                currentCoroutineContext().ensureActive()
                publish(stored)
            }
        }
    }

    override suspend fun signInWithToken(instance: String, token: String): Result<GitLabAccount> = mutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val base = instanceUrl(instance)
                val value = validatedToken(token.trim())
                val user = user(base, value, personal = true)
                currentCoroutineContext().ensureActive()
                save(user.credential(base, value))
            }
        }
    }

    override suspend fun startBrowserSignIn(instance: String, clientId: String, allowWrites: Boolean): Result<String> = pendingMutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val base = instanceUrl(instance)
                val appId = clientId.trim()
                require(appId.length in 1..512 && appId.none(Char::isWhitespace))
                val pending = GitLabPendingAuthorization(base.toString(), appId, randomValue(), randomValue(), now())
                val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray(Charsets.US_ASCII)))
                currentCoroutineContext().ensureActive()
                store.writePending(pending)
                base.newBuilder().addPathSegments("oauth/authorize")
                    .addQueryParameter("client_id", appId)
                    .addQueryParameter("redirect_uri", CALLBACK)
                    .addQueryParameter("response_type", "code")
                    .addQueryParameter("scope", if (allowWrites) "read_user read_api api" else "read_user read_api")
                    .addQueryParameter("state", pending.state)
                    .addQueryParameter("code_challenge", challenge)
                    .addQueryParameter("code_challenge_method", "S256").build().toString()
            }
        }
    }

    override suspend fun completeBrowserSignIn(callback: String): Result<GitLabAccount> = mutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val (pending, code) = consumeCallback(callback)
                currentCoroutineContext().ensureActive()
                val base = instanceUrl(pending.instanceUrl)
                val token = exchange(base, FormBody.Builder()
                    .add("grant_type", "authorization_code").add("client_id", pending.clientId)
                    .add("redirect_uri", CALLBACK).add("code", code).add("code_verifier", pending.verifier).build())
                currentCoroutineContext().ensureActive()
                val user = user(base, token.accessToken, personal = false)
                currentCoroutineContext().ensureActive()
                save(user.credential(base, token.accessToken, token.refreshToken, expiry(token), pending.clientId))
            }
        }
    }

    override suspend fun cancelBrowserSignIn() = pendingMutex.withLock {
        withContext(Dispatchers.IO) { store.writePending(null) }
    }

    private suspend fun consumeCallback(callback: String): Pair<GitLabPendingAuthorization, String> = pendingMutex.withLock {
        val uri = URI(callback)
        if (uri.scheme != "etoile" || uri.host != "gitlab-oauth" || uri.rawUserInfo != null ||
            uri.port != -1 || !uri.rawPath.isNullOrEmpty() || uri.rawFragment != null) throw GitLabInvalidCallbackException()
        val entries = uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map {
            val pair = it.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        if (entries.map { it.first }.distinct().size != entries.size) throw GitLabInvalidCallbackException()
        val params = entries.toMap()
        val pending = store.pending() ?: throw GitLabInvalidCallbackException()
        if (now() < pending.createdAt || now() - pending.createdAt > 600_000 ||
            !MessageDigest.isEqual(params["state"].orEmpty().toByteArray(), pending.state.toByteArray())) {
            throw GitLabInvalidCallbackException()
        }
        // Every valid callback is consumed once, including explicit denial.
        currentCoroutineContext().ensureActive()
        store.writePending(null)
        if (params["error"] != null) throw GitLabAuthenticationException()
        val code = params["code"]?.takeIf { it.length in 1..2048 && it.none(Char::isWhitespace) }
            ?: throw GitLabInvalidCallbackException()
        pending to code
    }

    override suspend fun activate(key: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val stored = store.read()
                val credential = refreshedCredential(stored, key)
                val user = user(instanceUrl(credential.instanceUrl), credential.accessToken, credential.clientId == null)
                require(user.id == credential.id)
                currentCoroutineContext().ensureActive()
                save(credential.copy(username = user.username, name = user.name.orEmpty(), webUrl = user.webUrl.orEmpty()))
                Unit
            }
        }
    }

    override suspend fun credential(accountKey: String): GitLabCredential = mutex.withLock {
        withContext(Dispatchers.IO) {
            val stored = store.read()
            if (stored.activeKey != accountKey) throw GitLabAuthenticationException()
            refreshedCredential(stored, accountKey).also { currentCoroutineContext().ensureActive() }
        }
    }

    private fun refreshedCredential(stored: GitLabStoredAccounts, key: String): GitLabCredential {
        var credential = stored.credentials.firstOrNull { it.account().key == key }
            ?: throw GitLabAuthenticationException()
        if (credential.expiresAt?.let { it <= now() + 60_000 } == true) {
            val refreshed = exchange(instanceUrl(credential.instanceUrl), FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", credential.clientId ?: throw GitLabAuthenticationException())
                .add("refresh_token", credential.refreshToken ?: throw GitLabAuthenticationException()).build())
            credential = credential.copy(accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: throw GitLabAuthenticationException(), expiresAt = expiry(refreshed))
            // Persist rotating refresh tokens even if the following read fails or is cancelled.
            store.write(stored.copy(credentials = stored.credentials.map { if (it.account().key == key) credential else it }))
        }
        return credential
    }

    override suspend fun remove(key: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            githubRunCatching {
                val stored = store.read()
                val remaining = stored.credentials.filterNot { it.account().key == key }
                val next = stored.copy(credentials = remaining,
                    activeKey = stored.activeKey?.takeIf { active -> remaining.any { it.account().key == active } })
                currentCoroutineContext().ensureActive()
                store.write(next)
                publish(next)
            }
        }
    }

    private fun user(base: HttpUrl, token: String, personal: Boolean): UserDto {
        val request = Request.Builder().url(base.newBuilder().addPathSegments("api/v4/user").build())
            .header(if (personal) "PRIVATE-TOKEN" else "Authorization", if (personal) token else "Bearer $token")
            .header("Accept", "application/json").header("User-Agent", "Etoile-GitLab-Client").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GitLabAuthenticationException()
            val user = json.decodeFromString<UserDto>(response.body?.string() ?: throw GitLabAuthenticationException())
            require(user.id > 0 && user.username.isNotBlank() && user.state in listOf(null, "active"))
            user
        }
    }

    private fun exchange(base: HttpUrl, form: FormBody): TokenDto = client.newCall(Request.Builder()
        .url(base.newBuilder().addPathSegments("oauth/token").build()).post(form.gitLabSingleUseBody()).build()).execute().use { response ->
        if (!response.isSuccessful) throw GitLabAuthenticationException()
        json.decodeFromString<TokenDto>(response.body?.string() ?: throw GitLabAuthenticationException()).also {
            validatedToken(it.accessToken)
            it.refreshToken?.let(::validatedToken)
            require(it.tokenType.equals("bearer", true))
            require(it.expiresIn == null || it.expiresIn in 1..31_536_000)
        }
    }

    private fun expiry(token: TokenDto): Long? = token.expiresIn?.let { now() + it * 1000 }

    private fun save(credential: GitLabCredential): GitLabAccount {
        val account = credential.account()
        val existing = store.read().credentials.filterNot { it.account().key == account.key }
        require(existing.size < 20)
        val stored = GitLabStoredAccounts(existing + credential, account.key)
        store.write(stored)
        publish(stored)
        return account
    }

    private fun publish(stored: GitLabStoredAccounts) {
        mutableAccounts.value = GitLabAccounts(stored.credentials.map { it.account() }, stored.activeKey)
    }

    private fun validatedToken(value: String): String {
        require(value.length in 8..4096 && value.all { it.code in 33..126 })
        return value
    }

    private fun randomValue(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    @Serializable private data class UserDto(val id: Long, val username: String, val name: String? = null,
        @SerialName("web_url") val webUrl: String? = null, val state: String? = null) {
        fun credential(base: HttpUrl, token: String, refresh: String? = null, expires: Long? = null, clientId: String? = null) =
            GitLabCredential(id, base.toString(), username, name.orEmpty(), webUrl.orEmpty(), token, refresh, expires, clientId)
    }

    @Serializable private data class TokenDto(@SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String, @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null) {
        override fun toString() = "GitLabToken(<redacted>)"
    }

    companion object { const val CALLBACK = "etoile://gitlab-oauth" }
}
