package takagi.ru.monica.github.feature.actions

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.domain.GithubArtifactOutput
import takagi.ru.monica.github.domain.GithubActionKind
import takagi.ru.monica.github.domain.GithubActionOperation
import takagi.ru.monica.github.domain.GithubWorkflow
import takagi.ru.monica.github.domain.GithubWorkflowArtifact
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
    val savedPages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var dispatchTargetId by remember(state.fullName, state.viewerAccountId, state.canManageWorkflows) { mutableStateOf<Long?>(null) }
    val dispatchTarget = state.items.firstOrNull { it.id == dispatchTargetId }
    if (dispatchTarget != null && state.canManageWorkflows) {
        savedPages.SaveableStateProvider("dispatch:${state.fullName}:${state.viewerAccountId}:${dispatchTarget.id}") {
            var ref by rememberSaveable { mutableStateOf("main") }
            var inputs by rememberSaveable { mutableStateOf("") }
            WorkflowDispatchScreen(state.fullName, dispatchTarget, ref, inputs, { ref = it }, { inputs = it },
                dispatchTarget.id in state.dispatchBusyIds, onDismiss = { dispatchTargetId = null },
                onSubmit = { requestedRef, values, repeatConfirmed ->
                    dispatchTargetId = null
                    onAction(ActionsWorkflowsAction.DispatchWorkflow(dispatchTarget.id, requestedRef, values, state.viewerAccountId, repeatConfirmed))
                }, unconfirmed = GithubActionOperation(GithubActionKind.DISPATCH, dispatchTarget.id) in state.unconfirmedOperations,
                viewerAccountId = state.viewerAccountId,
                onCheck = { dispatchTargetId = null; onOpenWorkflow(dispatchTarget) },
                onAcknowledge = { onAction(ActionsWorkflowsAction.Acknowledge(GithubActionOperation(GithubActionKind.DISPATCH, dispatchTarget.id), state.viewerAccountId)) })
        }
        return
    }
    savedPages.SaveableStateProvider("workflowList:${state.fullName}:${state.viewerAccountId}") {
        GithubDetailScaffold(
            contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
            title = state.name,
            subtitle = stringResource(R.string.github_actions),
            backContentDescription = stringResource(R.string.github_back),
            onBack = onBack,
            modifier = modifier,
            actions = {
                IconButton(
                    onClick = { onAction(ActionsWorkflowsAction.Refresh) },
                    enabled = !state.isRefreshing && !state.isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
                }
                GithubOpenOnGithubButton {
                    onOpenExternal(GithubWebUrls.actions(state.fullName))
                }
            }
        ) { padding ->
            GithubPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(ActionsWorkflowsAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoading && !state.isLoadingMore,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                GithubListLoadingState(
                    isLoading = state.isLoading,
                    hasItems = state.items.isNotEmpty(),
                    row = GithubSkeletonRow.LIST,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                GithubAdaptiveGrid(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (state.journalUnavailable) githubFullSpanItem(key = "journal-error") {
                        GithubMessageState(title = stringResource(R.string.github_actions_journal_error),
                            actionLabel = stringResource(R.string.github_retry), onAction = { onAction(ActionsWorkflowsAction.Refresh) })
                    }
                    state.unconfirmedOperations.filter { operation ->
                        operation.kind in setOf(GithubActionKind.TOGGLE, GithubActionKind.DISPATCH) && operation.targetId !in state.workflowBusyIds && operation.targetId !in state.dispatchBusyIds
                    }.forEach { operation ->
                        githubFullSpanItem(key = "uncertain:${operation.kind}:${operation.targetId}") {
                            val workflow = state.items.firstOrNull { it.id == operation.targetId }
                            val label = stringResource(if (operation.kind == GithubActionKind.DISPATCH) R.string.github_actions_dispatch else R.string.github_actions_toggle_workflow, workflow?.name ?: "#${operation.targetId}")
                            ActionsOutcomeNotice("${state.fullName} · ${workflow?.name ?: "#${operation.targetId}"} · $label",
                                onCheck = { if (workflow != null) onOpenWorkflow(workflow) else onOpenExternal(GithubWebUrls.actions(state.fullName)) },
                                onAcknowledge = { onAction(ActionsWorkflowsAction.Acknowledge(operation, state.viewerAccountId)) })
                        }
                    }
                    items(state.items, key = GithubWorkflow::id) { workflow ->
                        ActionsWorkflowRow(
                            workflow = workflow,
                            onClick = { onOpenWorkflow(workflow) },
                            canManage = state.canManageWorkflows && state.journalReady,
                            isUpdating = workflow.id in state.workflowBusyIds,
                            hasError = workflow.id in state.workflowErrorIds,
                            isDispatching = workflow.id in state.dispatchBusyIds,
                            hasDispatchError = workflow.id in state.dispatchErrorIds,
                            onEnabledChanged = { enabled ->
                                onAction(ActionsWorkflowsAction.SetWorkflowEnabled(workflow.id, enabled, state.viewerAccountId,
                                    GithubActionOperation(GithubActionKind.TOGGLE, workflow.id) in state.unconfirmedOperations))
                            },
                            onRequestDispatch = { dispatchTargetId = workflow.id },
                            viewerAccountId = state.viewerAccountId,
                            uncertainToggle = GithubActionOperation(GithubActionKind.TOGGLE, workflow.id) in state.unconfirmedOperations
                        )
                    }
                    githubFullSpanItem(key = "list-status") {
                        GithubPagedListStatus(
                            itemCount = state.items.size,
                            isInitialLoading = state.isLoading,
                            isLoadingMore = state.isLoadingMore,
                            hasError = state.error,
                            canLoadMore = state.canLoadMore,
                            errorMessage = stringResource(R.string.github_actions_workflows_error),
                            emptyMessage = stringResource(R.string.github_no_workflows),
                            onRetry = { onAction(ActionsWorkflowsAction.Retry) },
                            onLoadMore = { onAction(ActionsWorkflowsAction.LoadMore) },
                            emptyIcon = Icons.Default.PlayArrow
                        )
                    }
                }
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
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = state.workflowName.ifBlank { stringResource(R.string.github_unnamed_workflow) },
        subtitle = stringResource(R.string.github_workflow_runs),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { onAction(WorkflowRunsAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.actions(state.fullName))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(WorkflowRunsAction.Refresh) },
            enabled = !state.isRefreshing && !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.items.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.items, key = GithubWorkflowRun::id) { run ->
                    WorkflowRunRow(run = run, onClick = { onOpenRun(run) })
                }
                githubFullSpanItem(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_actions_runs_error),
                        emptyMessage = stringResource(R.string.github_no_workflow_runs),
                        onRetry = { onAction(WorkflowRunsAction.Retry) },
                        onLoadMore = { onAction(WorkflowRunsAction.LoadMore) },
                        emptyIcon = Icons.Default.PlayArrow
                    )
                }
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
    enableArtifactDownloads: Boolean = true,
    modifier: Modifier = Modifier
) {
    val run = state.run
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val context = LocalContext.current
    var downloadTarget by rememberSaveable(state.viewerAccountId, state.runId) { mutableStateOf(0L) }
    val artifactPicker = if (enableArtifactDownloads) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            val artifact = state.artifacts.firstOrNull { it.id == downloadTarget }
            if (uri != null && artifact != null) {
                onAction(
                    ActionsRunDetailAction.DownloadArtifact(artifact.id) {
                        uri.artifactOutput(context.contentResolver)
                    }
                )
            }
        }
    } else null
    val requestDownload: (GithubWorkflowArtifact) -> Unit = { artifact ->
        if (artifactPicker != null) {
            downloadTarget = artifact.id
            artifactPicker.launch(artifact.zipFileName())
        }
    }
    GithubDetailScaffold(
        title = run?.let { stringResource(R.string.github_run_number, it.runNumber) } ?: "#${state.runId}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(run?.htmlUrl ?: GithubWebUrls.actionsRun(state.fullName, state.runId))
            }
        }
    ) { padding ->
        when {
            run == null && state.isLoadingRun -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                ActionsRunOutcome(state, onAction)
                CircularProgressIndicator()
            }
            run == null && state.runError -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                ActionsRunOutcome(state, onAction)
                GithubMessageState(
                title = stringResource(R.string.github_actions_run_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(ActionsRunDetailAction.RetryRun) },
                modifier = Modifier.fillMaxWidth()
            )
            }
            run != null -> BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth * fontScale) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.width(380.dp * fontScale).fillMaxHeight(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item(key = "summary") {
                                ActionsRunOutcome(state, onAction)
                                ActionsRunSummaryCard(
                                    run = run,
                                    onAction = { action -> onAction(ActionsRunDetailAction.PerformRunAction(action, state.viewerAccountId, state.unconfirmedAction)) },
                                    canManage = state.canManageRun && state.journalReady,
                                    isPerformingAction = state.isPerformingAction,
                                    actionError = state.actionError,
                                    viewerAccountId = state.viewerAccountId,
                                    unconfirmed = state.unconfirmedAction
                                )
                            }
                            actionsArtifacts(state, onAction, requestDownload)
                        }
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        ActionsJobsList(
                            state = state,
                            onAction = onAction,
                            onOpenJob = onOpenJob,
                            onRequestDownload = requestDownload,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            includeSummary = false
                        )
                    }
                } else {
                    ActionsJobsList(
                        state = state,
                        onAction = onAction,
                        onOpenJob = onOpenJob,
                        onRequestDownload = requestDownload,
                        modifier = Modifier.fillMaxSize(),
                        includeSummary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsRunOutcome(state: ActionsRunDetailUiState, onAction: (ActionsRunDetailAction) -> Unit) {
    if (state.journalUnavailable) Text(stringResource(R.string.github_actions_journal_error), color = MaterialTheme.colorScheme.error)
    if (state.unconfirmedAction && !state.isPerformingAction) ActionsOutcomeNotice("${state.fullName} · #${state.runId}",
        onCheck = { onAction(ActionsRunDetailAction.RetryRun) },
        onAcknowledge = { onAction(ActionsRunDetailAction.Acknowledge(state.viewerAccountId)) })
}

@Composable
private fun ActionsJobsList(
    state: ActionsRunDetailUiState,
    onAction: (ActionsRunDetailAction) -> Unit,
    onOpenJob: (GithubWorkflowJob) -> Unit,
    onRequestDownload: (GithubWorkflowArtifact) -> Unit,
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
                    ActionsRunOutcome(state, onAction)
                    ActionsRunSummaryCard(
                        run = run,
                        onAction = { action -> onAction(ActionsRunDetailAction.PerformRunAction(action, state.viewerAccountId, state.unconfirmedAction)) },
                        canManage = state.canManageRun && state.journalReady,
                        isPerformingAction = state.isPerformingAction,
                        actionError = state.actionError,
                        viewerAccountId = state.viewerAccountId,
                                    unconfirmed = state.unconfirmedAction
                    )
                }
            }
            actionsArtifacts(state, onAction, onRequestDownload)
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
                onLoadMore = { onAction(ActionsRunDetailAction.LoadMoreJobs) },
                compact = true
            )
        }
    }
}

