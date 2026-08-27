package takagi.ru.monica.github.domain

enum class GithubPullRequestState { OPEN, CLOSED }
enum class GithubReviewState { PENDING, COMMENTED, APPROVED, CHANGES_REQUESTED, DISMISSED, UNKNOWN }
enum class GithubReviewEvent { COMMENT, APPROVE, REQUEST_CHANGES }
enum class GithubMergeMethod { MERGE, SQUASH, REBASE }

class GithubMergeDraft private constructor(
    val method: GithubMergeMethod,
    val expectedHeadSha: String,
    val commitTitle: String?,
    val commitMessage: String?
) {
    companion object {
        const val MAX_TITLE_LENGTH = 256
        const val MAX_MESSAGE_LENGTH = 65_536
        const val MAX_SHA_LENGTH = 128

        fun fromInput(
            method: GithubMergeMethod,
            expectedHeadSha: String,
            commitTitle: String? = null,
            commitMessage: String? = null
        ): Result<GithubMergeDraft> = runCatching {
            val normalizedSha = expectedHeadSha.trim()
            val normalizedTitle = commitTitle?.trim()?.takeIf(String::isNotEmpty)
                ?.takeUnless { method == GithubMergeMethod.REBASE }
            val normalizedMessage = commitMessage?.trim()?.takeIf(String::isNotEmpty)
                ?.takeUnless { method == GithubMergeMethod.REBASE }
            require(normalizedSha.isNotEmpty() && normalizedSha.length <= MAX_SHA_LENGTH)
            require(normalizedTitle == null || normalizedTitle.length <= MAX_TITLE_LENGTH)
            require(normalizedMessage == null || normalizedMessage.length <= MAX_MESSAGE_LENGTH)
            GithubMergeDraft(
                method = method,
                expectedHeadSha = normalizedSha,
                commitTitle = normalizedTitle,
                commitMessage = normalizedMessage
            )
        }
    }
}

data class GithubPullRequestRef(
    val label: String,
    val ref: String,
    val sha: String,
    val repositoryFullName: String?
)

data class GithubPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: GithubPullRequestState,
    val isDraft: Boolean,
    val isMerged: Boolean,
    val mergeable: Boolean?,
    val mergeableState: String?,
    val author: GithubUserSummary,
    val labels: List<GithubIssueLabel>,
    val assignees: List<GithubUserSummary>,
    val requestedReviewers: List<GithubUserSummary>,
    val head: GithubPullRequestRef,
    val base: GithubPullRequestRef,
    val comments: Int,
    val reviewComments: Int,
    val commits: Int,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val mergedAt: String?,
    val htmlUrl: String,
    val isLocked: Boolean = false,
    val milestone: GithubIssueMilestone? = null
)

data class GithubPullRequestFile(
    val sha: String,
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?,
    val blobUrl: String,
    val rawUrl: String?
)

data class GithubPullRequestReview(
    val id: Long,
    val body: String,
    val state: GithubReviewState,
    val author: GithubUserSummary,
    val submittedAt: String?,
    val htmlUrl: String
)

data class GithubPullRequestReviewComment(
    val id: Long,
    val body: String,
    val path: String,
    val line: Int?,
    val startLine: Int?,
    val side: String?,
    val diffHunk: String,
    val author: GithubUserSummary,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String
)

class GithubPullRequestDraft private constructor(
    val title: String,
    val body: String?
) {
    companion object {
        const val MAX_TITLE_LENGTH = 256
        const val MAX_BODY_LENGTH = 65_536

        fun fromInput(title: String, body: String?): Result<GithubPullRequestDraft> = runCatching {
            val normalizedTitle = title.trim()
            val normalizedBody = body?.trim()?.takeIf(String::isNotEmpty)
            require(normalizedTitle.isNotEmpty() && normalizedTitle.length <= MAX_TITLE_LENGTH)
            require(normalizedBody == null || normalizedBody.length <= MAX_BODY_LENGTH)
            GithubPullRequestDraft(title = normalizedTitle, body = normalizedBody)
        }
    }
}

class GithubRequestedReviewersUpdate private constructor(
    val current: Set<String>,
    val requested: Set<String>
) {
    val toAdd: Set<String> get() = requested - current
    val toRemove: Set<String> get() = current - requested

    companion object {
        const val MAX_REVIEWERS = 15
        private val LOGIN_PATTERN = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")

        fun fromInput(
            current: List<String>,
            requested: List<String>
        ): Result<GithubRequestedReviewersUpdate> = runCatching {
            val normalizedCurrent = current.normalizeGithubLogins()
            val normalizedRequested = requested.normalizeGithubLogins()
            require(normalizedRequested.size <= MAX_REVIEWERS)
            require((normalizedCurrent + normalizedRequested).all(LOGIN_PATTERN::matches))
            GithubRequestedReviewersUpdate(
                current = normalizedCurrent,
                requested = normalizedRequested
            )
        }

        private fun List<String>.normalizeGithubLogins(): Set<String> =
            map(String::trim).filter(String::isNotEmpty).map { it.lowercase() }.toSet()
    }
}

class GithubPullRequestReviewDraft private constructor(
    val event: GithubReviewEvent,
    val body: String?
) {
    companion object {
        const val MAX_BODY_LENGTH = 65_536

        fun fromInput(event: GithubReviewEvent, body: String): Result<GithubPullRequestReviewDraft> = runCatching {
            val normalized = body.trim().takeIf(String::isNotEmpty)
            require(normalized == null || normalized.length <= MAX_BODY_LENGTH)
            if (event != GithubReviewEvent.APPROVE) require(normalized != null)
            GithubPullRequestReviewDraft(event = event, body = normalized)
        }
    }
}

data class GithubMergeResult(
    val sha: String?,
    val merged: Boolean,
    val message: String
)

interface GithubPullRequestsRepository {
    suspend fun pullRequests(
        owner: String,
        name: String,
        query: GithubPullRequestListQuery,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubPullRequest>>

    suspend fun pullRequest(owner: String, name: String, number: Int): Result<GithubPullRequest>

    suspend fun files(
        owner: String,
        name: String,
        number: Int,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubPullRequestFile>>

    suspend fun reviews(
        owner: String,
        name: String,
        number: Int,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubPullRequestReview>>

    suspend fun reviewComments(
        owner: String,
        name: String,
        number: Int,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubPullRequestReviewComment>>

    suspend fun submitReview(
        owner: String,
        name: String,
        number: Int,
        draft: GithubPullRequestReviewDraft
    ): Result<GithubPullRequestReview>

    suspend fun merge(
        owner: String,
        name: String,
        number: Int,
        draft: GithubMergeDraft
    ): Result<GithubMergeResult>

    suspend fun updateState(
        owner: String,
        name: String,
        number: Int,
        state: GithubPullRequestState
    ): Result<GithubPullRequest>

    suspend fun updateContent(
        owner: String,
        name: String,
        number: Int,
        draft: GithubPullRequestDraft
    ): Result<GithubPullRequest>

    suspend fun updateRequestedReviewers(
        owner: String,
        name: String,
        number: Int,
        update: GithubRequestedReviewersUpdate
    ): Result<GithubPullRequest>
}
