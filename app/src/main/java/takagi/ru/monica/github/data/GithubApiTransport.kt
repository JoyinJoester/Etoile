package takagi.ru.monica.github.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * A transport failure cannot establish whether GitHub committed a mutation.
 * Preserve the first response and never follow redirects or replay a write.
 * Read-only HTTP requests keep the caller's policy. Cancellation remains attached
 * until [read] finishes, including reading or decoding the response body.
 */
internal suspend fun <T> OkHttpClient.readGithubResponse(request: Request, read: (Response) -> T): T {
    val isRead = request.method in setOf("GET", "HEAD", "OPTIONS")
    val call = if (isRead) newCall(request) else {
        val singleUse = request.newBuilder().method(request.method,
            (request.body ?: ByteArray(0).toRequestBody()).githubSingleUseBody()).build()
        // Retains interceptors, authentication, connection pool, dispatcher and
        // per-client timeouts (including the longer release-upload timeout).
        newBuilder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build().newCall(singleUse)
    }
    return call.readAuthResponse(read)
}
