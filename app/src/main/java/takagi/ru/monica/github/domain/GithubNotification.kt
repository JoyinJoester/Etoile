package takagi.ru.monica.github.domain

enum class GithubNotificationReason { REVIEW_REQUESTED, MENTION, ASSIGN, AUTHOR, COMMENT, INVITATION, OTHER }

data class GithubNotification(
    val id: String,
    val reason: GithubNotificationReason,
    val unread: Boolean,
    val title: String,
    val subjectType: String,
    val repository: String,
    val repositoryUrl: String,
    val updatedAt: String,
    val subjectUrl: String? = null
)

interface GithubNotificationsRepository {
    suspend fun notifications(
        page: Int = 1,
        perPage: Int = 50
    ): Result<GithubPage<GithubNotification>>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun markDone(id: String): Result<Unit>
    suspend fun unsubscribeAndMarkDone(id: String): Result<Unit>
}
