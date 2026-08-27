package takagi.ru.monica.github.domain

enum class GithubWorkflowState {
    ACTIVE,
    DISABLED_MANUALLY,
    DISABLED_INACTIVITY,
    DISABLED_FORK,
    UNKNOWN
}

enum class GithubActionsStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    WAITING,
    REQUESTED,
    PENDING,
    UNKNOWN
}

enum class GithubActionsConclusion {
    SUCCESS,
    FAILURE,
    CANCELLED,
    SKIPPED,
    TIMED_OUT,
    ACTION_REQUIRED,
    NEUTRAL,
    STALE,
    STARTUP_FAILURE,
    UNKNOWN
}

data class GithubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: GithubWorkflowState,
    val htmlUrl: String,
    val badgeUrl: String?,
    val createdAt: String,
    val updatedAt: String
)

data class GithubWorkflowRun(
    val id: Long,
    val workflowId: Long,
    val name: String,
    val displayTitle: String,
    val runNumber: Int,
    val event: String,
    val status: GithubActionsStatus,
    val conclusion: GithubActionsConclusion?,
    val headBranch: String?,
    val headSha: String,
    val actor: GithubUserSummary,
    val createdAt: String,
    val updatedAt: String,
    val runStartedAt: String?,
    val htmlUrl: String
)

data class GithubWorkflowStep(
    val number: Int,
    val name: String,
    val status: GithubActionsStatus,
    val conclusion: GithubActionsConclusion?,
    val startedAt: String?,
    val completedAt: String?
)

data class GithubWorkflowJob(
    val id: Long,
    val runId: Long,
    val name: String,
    val status: GithubActionsStatus,
    val conclusion: GithubActionsConclusion?,
    val startedAt: String?,
    val completedAt: String?,
    val htmlUrl: String,
    val runnerName: String?,
    val labels: List<String>,
    val steps: List<GithubWorkflowStep>
)

data class GithubActionsLog(
    val text: String,
    val isTruncated: Boolean
)

enum class GithubWorkflowRunAction { RERUN, CANCEL }

interface GithubActionsRepository {
    suspend fun workflows(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubWorkflow>>

    suspend fun workflowRuns(
        owner: String,
        name: String,
        workflowId: Long,
        page: Int = 1,
        perPage: Int = 30
    ): Result<GithubPage<GithubWorkflowRun>>

    suspend fun workflowRun(
        owner: String,
        name: String,
        runId: Long
    ): Result<GithubWorkflowRun>

    suspend fun jobs(
        owner: String,
        name: String,
        runId: Long,
        page: Int = 1,
        perPage: Int = 100
    ): Result<GithubPage<GithubWorkflowJob>>

    suspend fun job(
        owner: String,
        name: String,
        jobId: Long
    ): Result<GithubWorkflowJob>

    suspend fun jobLog(
        owner: String,
        name: String,
        jobId: Long
    ): Result<GithubActionsLog>

    suspend fun performRunAction(
        owner: String,
        name: String,
        runId: Long,
        action: GithubWorkflowRunAction
    ): Result<Unit>

    suspend fun setWorkflowEnabled(
        owner: String,
        name: String,
        workflowId: Long,
        enabled: Boolean
    ): Result<Unit>

    suspend fun dispatchWorkflow(
        owner: String,
        name: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit>
}
