package takagi.ru.monica.github.data

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object GithubNetwork {
    val rateLimitStore = GithubRateLimitStore()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(GithubRateLimitInterceptor(rateLimitStore))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Builds every GitHub request with the same API headers. Keeping this in one
 * place prevents individual repositories from drifting apart as new endpoints
 * are added.
 */
object GithubRequestFactory {
    fun publicBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "Etoile-GitHub-Client")

    fun authenticatedBuilder(url: String, token: String): Request.Builder =
        publicBuilder(url).header("Authorization", "Bearer $token")
}

/**
 * Like [runCatching], but preserves structured-concurrency cancellation.
 * Network repositories use this instead of accidentally turning cancellation
 * into a normal failure when a newer search or screen request supersedes one.
 */
suspend fun <T> githubRunCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
