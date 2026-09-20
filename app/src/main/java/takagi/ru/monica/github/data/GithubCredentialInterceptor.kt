package takagi.ru.monica.github.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Only API requests explicitly tagged by the account request factory can refresh. */
internal data class GithubRequestAccount(val accountId: Long)

class GithubCredentialInterceptor(
    private val refresher: GithubCredentialRefresher
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val account = original.tag(GithubRequestAccount::class.java) ?: return chain.proceed(original)
        // OkHttp interceptors execute synchronously on the network caller/dispatcher thread.
        val token = runBlocking { refresher.token(account.accountId) }.getOrElse {
            throw IOException("GitHub credentials could not be refreshed", it)
        }
        return chain.proceed(original.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