private fun LazyListScope.actionsArtifacts(
    state: ActionsRunDetailUiState,
    onAction: (ActionsRunDetailAction) -> Unit,
    onRequestDownload: (GithubWorkflowArtifact) -> Unit
) {
    item(key = "artifacts-title") {
        GithubSectionHeader(title = stringResource(R.string.github_artifacts))
    }
    if (state.isLoadingArtifacts && state.artifacts.isEmpty()) {
        item(key = "artifacts-loading") { GithubCenteredProgress() }
    }
    if (state.artifactDownloadFailed) {
        item(key = "artifacts-download-error") {
            Text(
                text = stringResource(R.string.github_actions_artifact_download_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
    items(state.artifacts, key = GithubWorkflowArtifact::id) { artifact ->
        ActionsArtifactRow(
            artifact = artifact,
            isDownloading = state.downloadingArtifactId == artifact.id,
            isBusy = state.isDownloadingArtifact,
            onDownload = { onRequestDownload(artifact) }
        )
    }
    item(key = "artifacts-status") {
        GithubPagedListStatus(
            itemCount = state.artifacts.size,
            isInitialLoading = state.isLoadingArtifacts,
            isLoadingMore = state.isLoadingMoreArtifacts,
            hasError = state.artifactsError,
            canLoadMore = state.canLoadMoreArtifacts,
            errorMessage = stringResource(R.string.github_actions_artifacts_error),
            emptyMessage = stringResource(R.string.github_no_artifacts),
            onRetry = { onAction(ActionsRunDetailAction.RetryArtifacts) },
            onLoadMore = { onAction(ActionsRunDetailAction.LoadMoreArtifacts) },
            compact = true
        )
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
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    GithubDetailScaffold(
        title = job?.name ?: "#${state.jobId}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(job?.htmlUrl ?: GithubWebUrls.actions(state.fullName))
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth * fontScale) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ActionsJobSummaryList(
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.width(400.dp * fontScale).fillMaxHeight()
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

/** Artifact names are workflow input, so the suggested file name drops characters file systems reject. */
private fun GithubWorkflowArtifact.zipFileName(): String {
    val safeName = name.map {
        if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '-'
    }.joinToString("").ifBlank { "artifact" }
    return "$safeName.zip"
}

private fun Uri.artifactOutput(resolver: ContentResolver): GithubArtifactOutput {
    val stream = resolver.openOutputStream(this) ?: error("Unable to open the selected file")
    return object : GithubArtifactOutput {
        override fun write(source: ByteArray, offset: Int, count: Int) = stream.write(source, offset, count)
        override fun close() {
            stream.close()
        }
    }
}
