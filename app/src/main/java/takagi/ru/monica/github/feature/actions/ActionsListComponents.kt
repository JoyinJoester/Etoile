package takagi.ru.monica.github.feature.actions

import takagi.ru.monica.github.component.githubConfirmedAction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import takagi.ru.monica.github.component.githubCollectionSurface
import takagi.ru.monica.github.component.githubCollectionPadding
import takagi.ru.monica.github.component.GithubCollectionDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubUserMetadataLine
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubActionsConclusion
import takagi.ru.monica.github.domain.GithubActionsStatus
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowState

@Composable
internal fun ActionsWorkflowRow(
    workflow: GithubWorkflow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
    isUpdating: Boolean = false,
    hasError: Boolean = false,
    onEnabledChanged: (Boolean) -> Unit = {},
    isDispatching: Boolean = false,
    hasDispatchError: Boolean = false,
    onRequestDispatch: () -> Unit = {},
    viewerAccountId: Long? = null,
    uncertainToggle: Boolean = false
) {
    val material = LocalDesignStyle.current == DesignStyle.MATERIAL
    var menuOpen by remember(workflow.id, viewerAccountId) { mutableStateOf(false) }
    val requestToggle = githubConfirmedAction(
        title = stringResource(if (workflow.state == GithubWorkflowState.ACTIVE) R.string.github_disable_workflow else R.string.github_enable_workflow),
        message = stringResource(if (uncertainToggle) R.string.github_actions_repeat_warning else R.string.github_confirm_workflow_toggle),
        subject = workflow.name + "\n" + workflow.path,
        enabled = canManage && !isUpdating,
        confirmationKey = Triple(viewerAccountId, workflow.id, uncertainToggle),
        onConfirm = { onEnabledChanged(workflow.state != GithubWorkflowState.ACTIVE) }
    )
    val workflowToggleDescription = stringResource(R.string.github_actions_toggle_workflow, workflow.name)
    Column(
        modifier = modifier.fillMaxWidth().githubCollectionSurface()
            .clickable(role = Role.Button, onClick = onClick).githubCollectionPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
                Text(
                    text = workflow.name.ifBlank { stringResource(R.string.github_unnamed_workflow) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = workflow.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkflowStateBadge(workflow.state)
            Spacer(Modifier.weight(1f))
            if (material) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.github_more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (workflow.state == GithubWorkflowState.ACTIVE)
                                R.string.github_disable_workflow else R.string.github_enable_workflow)) },
                            enabled = canManage && !isUpdating,
                            onClick = { menuOpen = false; requestToggle() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.github_actions_dispatch)) },
                            enabled = canManage && workflow.state == GithubWorkflowState.ACTIVE && !isDispatching,
                            onClick = { menuOpen = false; onRequestDispatch() }
                        )
                    }
                }
            } else {
                Switch(
                    checked = workflow.state == GithubWorkflowState.ACTIVE,
                    onCheckedChange = { requestToggle() },
                    enabled = canManage && !isUpdating,
                    thumbContent = null,
                    modifier = Modifier.padding(start = 4.dp).semantics {
                        contentDescription = workflowToggleDescription
                    }
                )
                IconButton(
                    onClick = onRequestDispatch,
                    enabled = canManage && workflow.state == GithubWorkflowState.ACTIVE && !isDispatching,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.github_actions_dispatch)
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.github_workflow_updated, githubRelativeTime(workflow.updatedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (hasError) {
            Text(
                text = stringResource(R.string.github_actions_action_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (hasDispatchError) {
            Text(
                text = stringResource(R.string.github_actions_dispatch_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        GithubCollectionDivider(
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
internal fun WorkflowDispatchScreen(
    fullName: String,
    workflow: GithubWorkflow,
    ref: String,
    inputsText: String,
    onRefChanged: (String) -> Unit,
    onInputsChanged: (String) -> Unit,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, Map<String, String>, Boolean) -> Unit,
    unconfirmed: Boolean = false,
    viewerAccountId: Long? = null,
    onCheck: () -> Unit = {},
    onAcknowledge: () -> Unit = {}
) {
    val parsedInputs = remember(inputsText) { parseWorkflowDispatchInputs(inputsText) }
    val repeat = githubConfirmedAction(
        title = stringResource(R.string.github_actions_repeat),
        message = stringResource(R.string.github_actions_repeat_warning),
        subject = "$fullName\n${workflow.name}\n${workflow.path}\n$ref\n$inputsText",
        enabled = unconfirmed && !isSubmitting && ref.isNotBlank() && parsedInputs.invalidLine == null,
        confirmationKey = listOf(viewerAccountId, workflow.id, ref, inputsText),
        onConfirm = { onSubmit(ref.trim(), parsedInputs.values, true) }
    )
    androidx.activity.compose.BackHandler { if (!isSubmitting) onDismiss() }
    takagi.ru.monica.github.component.GithubDetailScaffold(
        title = stringResource(R.string.github_actions_dispatch),
        subtitle = "$fullName · ${workflow.name}",
        backContentDescription = stringResource(R.string.github_back),
        onBack = { if (!isSubmitting) onDismiss() }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(workflow.path, style = MaterialTheme.typography.titleSmall)
            if (unconfirmed && !isSubmitting) ActionsOutcomeNotice("$fullName · ${workflow.name}", onCheck, onAcknowledge)
            Text(stringResource(R.string.github_actions_dispatch_notice), modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = ref,
                onValueChange = onRefChanged,
                enabled = !isSubmitting,
                singleLine = true,
                label = { Text(stringResource(R.string.github_actions_ref)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inputsText,
                onValueChange = onInputsChanged,
                enabled = !isSubmitting,
                minLines = 3,
                maxLines = 8,
                isError = parsedInputs.invalidLine != null,
                supportingText = {
                    parsedInputs.invalidLine?.let { line ->
                        Text(stringResource(R.string.github_actions_inputs_invalid, line))
                    }
                },
                label = { Text(stringResource(R.string.github_actions_inputs_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (unconfirmed) repeat() else onSubmit(ref.trim(), parsedInputs.values, false)
                },
                enabled = !isSubmitting && ref.isNotBlank() && parsedInputs.invalidLine == null,
                modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.github_run)) }
        }
    }
}

@Composable
internal fun WorkflowRunRow(
    run: GithubWorkflowRun,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().githubCollectionSurface().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .githubCollectionPadding()
    ) {
        Row(verticalAlignment = Alignment.Top) {
            GithubActionsStatusBadge(run.status, run.conclusion)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.displayTitle.ifBlank { stringResource(R.string.github_unnamed_run) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.github_run_metadata,
                        run.runNumber,
                        run.event,
                        githubRelativeTime(run.createdAt)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
                when {
                    run.actor.login.isNotBlank() -> GithubUserMetadataLine(
                        prefix = run.headBranch
                            ?.takeIf(String::isNotBlank)
                            ?.let { stringResource(R.string.github_run_branch_prefix, it) }
                            .orEmpty(),
                        login = run.actor.login,
                        avatarUrl = run.actor.avatarUrl,
                        suffix = "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    !run.headBranch.isNullOrBlank() -> Text(
                        text = run.headBranch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp).size(18.dp)
            )
        }
        GithubCollectionDivider(
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
internal fun ActionsJobRow(
    job: GithubWorkflowJob,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().githubCollectionSurface().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .githubCollectionPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GithubActionsStatusBadge(job.status, job.conclusion)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.name.ifBlank { stringResource(R.string.github_unnamed_job) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = job.runnerName?.let { runner ->
                        "$runner · ${stringResource(R.string.github_steps_count, job.steps.size)}"
                    } ?: stringResource(R.string.github_steps_count, job.steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 6.dp).size(18.dp)
            )
        }
        GithubCollectionDivider(
            modifier = Modifier.padding(top = 13.dp)
        )
    }
}

@Composable
internal fun GithubActionsStatusBadge(
    status: GithubActionsStatus,
    conclusion: GithubActionsConclusion?,
    modifier: Modifier = Modifier
) {
    val isSuccess = conclusion == GithubActionsConclusion.SUCCESS
    val isFailure = when (conclusion) {
        GithubActionsConclusion.FAILURE,
        GithubActionsConclusion.TIMED_OUT,
        GithubActionsConclusion.ACTION_REQUIRED,
        GithubActionsConclusion.STARTUP_FAILURE -> true
        else -> false
    }
    val isRunning = conclusion == null && when (status) {
        GithubActionsStatus.IN_PROGRESS,
        GithubActionsStatus.QUEUED,
        GithubActionsStatus.WAITING,
        GithubActionsStatus.REQUESTED,
        GithubActionsStatus.PENDING -> true
        else -> false
    }
    val container: Color = when {
        isSuccess -> MaterialTheme.colorScheme.primaryContainer
        isFailure -> MaterialTheme.colorScheme.errorContainer
        isRunning -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content: Color = when {
        isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
        isFailure -> MaterialTheme.colorScheme.onErrorContainer
        isRunning -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(modifier = modifier, shape = GithubExpressiveShapes.control, color = container) {
        Text(
            text = actionsStatusLabel(status, conclusion),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WorkflowStateBadge(state: GithubWorkflowState) {
    val active = state == GithubWorkflowState.ACTIVE
    Surface(
        shape = GithubExpressiveShapes.control,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = stringResource(
                if (active) R.string.github_workflow_active else R.string.github_workflow_disabled
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun actionsStatusLabel(
    status: GithubActionsStatus,
    conclusion: GithubActionsConclusion?
): String = stringResource(
    when (conclusion) {
        GithubActionsConclusion.SUCCESS -> R.string.github_actions_success
        GithubActionsConclusion.FAILURE -> R.string.github_actions_failure
        GithubActionsConclusion.CANCELLED -> R.string.github_actions_cancelled
        GithubActionsConclusion.SKIPPED -> R.string.github_actions_skipped
        GithubActionsConclusion.TIMED_OUT -> R.string.github_actions_timed_out
        GithubActionsConclusion.ACTION_REQUIRED -> R.string.github_actions_action_required
        GithubActionsConclusion.NEUTRAL -> R.string.github_actions_neutral
        GithubActionsConclusion.STALE -> R.string.github_actions_stale
        GithubActionsConclusion.STARTUP_FAILURE -> R.string.github_actions_startup_failure
        GithubActionsConclusion.UNKNOWN -> R.string.github_actions_status_unknown
        null -> when (status) {
            GithubActionsStatus.QUEUED -> R.string.github_actions_queued
            GithubActionsStatus.IN_PROGRESS -> R.string.github_actions_in_progress
            GithubActionsStatus.COMPLETED -> R.string.github_actions_completed
            GithubActionsStatus.WAITING -> R.string.github_actions_waiting
            GithubActionsStatus.REQUESTED -> R.string.github_actions_requested
            GithubActionsStatus.PENDING -> R.string.github_actions_pending
            GithubActionsStatus.UNKNOWN -> R.string.github_actions_status_unknown
        }
    }
)
