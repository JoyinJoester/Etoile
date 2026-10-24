package takagi.ru.monica.gitlab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.github.data.githubRunCatching
import takagi.ru.monica.github.data.readAuthResponse
import takagi.ru.monica.gitlab.domain.*

/** Shared instance-bound transport. Never follows links or retries a write automatically. */
internal class GitLabApiClient(
    client: OkHttpClient,
    private val credentials: GitLabCredentialProvider,
    private val instanceUrl: (String) -> HttpUrl = ::gitLabInstanceUrl
) {
    private val client = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.authenticator(okhttp3.Authenticator.NONE).cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .cache(null).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    data class Response(val body: String, val nextPage: Int?)

    suspend fun request(
        key: String,
        segments: List<String>,
        params: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: String = "{}",
        maxBytes: Long = 2_097_152,
        oversized: GitLabReadError = GitLabReadError.INVALID_RESPONSE
    ): Response {
        require(method in setOf("GET", "POST", "PUT"))
        require(segments.none { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) })
        val credential = credentials.credential(key)
        require(credential.account().key == key)
        val builder = instanceUrl(credential.instanceUrl).newBuilder().addPathSegments("api/v4")
        segments.forEach(builder::addPathSegment)
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        val url = builder.build()
        val personal = credential.clientId == null
        val request = Request.Builder().url(url)
            .header(if (personal) "PRIVATE-TOKEN" else "Authorization", if (personal) credential.accessToken else "Bearer ${credential.accessToken}")
            .header("Accept", if (segments.lastOrNull() == "trace") "text/plain" else "application/json")
            .header("User-Agent", "Etoile-GitLab-Client")
            .apply {
                if (method != "GET") {
                    method(method, body.toRequestBody("application/json; charset=utf-8".toMediaType()).gitLabSingleUseBody())
                }
            }
            .build()
        val context = currentCoroutineContext()
        context.ensureActive()
        return client.newCall(request).readAuthResponse { response ->
            if (!response.isSuccessful) throw GitLabReadException(when (response.code) {
                401 -> GitLabReadError.AUTHENTICATION
                403 -> GitLabReadError.PERMISSION
                404 -> GitLabReadError.NOT_FOUND
                409 -> GitLabReadError.CONFLICT
                400, 405, 422 -> GitLabReadError.INVALID_INPUT
                429 -> GitLabReadError.RATE_LIMIT
                else -> GitLabReadError.NETWORK
            })
            val responseBody = response.body ?: throw GitLabReadException(GitLabReadError.INVALID_RESPONSE)
            if (responseBody.contentLength() > maxBytes) throw GitLabReadException(oversized)
            val source = responseBody.source()
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw GitLabReadException(oversized)
            context.ensureActive()
            val page = params["page"]?.toIntOrNull()
            val next = if (page == null) null else {
                val header = response.header("X-Next-Page")
                if (header != null) header.toIntOrNull()?.takeIf { it.toLong() == page.toLong() + 1 }
                else response.headers.values("Link").flatMap { it.split(',') }.firstNotNullOfOrNull { link ->
                    if (!Regex(";\\s*rel=\"?next\"?(?:\\s*;|\\s*$)").containsMatchIn(link)) null
                    else link.substringAfter('<', "").substringBefore('>').toHttpUrlOrNull()?.let { target ->
                        val nextPage = target.queryParameter("page")?.toIntOrNull()
                        val expected = nextPage?.let { url.newBuilder().setQueryParameter("page", it.toString()).build() }
                        nextPage?.takeIf { it.toLong() == page.toLong() + 1 && expected != null &&
                            target.newBuilder().query(null).build() == expected.newBuilder().query(null).build() &&
                            target.queryParameterNames == expected.queryParameterNames &&
                            target.queryParameterNames.all { name -> target.queryParameterValues(name) == expected.queryParameterValues(name) } }
                    }
                }
            }
            Response(source.readUtf8(), next)
        }
    }

    suspend fun <T> result(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        githubRunCatching {
            try { block() }
            catch (_: GitLabAuthenticationException) { throw GitLabReadException(GitLabReadError.AUTHENTICATION) }
            catch (_: java.io.IOException) { throw GitLabReadException(GitLabReadError.NETWORK) }
            catch (_: IllegalArgumentException) { throw GitLabReadException(GitLabReadError.INVALID_RESPONSE) }
        }
    }

    companion object {
        fun projectPath(id: Long): List<String> { require(id > 0); return listOf("projects", id.toString()) }
        fun pageParams(page: Int): Map<String, String> { require(page > 0); return mapOf("page" to "$page", "per_page" to "30") }
    }
}
