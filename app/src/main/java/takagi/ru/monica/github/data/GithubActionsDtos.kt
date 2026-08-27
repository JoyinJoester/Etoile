package takagi.ru.monica.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import takagi.ru.monica.github.domain.GithubActionsConclusion
import takagi.ru.monica.github.domain.GithubActionsStatus
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowState
import takagi.ru.monica.github.domain.GithubWorkflowStep

@Serializable
internal data class GithubWorkflowsResponseDto(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<GithubWorkflowDto> = emptyList()
)

@Serializable
internal data class GithubWorkflowRunsResponseDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GithubWorkflowRunDto> = emptyList()
)

@Serializable
internal data class GithubWorkflowJobsResponseDto(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<GithubWorkflowJobDto> = emptyList()
)

@Serializable
internal data class GithubWorkflowDto(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("badge_url") val badgeUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
) {
    fun toDomain() = GithubWorkflow(
        id = id,
        name = name,
        path = path,
        state = state.toGithubWorkflowState(),
        htmlUrl = htmlUrl,
        badgeUrl = badgeUrl,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Serializable
internal data class GithubWorkflowRunDto(
    val id: Long,
    @SerialName("workflow_id") val workflowId: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    val event: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String = "",
    val actor: GithubUserDto? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String
) {
    fun toDomain() = GithubWorkflowRun(
        id = id,
        workflowId = workflowId,
        name = name.orEmpty(),
        displayTitle = displayTitle.orEmpty().ifBlank { name.orEmpty() },
        runNumber = runNumber,
        event = event,
        status = status.toGithubActionsStatus(),
        conclusion = conclusion.toGithubActionsConclusion(),
        headBranch = headBranch,
        headSha = headSha,
        actor = actor.toDomainOrGhost(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        runStartedAt = runStartedAt,
        htmlUrl = htmlUrl
    )
}

@Serializable
internal data class GithubWorkflowJobDto(
    val id: Long,
    @SerialName("run_id") val runId: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("runner_name") val runnerName: String? = null,
    val labels: List<String> = emptyList(),
    val steps: List<GithubWorkflowStepDto> = emptyList()
) {
    fun toDomain() = GithubWorkflowJob(
        id = id,
        runId = runId,
        name = name,
        status = status.toGithubActionsStatus(),
        conclusion = conclusion.toGithubActionsConclusion(),
        startedAt = startedAt,
        completedAt = completedAt,
        htmlUrl = htmlUrl,
        runnerName = runnerName,
        labels = labels,
        steps = steps.map(GithubWorkflowStepDto::toDomain)
    )
}

@Serializable
internal data class GithubWorkflowStepDto(
    val number: Int,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
) {
    fun toDomain() = GithubWorkflowStep(
        number = number,
        name = name,
        status = status.toGithubActionsStatus(),
        conclusion = conclusion.toGithubActionsConclusion(),
        startedAt = startedAt,
        completedAt = completedAt
    )
}

private fun String.toGithubWorkflowState(): GithubWorkflowState = when (lowercase()) {
    "active" -> GithubWorkflowState.ACTIVE
    "disabled_manually" -> GithubWorkflowState.DISABLED_MANUALLY
    "disabled_inactivity" -> GithubWorkflowState.DISABLED_INACTIVITY
    "disabled_fork" -> GithubWorkflowState.DISABLED_FORK
    else -> GithubWorkflowState.UNKNOWN
}

internal fun String?.toGithubActionsStatus(): GithubActionsStatus = when (this?.lowercase()) {
    "queued" -> GithubActionsStatus.QUEUED
    "in_progress" -> GithubActionsStatus.IN_PROGRESS
    "completed" -> GithubActionsStatus.COMPLETED
    "waiting" -> GithubActionsStatus.WAITING
    "requested" -> GithubActionsStatus.REQUESTED
    "pending" -> GithubActionsStatus.PENDING
    else -> GithubActionsStatus.UNKNOWN
}

internal fun String?.toGithubActionsConclusion(): GithubActionsConclusion? = when (this?.lowercase()) {
    null -> null
    "success" -> GithubActionsConclusion.SUCCESS
    "failure" -> GithubActionsConclusion.FAILURE
    "cancelled" -> GithubActionsConclusion.CANCELLED
    "skipped" -> GithubActionsConclusion.SKIPPED
    "timed_out" -> GithubActionsConclusion.TIMED_OUT
    "action_required" -> GithubActionsConclusion.ACTION_REQUIRED
    "neutral" -> GithubActionsConclusion.NEUTRAL
    "stale" -> GithubActionsConclusion.STALE
    "startup_failure" -> GithubActionsConclusion.STARTUP_FAILURE
    else -> GithubActionsConclusion.UNKNOWN
}
