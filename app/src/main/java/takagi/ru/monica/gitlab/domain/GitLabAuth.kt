package takagi.ru.monica.gitlab.domain

import kotlinx.coroutines.flow.StateFlow

data class GitLabAccount(
    val id: Long,
    val instanceUrl: String,
    val username: String,
    val name: String,
    val webUrl: String
) {
    // User IDs are only unique within an instance, and never shared with GitHub.
    val key: String get() = "$instanceUrl#$id"
}

data class GitLabAccounts(
    val accounts: List<GitLabAccount> = emptyList(),
    val activeKey: String? = null
)

interface GitLabAuthRepository {
    val accounts: StateFlow<GitLabAccounts>
    suspend fun restore(): Result<Unit>
    suspend fun signInWithToken(instance: String, token: String): Result<GitLabAccount>
    suspend fun startBrowserSignIn(instance: String, clientId: String, allowWrites: Boolean = false): Result<String>
    suspend fun completeBrowserSignIn(callback: String): Result<GitLabAccount>
    suspend fun cancelBrowserSignIn()
    suspend fun activate(key: String): Result<Unit>
    suspend fun remove(key: String): Result<Unit>
}

class GitLabAuthenticationException : Exception("GitLab authentication failed")
class GitLabInvalidCallbackException : Exception("Invalid or expired GitLab callback")
