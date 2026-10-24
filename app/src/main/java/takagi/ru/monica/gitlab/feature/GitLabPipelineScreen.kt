package takagi.ru.monica.gitlab.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.*
import takagi.ru.monica.gitlab.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitLabPipelineScreen(
    state: GitLabPipelineState,
    projectId: Long,
    pipelineId: Long,
    onAction: (GitLabPipelineAction) -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit
) {
    val back = { if (state.traceJob != null) onAction(GitLabPipelineAction.CloseTrace) else if (!state.writing) onBack() }
    BackHandler(enabled = state.traceJob != null || state.writing, onBack = back)
    val lines = remember(state.trace) { state.trace?.lineSequence()?.take(5001)?.toList().orEmpty() }
    val jobsListState = key(state.account?.key, projectId, pipelineId) { rememberLazyListState() }
    val traceListState = key(state.account?.key, state.traceJob?.id) { rememberLazyListState() }
    GithubDetailScaffold(title = state.traceJob?.name ?: stringResource(R.string.gitlab_pipeline_heading, pipelineId), subtitle = state.account?.instanceUrl,
        backContentDescription = stringResource(R.string.github_back), onBack = back, showGithubServiceStatus = false,
        actions = { IconButton(onClick = { onAction(GitLabPipelineAction.Refresh) }, enabled = state.account != null && !state.writing && !state.loading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.gitlab_refresh))
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = if (state.traceJob == null) jobsListState else traceListState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (state.traceJob == null) 12.dp else 0.dp)) {
            if (state.account == null) item {
                GithubMessageState(stringResource(R.string.gitlab_account_required), actionLabel = stringResource(R.string.gitlab_accounts), onAction = onAccounts)
            } else {
                if (state.loading || state.writing || state.traceLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.writeError != null || state.writeSucceeded) item {
                    GitLabWriteFeedback(state.writeError, state.writeSucceeded, { onAction(GitLabPipelineAction.Refresh) }, onAccounts)
                }
                state.error?.let { error -> item {
                    Column {
                        GithubMessageState(gitLabWorkError(error), actionLabel = stringResource(R.string.github_retry), onAction = { onAction(GitLabPipelineAction.Refresh) })
                        GitLabAccessNotice(error, onAccounts)
                    }
                } }
                state.pipeline?.let { pipeline ->
                    if (state.traceJob == null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pipeline.ref, style = MaterialTheme.typography.titleLarge)
                            Text(gitLabStatus(pipeline.status), color = MaterialTheme.colorScheme.primary)
                            GitLabSelectableBody(pipeline.sha)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pipeline.canRetry) GitLabRunButton(GitLabRunAction.RETRY, gitLabOperationTarget(state.account, projectId, "#$pipelineId\n${pipeline.ref}"),
                                    enabled = !state.writing && !state.loading, onConfirm = { onAction(GitLabPipelineAction.Run(pipeline, GitLabRunAction.RETRY)) })
                                if (pipeline.canCancel) GitLabRunButton(GitLabRunAction.CANCEL, gitLabOperationTarget(state.account, projectId, "#$pipelineId\n${pipeline.ref}"),
                                    enabled = !state.writing && !state.loading, onConfirm = { onAction(GitLabPipelineAction.Run(pipeline, GitLabRunAction.CANCEL)) })
                            }
                        }
                    }
                    if (state.traceJob != null) {
                        item {
                            Column(Modifier.padding(bottom = 12.dp)) {
                                Text(state.traceJob.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.gitlab_log_hint), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onAction(GitLabPipelineAction.CloseTrace) }) { Text(stringResource(R.string.gitlab_back_to_jobs)) }
                                state.traceError?.let { error ->
                                    GithubMessageState(gitLabWorkError(error), actionLabel = stringResource(R.string.github_retry),
                                        onAction = { onAction(GitLabPipelineAction.Trace(state.traceJob)) })
                                    GitLabAccessNotice(error, onAccounts)
                                }
                                if (lines.size > 5000 || lines.any { it.length > 20_000 }) Text(stringResource(R.string.gitlab_preview_truncated))
                            }
                        }
                        itemsIndexed(lines.take(5000), key = { index, _ -> "trace:${state.traceJob.id}:$index" }) { _, line ->
                            SelectionContainer {
                                Text(line.take(20_000).ifEmpty { " " }, Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, softWrap = true)
                            }
                        }
                    } else {
                        items(state.jobs, key = { it.id }) { job ->
                            Surface(onClick = { onAction(GitLabPipelineAction.Trace(job)) }, enabled = !state.writing,
                                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(job.name, style = MaterialTheme.typography.titleMedium)
                                        Text(job.stage, style = MaterialTheme.typography.labelMedium)
                                        Text(gitLabStatus(job.status), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        Text(stringResource(R.string.gitlab_view_log), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    GitLabJobMenu(job, gitLabOperationTarget(state.account, projectId, "#$pipelineId\n${job.name} · #${job.id}"),
                                        !state.writing && !state.loading, onAction)
                                    Icon(Icons.Default.ChevronRight, null, Modifier.padding(end = 12.dp))
                                }
                            }
                        }
                        item {
                            Column {
                                GithubPagedListStatus(state.jobs.size, state.jobsLoading && state.jobs.isEmpty(), state.jobsLoading && state.jobs.isNotEmpty(),
                                    state.jobsError != null, state.nextPage != null && !state.writing, gitLabWorkError(state.jobsError), stringResource(R.string.gitlab_no_jobs),
                                    { onAction(GitLabPipelineAction.Jobs(state.jobs.isNotEmpty() && state.nextPage != null)) }, { onAction(GitLabPipelineAction.Jobs(true)) })
                                if (state.jobsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                GitLabAccessNotice(state.jobsError, onAccounts)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitLabJobMenu(job: GitLabJob, subject: String, enabled: Boolean, onAction: (GitLabPipelineAction) -> Unit) {
    var expanded by remember(job.id) { mutableStateOf(false) }
    val actions = listOfNotNull(
        GitLabRunAction.PLAY.takeIf { job.canPlay }, GitLabRunAction.RETRY.takeIf { job.canRetry }, GitLabRunAction.CANCEL.takeIf { job.canCancel }
    )
    // Confirmation lives outside the popup so dismissing the menu cannot dismiss the dialog.
    val requests = actions.associateWith { action ->
        val title = gitLabRunActionTitle(action)
        githubConfirmedAction(title, stringResource(R.string.gitlab_run_action_warning), subject, enabled) {
            onAction(GitLabPipelineAction.Job(job, action))
        }
    }
    if (actions.isNotEmpty()) Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.github_more_actions))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(gitLabRunActionTitle(action)) }, enabled = enabled,
                    onClick = { expanded = false; requests.getValue(action)() })
            }
        }
    }
}

@Composable
private fun gitLabRunActionTitle(action: GitLabRunAction): String = stringResource(when (action) {
    GitLabRunAction.RETRY -> R.string.gitlab_retry_run
    GitLabRunAction.CANCEL -> R.string.gitlab_cancel_run
    GitLabRunAction.PLAY -> R.string.gitlab_play_job
})

@Composable
private fun GitLabRunButton(action: GitLabRunAction, subject: String, enabled: Boolean, onConfirm: () -> Unit) {
    val title = gitLabRunActionTitle(action)
    val request = githubConfirmedAction(title, stringResource(R.string.gitlab_run_action_warning), subject, enabled, onConfirm)
    TextButton(onClick = request, enabled = enabled) { Text(title) }
}
