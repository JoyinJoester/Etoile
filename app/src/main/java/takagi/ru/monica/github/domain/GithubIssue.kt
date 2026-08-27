package takagi.ru.monica.github.domain

enum class GithubIssueState { OPEN, CLOSED }

enum class GithubReactionContent(val apiValue: String) {
    PLUS_ONE("+1"),
    MINUS_ONE("-1"),
    LAUGH("laugh"),
    CONFUSED("confused"),
    HEART("heart"),
    HOORAY("hooray"),
    ROCKET("rocket"),
    EYES("eyes");

    companion object {
        fun fromApiValue(value: String): GithubReactionContent? =
            entries.firstOrNull { it.apiValue == value }
    }
}

data class GithubReactionCounts(
    val values: Map<GithubReactionContent, Int> = emptyMap()
) {
    fun count(content: GithubReactionContent): Int = values[content] ?: 0

    fun withDelta(content: GithubReactionContent, delta: Int): GithubReactionCounts {
        val next = (count(content) + delta).coerceAtLeast(0)
        return copy(values = values + (content to next))
    }
}

data class GithubReactionToggle(
    val content: GithubReactionContent,
    val active: Boolean,
    val reactionId: Long?
)

data class GithubIssueLabel(
    val name: String,
    val color: String,
    val description: String?
)

data class GithubIssueMilestone(
    val number: Int,
    val title: String,
    val description: String?,
    val openIssues: Int,
    val closedIssues: Int,
    val dueOn: String?
)

data class GithubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: GithubIssueState,
    val author: GithubUserSummary,
    val labels: List<GithubIssueLabel>,
    val assignees: List<GithubUserSummary>,
    val comments: Int,
    val isLocked: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val htmlUrl: String,
    val milestone: GithubIssueMilestone? = null
)

data class GithubIssueComment(
    val id: Long,
    val body: String,
    val author: GithubUserSummary,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val reactions: GithubReactionCounts = GithubReactionCounts()
)

class GithubIssueDraft private constructor(
    val title: String,
    val body: String?
) {
    companion object {
        const val MAX_TITLE_LENGTH = 256
        const val MAX_BODY_LENGTH = 65_536

        fun fromInput(title: String, body: String?): Result<GithubIssueDraft> = runCatching {
            val normalizedTitle = title.trim()
            val normalizedBody = body?.trim()?.takeIf(String::isNotEmpty)
            require(normalizedTitle.isNotEmpty() && normalizedTitle.length <= MAX_TITLE_LENGTH)
            require(normalizedBody == null || normalizedBody.length <= MAX_BODY_LENGTH)
            GithubIssueDraft(title = normalizedTitle, body = normalizedBody)
        }
    }
}

class GithubIssueCommentDraft private constructor(val body: String) {
    companion object {
        const val MAX_BODY_LENGTH = 65_536

        fun fromInput(body: String): Result<GithubIssueCommentDraft> = runCatching {
            val normalized = body.trim()
            require(normalized.isNotEmpty() && normalized.length <= MAX_BODY_LENGTH)
            GithubIssueCommentDraft(normalized)
        }
    }
}

interface GithubIssuesRepository {
    suspend fun issues(
        owner: String,
        name: String,
        query: GithubIssueListQuery,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubIssue>>

    suspend fun issue(owner: String, name: String, number: Int): Result<GithubIssue>

    suspend fun comments(
        owner: String,
        name: String,
        number: Int,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubIssueComment>>

    suspend fun createIssue(
        owner: String,
        name: String,
        draft: GithubIssueDraft
    ): Result<GithubIssue>

    suspend fun updateIssue(
        owner: String,
        name: String,
        number: Int,
        draft: GithubIssueDraft
    ): Result<GithubIssue>

    suspend fun addComment(
        owner: String,
        name: String,
        number: Int,
        draft: GithubIssueCommentDraft
    ): Result<GithubIssueComment>

    suspend fun toggleCommentReaction(
        owner: String,
        name: String,
        commentId: Long,
        content: GithubReactionContent,
        viewerLogin: String
    ): Result<GithubReactionToggle>

    suspend fun updateIssueState(
        owner: String,
        name: String,
        number: Int,
        state: GithubIssueState
    ): Result<GithubIssue>

    suspend fun updateIssueLock(
        owner: String,
        name: String,
        number: Int,
        locked: Boolean
    ): Result<GithubIssue>

    suspend fun labels(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubIssueLabel>>

    suspend fun updateIssueLabels(
        owner: String,
        name: String,
        number: Int,
        labels: List<String>
    ): Result<GithubIssue>

    suspend fun assignees(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubUserSummary>>

    suspend fun updateIssueAssignees(
        owner: String,
        name: String,
        number: Int,
        assignees: List<String>
    ): Result<GithubIssue>

    suspend fun milestones(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubIssueMilestone>>

    suspend fun updateIssueMilestone(
        owner: String,
        name: String,
        number: Int,
        milestoneNumber: Int?
    ): Result<GithubIssue>
}
