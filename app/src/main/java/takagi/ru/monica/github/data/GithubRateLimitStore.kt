package takagi.ru.monica.github.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response
import takagi.ru.monica.github.domain.GithubRateLimitMonitor
import takagi.ru.monica.github.domain.GithubRateLimitSnapshot

class GithubRateLimitStore : GithubRateLimitMonitor {
    private val _state = MutableStateFlow<Map<String, GithubRateLimitSnapshot>>(emptyMap())
    override val state: StateFlow<Map<String, GithubRateLimitSnapshot>> = _state.asStateFlow()

    internal fun update(snapshot: GithubRateLimitSnapshot) {
        _state.update { current ->
            val previous = current[snapshot.resource]
            val shouldReplace = when {
                previous == null -> true
                snapshot.resetAtEpochSeconds > previous.resetAtEpochSeconds -> true
                snapshot.resetAtEpochSeconds < previous.resetAtEpochSeconds -> false
                else -> snapshot.used >= previous.used
            }
            if (shouldReplace) current + (snapshot.resource to snapshot) else current
        }
    }
}

internal class GithubRateLimitInterceptor(
    private val store: GithubRateLimitStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        response.toRateLimitSnapshot()?.let(store::update)
        return response
    }
}

private fun Response.toRateLimitSnapshot(): GithubRateLimitSnapshot? {
    val limit = header("X-RateLimit-Limit")?.toIntOrNull() ?: return null
    val remaining = header("X-RateLimit-Remaining")?.toIntOrNull() ?: return null
    val reset = header("X-RateLimit-Reset")?.toLongOrNull() ?: return null
    val used = header("X-RateLimit-Used")?.toIntOrNull() ?: (limit - remaining).coerceAtLeast(0)
    return GithubRateLimitSnapshot(
        resource = header("X-RateLimit-Resource").orEmpty().ifBlank { "core" },
        limit = limit.coerceAtLeast(0),
        remaining = remaining.coerceAtLeast(0),
        used = used.coerceAtLeast(0),
        resetAtEpochSeconds = reset
    )
}
