package takagi.ru.monica.github.domain

import kotlinx.coroutines.flow.StateFlow

data class GithubAccount(
    val id: Long,
    val login: String,
    val name: String?,
    val bio: String?,
    val avatarUrl: String,
    val htmlUrl: String,
    val publicRepositories: Int,
    val followers: Int,
    val following: Int
)

sealed interface GithubSession {
    data object Loading : GithubSession
    data object SignedOut : GithubSession
    data class SignedIn(val account: GithubAccount) : GithubSession
    data class Error(val recoverable: Boolean) : GithubSession
}

interface GithubAuthRepository {
    val session: StateFlow<GithubSession>
    val accounts: StateFlow<List<GithubAccount>>
    suspend fun restore(): Result<Unit>
    suspend fun signInWithToken(token: String): Result<GithubAccount>
    suspend fun switchAccount(accountId: Long): Result<GithubAccount>
    suspend fun removeAccount(accountId: Long): Result<Unit>
    suspend fun signOut()
}
