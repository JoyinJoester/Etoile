package takagi.ru.monica.github.domain

/** Lightweight user identity reused by conversations, Actions and releases. */
data class GithubUserSummary(
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String
)
