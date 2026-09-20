package takagi.ru.monica.github.feature.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubUserLink
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubActionsLog
import takagi.ru.monica.github.domain.GithubWorkflowArtifact
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.domain.GithubWorkflowRunAction

@Composable
internal fun ActionsRunSummaryCard(
    run: GithubWorkflowRun,
    modifier: Modifier = Modifier,
    onAction: ((GithubWorkflowRunAction) -> Unit)? = null,
    canManage: Boolean = false,
    isPerformingAction: Boolean = false,
    actionError: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            GithubActionsStatusBadge(run.status, run.conclusion)
            Text(
                text = run.displayTitle.ifBlank { stringResource(R.string.github_unnamed_run) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = stringResource(R.string.github_run_number, run.runNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            GithubMetadataRow(
                icon = Icons.Default.PlayArrow,
                title = stringResource(R.string.github_event),
                value = run.event
            )
            GithubMetadataRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(R.string.github_branch),
                value = run.headBranch?.ifBlank { null }
                    ?: stringResource(R.string.github_unknown_language)
            )
            GithubMetadataRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.github_triggered_by),
                value = run.actor.login,
                valueContent = { GithubUserLink(run.actor.login, avatarUrl = run.actor.avatarUrl, maxLines = Int.MAX_VALUE) }
            )
            GithubMetadataRow(
                icon = Icons.Default.Code,
                title = stringResource(R.string.github_commit),
                value = run.headSha.take(12)
            )
            if (onAction != null) {
                if (actionError) {
                    Text(
                        text = stringResource(R.string.github_actions_action_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (run.status == takagi.ru.monica.github.domain.GithubActionsStatus.COMPLETED) {
                    Button(
                        onClick = { onAction(GithubWorkflowRunAction.RERUN) },
                        enabled = canManage && !isPerformingAction,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 14.dp),
                        shape = GithubExpressiveShapes.control
                    ) {
                        if (isPerformingAction) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.github_actions_rerun))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAction(GithubWorkflowRunAction.CANCEL) },
                        enabled = canManage && !isPerformingAction,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 14.dp),
                        shape = GithubExpressiveShapes.control
                    ) {
                        if (isPerformingAction) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.github_actions_cancel))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionsArtifactRow(
    artifact: GithubWorkflowArtifact,
    isDownloading: Boolean,
    isBusy: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artifact.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(Formatter.formatShortFileSize(context, artifact.sizeBytes))
                    if (artifact.isExpired) {
                        append(" · ")
                        append(stringResource(R.string.github_actions_artifact_expired))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        if (isDownloading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.padding(start = 8.dp).size(18.dp)
            )
        } else {
            IconButton(
                onClick = onDownload,
                enabled = !artifact.isExpired && !isBusy
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.github_actions_download_artifact),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun ActionsJobSummaryCard(
    job: GithubWorkflowJob,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.prominent,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                GithubActionsStatusBadge(job.status, job.conclusion)
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp)
                )
                job.runnerName?.let { runner ->
                    GithubMetadataRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.github_runner),
                        value = runner,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (job.labels.isNotEmpty()) {
                    Text(
                        text = job.labels.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        GithubSectionHeader(title = stringResource(R.string.github_steps))
        if (job.steps.isEmpty()) {
            Text(
                text = stringResource(R.string.github_no_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = GithubExpressiveShapes.container,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    job.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = step.number.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = 24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = step.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                GithubActionsStatusBadge(step.status, step.conclusion)
                            }
                        }
                        if (index != job.steps.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionsLogPanel(
    log: GithubActionsLog,
    modifier: Modifier = Modifier
) {
    val formattedLog = remember(log.text) { formatGithubActionsLog(log.text) }
    var wrapLines by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = modifier) {
        GithubSectionHeader(
            title = stringResource(R.string.github_job_log),
            action = stringResource(if (wrapLines) R.string.github_log_horizontal_scroll else R.string.github_log_wrap_lines),
            onAction = { wrapLines = !wrapLines }
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            if (formattedLog.isBlank()) {
                Text(
                    text = stringResource(R.string.github_actions_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            } else {
                SelectionContainer {
                    Text(
                        text = formattedLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .then(if (wrapLines) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        softWrap = wrapLines
                    )
                }
            }
        }
        if (log.isTruncated) {
            Text(
                text = stringResource(R.string.github_actions_log_truncated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
