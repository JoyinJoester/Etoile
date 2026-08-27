package takagi.ru.monica.github.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import takagi.ru.monica.github.domain.GithubAccount

interface GithubAccountRemoteDataSource {
    suspend fun authenticatedUser(token: String): Result<GithubAccount>
}

class GithubAccountApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : GithubAccountRemoteDataSource {
    override suspend fun authenticatedUser(token: String): Result<GithubAccount> = withContext(Dispatchers.IO) {
        githubRunCatching {
            val request = GithubRequestFactory.authenticatedBuilder("https://api.github.com/user", token)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw GithubAuthenticationException()
                check(response.isSuccessful) { "GitHub account request failed" }
                val payload = response.body?.string().orEmpty()
                json.decodeFromString(AccountDto.serializer(), payload).toDomain()
            }
        }
    }

    @Serializable
    private data class AccountDto(
        val id: Long,
        val login: String,
        val name: String? = null,
        val bio: String? = null,
        @SerialName("avatar_url") val avatarUrl: String,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("public_repos") val publicRepositories: Int = 0,
        val followers: Int = 0,
        val following: Int = 0
    ) {
        fun toDomain() = GithubAccount(id, login, name, bio, avatarUrl, htmlUrl, publicRepositories, followers, following)
    }
}

class GithubAuthenticationException : IllegalStateException("GitHub authentication failed")
