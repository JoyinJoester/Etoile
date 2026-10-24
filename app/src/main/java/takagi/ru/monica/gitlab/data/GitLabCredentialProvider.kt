package takagi.ru.monica.gitlab.data

/** Data-layer only: credentials must never enter UI state, routes or saved state. */
internal fun interface GitLabCredentialProvider {
    suspend fun credential(accountKey: String): GitLabCredential
}
