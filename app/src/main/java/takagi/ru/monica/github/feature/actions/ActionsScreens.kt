package takagi.ru.monica.github.feature.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowJob
import takagi.ru.monica.github.domain.GithubWorkflowRun
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun ActionsWorkflowsScreen(
    state: ActionsWorkflowsUiState,
    onAction: (ActionsWorkflowsAction) -> Unit,
    onBack: () -> Unit,
    onOpenWorkflow: (GithubWorkflow) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = state.name,
        subtitle = stringResource(R.string.github_actions),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.actions(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.items, key = GithubWorkflow::id) { workflow ->
                    ActionsWorkflowRow(
                        workflow = workflow,
                        onClick = { onOpenWorkflow(workflow) },
                        isUpdating = workflow.id in state.workflowBusyIds,
                        hasError = workflow.id in state.workflowErrorIds,
                        isDispatching = workflow.id in state.dispatchBusyIds,
                        hasDispatchError = workflow.id in state.dispatchErrorIds,
                        onEnabledChanged = { enabled ->
                            onAction(ActionsWorkflowsAction.SetWorkflowEnabled(workflow.id, enabled))
                        },
                        onDispatch = { ref, inputs ->
                            onAction(ActionsWorkflowsAction.DispatchWorkflow(workflow.id, ref, inputs))
                        }
                    )
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_actions_workflows_error),
                        emptyMessage = stringResource(R.string.github_no_workflows),
                        onRetry = { onAction(ActionsWorkflowsAction.Retry) },
                        onLoadMore = { onAction(ActionsWorkflowsAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkflowRunsScreen(
    state: WorkflowRunsUiState,
    onAction: (WorkflowRunsAction) -> Unit,
    onBack: () -> Unit,
    onOpenRun: (GithubWorkflowRun) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = state.workflowName.ifBlank { stringResource(R.string.github_unnamed_workflow) },
        subtitle = stringResource(R.string.github_workflow_runs),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.actions(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.items, key = GithubWorkflowRun::id) { run ->
                    WorkflowRunRow(run = run, onClick = { onOpenRun(run) })
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_actions_runs_error),
                        emptyMessage = stringResource(R.string.github_no_workflow_runs),
                        onRetry = { onAction(WorkflowRunsAction.Retry) },
                        onLoadMore = { onAction(WorkflowRunsAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsRunDetailScreen(
    state: ActionsRunDetailUiState,
    onAction: (ActionsRunDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenJob: (GithubWorkflowJob) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val run = state.run
    GithubDetailScaffold(
        title = run?.let { stringResource(R.string.github_run_number, it.runNumber) } ?: "#${state.runId}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(run?.htmlUrl ?: GithubWebUrls.actionsRun(state.fullName, state.runId))
            }
        }
    ) { padding ->
        when {
            run == null && state.isLoadingRun -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            run == null && state.runError -> GithubMessageState(
                title = stringResource(R.string.github_actions_run_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(ActionsRunDetailAction.RetryRun) },
                modifier = Modifier.padding(padding).padding(horizontal = 20.dp)
            )
            run != null -> BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.width(380.dp).fillMaxHeight(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item(key = "summary") {
                                ActionsRunSummaryCard(
                                    run = run,
                                    onAction = { action -> onAction(ActionsRunDetailAction.PerformRunAction(action)) },
                                    isPerformingAction = state.isPerformingAction,
                                    actionError = state.actionError
                                )
                            }
                        }
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        ActionsJobsList(
                            state = state,
                            onAction = onAction,
                            onOpenJob = onOpenJob,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            includeSummary = false
                        )
                    }
                } else {
                    ActionsJobsList(
                        state = state,
                        onAction = onAction,
                        onOpenJob = onOpenJob,
                        modifier = Modifier.fillMaxSize(),
                        includeSummary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsJobsList(
    state: ActionsRunDetailUiState,
    onAction: (ActionsRunDetailAction) -> Unit,
    onOpenJob: (GithubWorkflowJob) -> Unit,
    modifier: Modifier,
    includeSummary: Boolean
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (includeSummary) {
            state.run?.let { run ->
                item(key = "summary") {
                    ActionsRunSummaryCard(
                        run = run,
                        onAction = { action -> onAction(ActionsRunDetailAction.PerformRunAction(action)) },
                        isPerformingAction = state.isPerformingAction,
                        actionError = state.actionError
                    )
                }
            }
        }
        item(key = "jobs-title") {
            GithubSectionHeader(title = stringResource(R.string.github_jobs))
        }
        if (state.isLoadingJobs && state.jobs.isEmpty()) {
            item(key = "jobs-loading") { GithubCenteredProgress() }
        }
        items(state.jobs, key = GithubWorkflowJob::id) { job ->
            ActionsJobRow(job = job, onClick = { onOpenJob(job) })
        }
        item(key = "jobs-status") {
            GithubPagedListStatus(
                itemCount = state.jobs.size,
                isInitialLoading = state.isLoadingJobs,
                isLoadingMore = state.isLoadingMoreJobs,
                hasError = state.jobsError,
                canLoadMore = state.canLoadMoreJobs,
                errorMessage = stringResource(R.string.github_actions_jobs_error),
                emptyMessage = stringResource(R.string.github_no_jobs),
                onRetry = { onAction(ActionsRunDetailAction.RetryJobs) },
                onLoadMore = { onAction(ActionsRunDetailAction.LoadMoreJobs) }
            )
        }
    }
}

@Composable
fun ActionsJobDetailScreen(
    state: ActionsJobDetailUiState,
    onAction: (ActionsJobDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val job = state.job
    GithubDetailScaffold(
        title = job?.name ?: "#${state.jobId}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(job?.htmlUrl ?: GithubWebUrls.actions(state.fullName))
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ActionsJobSummaryList(
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.width(400.dp).fillMaxHeight()
                    )
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    ActionsJobLogList(
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item(key = "summary") {
                        ActionsJobSummaryState(state = state, onAction = onAction)
                    }
                    item(key = "log") {
                        ActionsJobLogState(state = state, onAction = onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsJobSummaryList(
    state: ActionsJobDetailUiState,
    onAction: (ActionsJobDetailAction) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        item(key = "summary") { ActionsJobSummaryState(state, onAction) }
    }
}

@Composable
private fun ActionsJobLogList(
    state: ActionsJobDetailUiState,
    onAction: (ActionsJobDetailAction) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    ) {
        item(key = "log") { ActionsJobLogState(state, onAction) }
    }
}

@Composable
private fun ActionsJobSummaryState(
    state: ActionsJobDetailUiState,
    onAction: (ActionsJobDetailAction) -> Unit
) {
    when {
        state.job != null -> ActionsJobSummaryCard(state.job)
        state.isLoadingJob -> GithubCenteredProgress()
        state.jobError -> GithubMessageState(
            title = stringResource(R.string.github_actions_job_error),
            color = MaterialTheme.colorScheme.error,
            actionLabel = stringResource(R.string.github_retry),
            onAction = { onAction(ActionsJobDetailAction.RetryJob) }
        )
    }
}

@Composable
private fun ActionsJobLogState(
    state: ActionsJobDetailUiState,
    onAction: (ActionsJobDetailAction) -> Unit
) {
    when {
        state.log != null -> ActionsLogPanel(state.log)
        state.isLoadingLog -> GithubCenteredProgress()
        state.logError -> GithubMessageState(
            title = stringResource(R.string.github_actions_log_error),
            color = MaterialTheme.colorScheme.error,
            actionLabel = stringResource(R.string.github_retry),
            onAction = { onAction(ActionsJobDetailAction.RetryLog) }
        )
    }
}
