package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubAssigneesEditorSheet
import takagi.ru.monica.github.component.GithubCommentCard
import takagi.ru.monica.github.component.GithubCommentComposer
import takagi.ru.monica.github.component.GithubConversationContentEditor
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubLabelsEditorSheet
import takagi.ru.monica.github.component.GithubListFilterSection
import takagi.ru.monica.github.component.GithubListOrderingSheet
import takagi.ru.monica.github.component.GithubListSearchField
import takagi.ru.monica.github.component.GithubMilestoneEditorSheet
import takagi.ru.monica.github.component.GithubReviewersEditorSheet
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestDraft
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.navigation.GithubWebUrls

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PullRequestsScreen(
    state: PullRequestsUiState,
    onAction: (PullRequestsAction) -> Unit,
    onBack: () -> Unit,
    onOpenPullRequest: (GithubPullRequest) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val states = GithubPullRequestState.entries
    val draftFilters = PullRequestDraftFilter.entries
    var orderingOpen by remember { mutableStateOf(false) }
    GithubDetailScaffold(
        title = state.name,
        subtitle = stringResource(R.string.github_pull_requests),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.pullRequests(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GithubFilterRow(
                labels = listOf(
                    stringResource(R.string.github_pr_open),
                    stringResource(R.string.github_pr_closed)
                ),
                selectedIndex = states.indexOf(state.selectedState),
                onSelected = { onAction(PullRequestsAction.SelectState(states[it])) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            GithubListSearchField(
                value = state.searchQuery,
                onValueChange = { onAction(PullRequestsAction.SearchChanged(it)) },
                label = stringResource(R.string.github_search_loaded_pull_requests),
                clearContentDescription = stringResource(R.string.github_clear_search),
                orderingContentDescription = stringResource(R.string.github_list_sort_and_filter),
                onOpenOrdering = { orderingOpen = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.visibleItems.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.visibleItems, key = GithubPullRequest::id) { pullRequest ->
                    PullRequestListRow(
                        pullRequest = pullRequest,
                        onClick = { onOpenPullRequest(pullRequest) },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.visibleItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_pr_list_error),
                        emptyMessage = stringResource(
                            if (state.hasLocalFilters) {
                                R.string.github_no_loaded_pull_requests_match
                            } else {
                                R.string.github_no_pull_requests
                            }
                        ),
                        onRetry = { onAction(PullRequestsAction.Retry) },
                        emptyIcon = Icons.AutoMirrored.Filled.CallSplit,
                        onLoadMore = { onAction(PullRequestsAction.LoadMore) }
                    )
                }
            }
        }
    }
    if (orderingOpen) {
        GithubListOrderingSheet(
            title = stringResource(R.string.github_sort_pull_requests),
            subtitle = stringResource(R.string.github_list_sort_subtitle),
            sort = state.sort,
            direction = state.direction,
            onSelectOrdering = { sort, direction ->
                orderingOpen = false
                onAction(PullRequestsAction.SelectOrdering(sort, direction))
            },
            onDismissRequest = { orderingOpen = false },
            filterContent = {
                GithubListFilterSection(
                    title = stringResource(R.string.github_pr_readiness),
                    labels = listOf(
                        stringResource(R.string.github_filter_all),
                        stringResource(R.string.github_pr_ready),
                        stringResource(R.string.github_pr_draft)
                    ),
                    selectedIndex = draftFilters.indexOf(state.draftFilter),
                    onSelected = { index ->
                        orderingOpen = false
                        onAction(PullRequestsAction.SelectDraftFilter(draftFilters[index]))
                    }
                )
            }
        )
    }
}

@Composable
fun PullRequestDetailScreen(
    state: PullRequestDetailUiState,
    onAction: (PullRequestDetailAction) -> Unit,
    onBack: () -> Unit,
    canWrite: Boolean,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = PullRequestSection.entries
    val sectionStateHolder = rememberSaveableStateHolder()
    var contentEditorOpen by remember { mutableStateOf(false) }
    var managementOpen by remember { mutableStateOf(false) }
    var labelsOpen by remember { mutableStateOf(false) }
    var selectedLabels by remember { mutableStateOf(emptySet<String>()) }
    var labelSaveRequested by remember { mutableStateOf(false) }
    var assigneesOpen by remember { mutableStateOf(false) }
    var selectedAssignees by remember { mutableStateOf(emptySet<String>()) }
    var assigneeSaveRequested by remember { mutableStateOf(false) }
    var milestonesOpen by remember { mutableStateOf(false) }
    var selectedMilestone by remember { mutableStateOf<Int?>(null) }
    var milestoneSaveRequested by remember { mutableStateOf(false) }
    var reviewersOpen by remember { mutableStateOf(false) }
    var selectedReviewers by remember { mutableStateOf(emptySet<String>()) }
    var reviewerSaveRequested by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }
    var contentSaveRequested by remember { mutableStateOf(false) }
    var stateActionRequested by remember { mutableStateOf(false) }
    var lockActionRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.isUpdatingContent, state.contentUpdateError, contentSaveRequested) {
        if (contentSaveRequested && !state.isUpdatingContent) {
            if (!state.contentUpdateError && !state.contentValidationError) contentEditorOpen = false
            contentSaveRequested = false
        }
    }
    LaunchedEffect(state.isUpdatingState, state.stateUpdateError, stateActionRequested) {
        if (stateActionRequested && !state.isUpdatingState) {
            if (!state.stateUpdateError) managementOpen = false
            stateActionRequested = false
        }
    }
    LaunchedEffect(state.isUpdatingLock, state.lockUpdateError, lockActionRequested) {
        if (lockActionRequested && !state.isUpdatingLock) {
            if (!state.lockUpdateError) managementOpen = false
            lockActionRequested = false
        }
    }
    LaunchedEffect(state.isUpdatingLabels, state.labelsUpdateError, labelSaveRequested) {
        if (labelSaveRequested && !state.isUpdatingLabels) {
            if (!state.labelsUpdateError) labelsOpen = false
            labelSaveRequested = false
        }
    }
    LaunchedEffect(state.isUpdatingAssignees, state.assigneesUpdateError, assigneeSaveRequested) {
        if (assigneeSaveRequested && !state.isUpdatingAssignees) {
            if (!state.assigneesUpdateError) assigneesOpen = false
            assigneeSaveRequested = false
        }
    }
    LaunchedEffect(state.isUpdatingMilestone, state.milestoneUpdateError, milestoneSaveRequested) {
        if (milestoneSaveRequested && !state.isUpdatingMilestone) {
            if (!state.milestoneUpdateError) milestonesOpen = false
            milestoneSaveRequested = false
        }
    }
    LaunchedEffect(
        state.isUpdatingReviewers,
        state.reviewersValidationError,
        state.reviewersUpdateError,
        reviewerSaveRequested
    ) {
        if (reviewerSaveRequested && !state.isUpdatingReviewers) {
            if (!state.reviewersValidationError && !state.reviewersUpdateError) reviewersOpen = false
            reviewerSaveRequested = false
        }
    }
    GithubDetailScaffold(
        title = "#${state.number}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (canWrite && state.pullRequest != null) {
                IconButton(
                    onClick = {
                        managementOpen = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.github_manage_pr)
                    )
                }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.pullRequest(state.fullName, state.number))
            }
        }
    ) { padding ->
        val pullRequest = state.pullRequest
        when {
            pullRequest == null && state.isLoadingPullRequest -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            pullRequest == null && state.pullRequestError -> GithubMessageState(
                title = stringResource(R.string.github_pr_load_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(PullRequestDetailAction.RetryPullRequest) },
                modifier = Modifier.padding(padding).padding(horizontal = 20.dp)
            )
            pullRequest != null -> BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (
                    maxWidth >= GithubAdaptiveLayout.detailTwoPaneWidth &&
                    state.selectedSection != PullRequestSection.OVERVIEW
                ) {
                    PullRequestDetailExpanded(
                        state = state,
                        pullRequest = pullRequest,
                        sections = sections,
                        sectionStateHolder = sectionStateHolder,
                        canWrite = canWrite,
                        onAction = onAction,
                        onSignIn = onSignIn,
                        onOpenExternal = onOpenExternal
                    )
                } else {
                    PullRequestDetailCompact(
                        state = state,
                        pullRequest = pullRequest,
                        sections = sections,
                        sectionStateHolder = sectionStateHolder,
                        canWrite = canWrite,
                        onAction = onAction,
                        onSignIn = onSignIn,
                        onOpenExternal = onOpenExternal
                    )
                }
            }
        }
    }
    val managedPullRequest = state.pullRequest
    if (managementOpen && managedPullRequest != null) {
        PullRequestManagementSheet(
            pullRequest = managedPullRequest,
            isBusy = state.isUpdatingState || state.isUpdatingLock,
            hasError = state.stateUpdateError || state.lockUpdateError,
            onEditContent = {
                managementOpen = false
                editTitle = managedPullRequest.title
                editBody = managedPullRequest.body.orEmpty()
                contentEditorOpen = true
            },
            onEditLabels = {
                managementOpen = false
                selectedLabels = managedPullRequest.labels.map(GithubIssueLabel::name).toSet()
                labelsOpen = true
                onAction(PullRequestDetailAction.LoadLabels)
            },
            onEditAssignees = {
                managementOpen = false
                selectedAssignees = managedPullRequest.assignees.map(GithubUserSummary::login).toSet()
                assigneesOpen = true
                onAction(PullRequestDetailAction.LoadAssignees)
            },
            onEditMilestone = {
                managementOpen = false
                selectedMilestone = managedPullRequest.milestone?.number
                milestonesOpen = true
                onAction(PullRequestDetailAction.LoadMilestones)
            },
            onEditReviewers = {
                managementOpen = false
                selectedReviewers = managedPullRequest.requestedReviewers.map(GithubUserSummary::login).toSet()
                reviewersOpen = true
                onAction(PullRequestDetailAction.LoadAssignees)
            },
            onToggleState = {
                stateActionRequested = true
                onAction(PullRequestDetailAction.ToggleState)
            },
            onToggleLock = {
                lockActionRequested = true
                onAction(PullRequestDetailAction.ToggleLock)
            },
            onDismiss = {
                if (!state.isUpdatingState && !state.isUpdatingLock) managementOpen = false
            }
        )
    }
    if (labelsOpen) {
        GithubLabelsEditorSheet(
            labels = state.availableLabels,
            selected = selectedLabels,
            isLoading = state.isLoadingLabels,
            isLoadingMore = state.isLoadingMoreLabels,
            hasError = state.labelsError,
            canLoadMore = state.nextLabelsPage != null,
            isSaving = state.isUpdatingLabels,
            saveError = state.labelsUpdateError,
            saveErrorMessage = stringResource(R.string.github_pr_labels_update_error),
            onToggle = { name ->
                selectedLabels = if (name in selectedLabels) selectedLabels - name else selectedLabels + name
            },
            onLoadMore = { onAction(PullRequestDetailAction.LoadMoreLabels) },
            onRetry = { onAction(PullRequestDetailAction.LoadLabels) },
            onSave = {
                labelSaveRequested = true
                onAction(PullRequestDetailAction.UpdateLabels(selectedLabels.toList()))
            },
            onDismiss = { if (!state.isUpdatingLabels) labelsOpen = false }
        )
    }
    if (assigneesOpen) {
        GithubAssigneesEditorSheet(
            assignees = state.availableAssignees,
            selected = selectedAssignees,
            isLoading = state.isLoadingAssignees,
            isLoadingMore = state.isLoadingMoreAssignees,
            hasError = state.assigneesError,
            canLoadMore = state.nextAssigneesPage != null,
            isSaving = state.isUpdatingAssignees,
            saveError = state.assigneesUpdateError,
            saveErrorMessage = stringResource(R.string.github_pr_assignees_update_error),
            onToggle = { login ->
                selectedAssignees = if (login in selectedAssignees) {
                    selectedAssignees - login
                } else {
                    selectedAssignees + login
                }
            },
            onLoadMore = { onAction(PullRequestDetailAction.LoadMoreAssignees) },
            onRetry = { onAction(PullRequestDetailAction.LoadAssignees) },
            onSave = {
                assigneeSaveRequested = true
                onAction(PullRequestDetailAction.UpdateAssignees(selectedAssignees.toList()))
            },
            onDismiss = { if (!state.isUpdatingAssignees) assigneesOpen = false }
        )
    }
    if (milestonesOpen) {
        GithubMilestoneEditorSheet(
            milestones = state.availableMilestones,
            selected = selectedMilestone,
            isLoading = state.isLoadingMilestones,
            isLoadingMore = state.isLoadingMoreMilestones,
            hasError = state.milestonesError,
            canLoadMore = state.nextMilestonesPage != null,
            isSaving = state.isUpdatingMilestone,
            saveError = state.milestoneUpdateError,
            saveErrorMessage = stringResource(R.string.github_pr_milestone_update_error),
            onSelect = { selectedMilestone = it },
            onLoadMore = { onAction(PullRequestDetailAction.LoadMoreMilestones) },
            onRetry = { onAction(PullRequestDetailAction.LoadMilestones) },
            onSave = {
                milestoneSaveRequested = true
                onAction(PullRequestDetailAction.UpdateMilestone(selectedMilestone))
            },
            onDismiss = { if (!state.isUpdatingMilestone) milestonesOpen = false }
        )
    }
    if (reviewersOpen) {
        val currentPullRequest = state.pullRequest
        val reviewerCandidates = remember(
            state.availableAssignees,
            currentPullRequest?.requestedReviewers,
            currentPullRequest?.author
        ) {
            (state.availableAssignees + currentPullRequest?.requestedReviewers.orEmpty())
                .distinctBy { it.login.lowercase() }
                .filterNot { candidate ->
                    candidate.login.equals(currentPullRequest?.author?.login, ignoreCase = true)
                }
        }
        GithubReviewersEditorSheet(
            users = reviewerCandidates,
            selected = selectedReviewers,
            isLoading = state.isLoadingAssignees,
            isLoadingMore = state.isLoadingMoreAssignees,
            hasError = state.assigneesError,
            canLoadMore = state.nextAssigneesPage != null,
            isSaving = state.isUpdatingReviewers,
            validationError = state.reviewersValidationError,
            saveError = state.reviewersUpdateError,
            onToggle = { login ->
                selectedReviewers = if (login in selectedReviewers) {
                    selectedReviewers - login
                } else {
                    selectedReviewers + login
                }
            },
            onLoadMore = { onAction(PullRequestDetailAction.LoadMoreAssignees) },
            onRetry = { onAction(PullRequestDetailAction.LoadAssignees) },
            onSave = {
                reviewerSaveRequested = true
                onAction(PullRequestDetailAction.UpdateRequestedReviewers(selectedReviewers.toList()))
            },
            onDismiss = { if (!state.isUpdatingReviewers) reviewersOpen = false }
        )
    }
    if (contentEditorOpen) {
        GithubConversationContentEditor(
            sheetTitle = stringResource(R.string.github_edit_pr_content),
            sheetSubtitle = stringResource(R.string.github_edit_pr_content_subtitle),
            title = editTitle,
            body = editBody,
            titleLabel = stringResource(R.string.github_pr_title),
            bodyLabel = stringResource(R.string.github_pr_body),
            validationMessage = stringResource(R.string.github_pr_content_validation_error),
            saveErrorMessage = stringResource(R.string.github_pr_content_update_error),
            maxTitleLength = GithubPullRequestDraft.MAX_TITLE_LENGTH,
            maxBodyLength = GithubPullRequestDraft.MAX_BODY_LENGTH,
            isSaving = state.isUpdatingContent,
            validationError = state.contentValidationError,
            saveError = state.contentUpdateError,
            onTitleChange = { if (it.length <= GithubPullRequestDraft.MAX_TITLE_LENGTH) editTitle = it },
            onBodyChange = { if (it.length <= GithubPullRequestDraft.MAX_BODY_LENGTH) editBody = it },
            onSave = {
                contentSaveRequested = true
                onAction(PullRequestDetailAction.UpdateContent(editTitle, editBody))
            },
            onDismiss = { if (!state.isUpdatingContent) contentEditorOpen = false }
        )
    }
}

@Composable
private fun PullRequestDetailCompact(
    state: PullRequestDetailUiState,
    pullRequest: GithubPullRequest,
    sections: List<PullRequestSection>,
    sectionStateHolder: SaveableStateHolder,
    canWrite: Boolean,
    onAction: (PullRequestDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PullRequestSectionSelector(state, sections, onAction)
        PullRequestSectionProgress(state)
        PullRequestSelectedSection(
            state = state,
            pullRequest = pullRequest,
            sectionStateHolder = sectionStateHolder,
            canWrite = canWrite,
            onAction = onAction,
            onSignIn = onSignIn,
            onOpenExternal = onOpenExternal
        )
    }
}

@Composable
private fun PullRequestDetailExpanded(
    state: PullRequestDetailUiState,
    pullRequest: GithubPullRequest,
    sections: List<PullRequestSection>,
    sectionStateHolder: SaveableStateHolder,
    canWrite: Boolean,
    onAction: (PullRequestDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(380.dp).fillMaxHeight(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item(key = "overview-panel") {
                PullRequestOverviewCard(
                    pullRequest = pullRequest,
                    fullName = state.fullName,
                    onOpenExternal = onOpenExternal
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            PullRequestSectionSelector(state, sections, onAction)
            PullRequestSectionProgress(state)
            PullRequestSelectedSection(
                state = state,
                pullRequest = pullRequest,
                sectionStateHolder = sectionStateHolder,
                canWrite = canWrite,
                onAction = onAction,
                onSignIn = onSignIn,
                onOpenExternal = onOpenExternal
            )
        }
    }
}

@Composable
private fun PullRequestSectionSelector(
    state: PullRequestDetailUiState,
    sections: List<PullRequestSection>,
    onAction: (PullRequestDetailAction) -> Unit
) {
    GithubFilterRow(
        labels = listOf(
            stringResource(R.string.github_pr_overview),
            stringResource(R.string.github_pr_files),
            stringResource(R.string.github_pr_activity)
        ),
        selectedIndex = sections.indexOf(state.selectedSection),
        onSelected = { onAction(PullRequestDetailAction.SelectSection(sections[it])) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PullRequestSectionProgress(state: PullRequestDetailUiState) {
    if (state.selectedSection.isLoading(state)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PullRequestSelectedSection(
    state: PullRequestDetailUiState,
    pullRequest: GithubPullRequest,
    sectionStateHolder: SaveableStateHolder,
    canWrite: Boolean,
    onAction: (PullRequestDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    sectionStateHolder.SaveableStateProvider(state.selectedSection.name) {
        when (state.selectedSection) {
            PullRequestSection.OVERVIEW -> PullRequestOverviewContent(
                pullRequest = pullRequest,
                fullName = state.fullName,
                onOpenExternal = onOpenExternal
            )
            PullRequestSection.FILES -> PullRequestFilesContent(
                state = state,
                onAction = onAction,
                onOpenExternal = onOpenExternal
            )
            PullRequestSection.ACTIVITY -> PullRequestActivityContent(
                state = state,
                pullRequest = pullRequest,
                canWrite = canWrite,
                onAction = onAction,
                onSignIn = onSignIn,
                onOpenExternal = onOpenExternal
            )
        }
    }
}

@Composable
private fun PullRequestOverviewContent(
    pullRequest: GithubPullRequest,
    fullName: String,
    onOpenExternal: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item(key = "overview") {
            PullRequestOverviewCard(
                pullRequest = pullRequest,
                fullName = fullName,
                onOpenExternal = onOpenExternal
            )
        }
    }
}

@Composable
private fun PullRequestFilesContent(
    state: PullRequestDetailUiState,
    onAction: (PullRequestDetailAction) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (state.isLoadingFiles && state.files.isEmpty()) {
            item(key = "files-loading") { GithubCenteredProgress() }
        }
        items(state.files, key = GithubPullRequestFile::filename) { file ->
            PullRequestDiffCard(file = file, onOpenExternal = onOpenExternal)
        }
        item(key = "files-status") {
            GithubPagedListStatus(
                itemCount = state.files.size,
                isInitialLoading = state.isLoadingFiles,
                isLoadingMore = state.isLoadingMoreFiles,
                hasError = state.filesError,
                canLoadMore = state.canLoadMoreFiles,
                errorMessage = stringResource(R.string.github_pr_files_error),
                emptyMessage = stringResource(R.string.github_no_changed_files),
                onRetry = { onAction(PullRequestDetailAction.RetryFiles) },
                onLoadMore = { onAction(PullRequestDetailAction.LoadMoreFiles) },
                compact = true
            )
        }
    }
}

@Composable
private fun PullRequestActivityContent(
    state: PullRequestDetailUiState,
    pullRequest: GithubPullRequest,
    canWrite: Boolean,
    onAction: (PullRequestDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item(key = "reviews-title") {
            GithubSectionHeader(title = stringResource(R.string.github_reviews))
        }
        if (state.isLoadingReviews && state.reviews.isEmpty()) {
            item(key = "reviews-loading") { GithubCenteredProgress() }
        }
        items(state.reviews, key = { "review:${it.id}" }) { review ->
            PullRequestReviewCard(
                review = review,
                fullName = state.fullName,
                ref = pullRequest.head.sha,
                onOpenExternal = onOpenExternal
            )
        }
        item(key = "reviews-status") {
            GithubPagedListStatus(
                itemCount = state.reviews.size,
                isInitialLoading = state.isLoadingReviews,
                isLoadingMore = state.isLoadingMoreReviews,
                hasError = state.reviewsError,
                canLoadMore = state.canLoadMoreReviews,
                errorMessage = stringResource(R.string.github_pr_reviews_error),
                emptyMessage = stringResource(R.string.github_no_reviews),
                onRetry = { onAction(PullRequestDetailAction.RetryReviews) },
                onLoadMore = { onAction(PullRequestDetailAction.LoadMoreReviews) },
                compact = true
            )
        }
        item(key = "review-comments-title") {
            GithubSectionHeader(title = stringResource(R.string.github_review_comments))
        }
        if (state.isLoadingReviewComments && state.reviewComments.isEmpty()) {
            item(key = "review-comments-loading") { GithubCenteredProgress() }
        }
        items(state.reviewComments, key = { "review-comment:${it.id}" }) { comment ->
            PullRequestReviewCommentCard(
                comment = comment,
                fullName = state.fullName,
                ref = pullRequest.head.sha,
                onOpenExternal = onOpenExternal
            )
        }
        item(key = "review-comments-status") {
            GithubPagedListStatus(
                itemCount = state.reviewComments.size,
                isInitialLoading = state.isLoadingReviewComments,
                isLoadingMore = state.isLoadingMoreReviewComments,
                hasError = state.reviewCommentsError,
                canLoadMore = state.canLoadMoreReviewComments,
                errorMessage = stringResource(R.string.github_pr_review_comments_error),
                emptyMessage = stringResource(R.string.github_no_review_comments),
                onRetry = { onAction(PullRequestDetailAction.RetryReviewComments) },
                onLoadMore = { onAction(PullRequestDetailAction.LoadMoreReviewComments) },
                compact = true
            )
        }
        item(key = "review-composer") {
            PullRequestReviewComposer(
                body = state.reviewBody,
                canWrite = canWrite,
                isValidationError = state.reviewValidationError,
                isSubmitError = state.reviewSubmitError,
                isSubmitting = state.isSubmittingReview,
                onBodyChanged = { onAction(PullRequestDetailAction.ReviewBodyChanged(it)) },
                onSubmit = { onAction(PullRequestDetailAction.SubmitReview(it)) },
                onSignIn = onSignIn
            )
        }

        item(key = "conversation-title") {
            GithubSectionHeader(title = stringResource(R.string.github_conversation))
        }
        if (state.isLoadingComments && state.comments.isEmpty()) {
            item(key = "comments-loading") { GithubCenteredProgress() }
        }
        items(state.comments, key = { "conversation-comment:${it.id}" }) { comment ->
            GithubCommentCard(
                comment = comment,
                fullName = state.fullName,
                ref = pullRequest.head.sha,
                onOpenExternal = onOpenExternal,
                activeReactions = state.activeReactions[comment.id].orEmpty(),
                isReactionUpdating = comment.id in state.reactionBusyCommentIds,
                hasReactionError = comment.id in state.reactionErrorCommentIds,
                canReact = canWrite,
                onReaction = { reaction ->
                    onAction(PullRequestDetailAction.ToggleCommentReaction(comment.id, reaction))
                }
            )
        }
        item(key = "comments-status") {
            GithubPagedListStatus(
                itemCount = state.comments.size,
                isInitialLoading = state.isLoadingComments,
                isLoadingMore = state.isLoadingMoreComments,
                hasError = state.commentsError,
                canLoadMore = state.canLoadMoreComments,
                errorMessage = stringResource(R.string.github_comments_load_error),
                emptyMessage = stringResource(R.string.github_no_comments),
                onRetry = { onAction(PullRequestDetailAction.RetryComments) },
                onLoadMore = { onAction(PullRequestDetailAction.LoadMoreComments) },
                compact = true
            )
        }
        item(key = "comment-composer") {
            GithubCommentComposer(
                value = state.commentDraft,
                maxLength = GithubIssueCommentDraft.MAX_BODY_LENGTH,
                canWrite = canWrite,
                isValidationError = state.commentValidationError,
                isSubmitError = state.commentSubmitError,
                isSubmitting = state.isSubmittingComment,
                onValueChange = { onAction(PullRequestDetailAction.CommentChanged(it)) },
                onSubmit = { onAction(PullRequestDetailAction.SubmitComment) },
                onSignIn = onSignIn,
                disabledMessage = if (pullRequest.isLocked) {
                    stringResource(R.string.github_locked_comment_disabled)
                } else {
                    null
                }
            )
        }
        item(key = "pull-request-actions") {
            PullRequestActionsCard(
                pullRequest = pullRequest,
                canWrite = canWrite,
                isMerging = state.isMerging,
                mergeError = state.mergeError,
                mergeValidationError = state.mergeValidationError,
                mergeSucceeded = state.mergeResult?.merged == true,
                onMerge = { method, title, message ->
                    onAction(
                        PullRequestDetailAction.Merge(
                            method = method,
                            commitTitle = title,
                            commitMessage = message
                        )
                    )
                },
                onSignIn = onSignIn
            )
        }
    }
}

@Composable
private fun PullRequestManagementSheet(
    pullRequest: GithubPullRequest,
    isBusy: Boolean,
    hasError: Boolean,
    onEditContent: () -> Unit,
    onEditLabels: () -> Unit,
    onEditAssignees: () -> Unit,
    onEditMilestone: () -> Unit,
    onEditReviewers: () -> Unit,
    onToggleState: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = stringResource(R.string.github_manage_pr),
            subtitle = stringResource(R.string.github_pr_number, pullRequest.number),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            PullRequestManagementButton(
                label = stringResource(R.string.github_edit_pr_content),
                enabled = !isBusy,
                onClick = onEditContent
            )
            PullRequestManagementButton(
                label = stringResource(R.string.github_edit_labels),
                enabled = !isBusy,
                onClick = onEditLabels
            )
            PullRequestManagementButton(
                label = stringResource(R.string.github_edit_assignees),
                enabled = !isBusy,
                onClick = onEditAssignees
            )
            PullRequestManagementButton(
                label = stringResource(R.string.github_edit_milestone),
                enabled = !isBusy,
                onClick = onEditMilestone
            )
            PullRequestManagementButton(
                label = stringResource(R.string.github_edit_reviewers),
                enabled = !isBusy,
                onClick = onEditReviewers
            )
            if (!pullRequest.isMerged) {
                PullRequestManagementButton(
                    label = stringResource(
                        if (pullRequest.state == GithubPullRequestState.OPEN) {
                            R.string.github_close_pr
                        } else {
                            R.string.github_reopen_pr
                        }
                    ),
                    enabled = !isBusy,
                    onClick = onToggleState
                )
            }
            PullRequestManagementButton(
                label = stringResource(
                    if (pullRequest.isLocked) R.string.github_unlock_pr else R.string.github_lock_pr
                ),
                enabled = !isBusy,
                onClick = onToggleLock
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp).size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            if (hasError) {
                Text(
                    text = stringResource(R.string.github_pr_management_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PullRequestManagementButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.control
    ) {
        Text(label)
    }
}

private fun PullRequestSection.isLoading(state: PullRequestDetailUiState): Boolean = when (this) {
    PullRequestSection.OVERVIEW -> state.isLoadingPullRequest
    PullRequestSection.FILES -> state.isLoadingFiles && state.files.isNotEmpty()
    PullRequestSection.ACTIVITY ->
        (state.isLoadingReviews && state.reviews.isNotEmpty()) ||
            (state.isLoadingComments && state.comments.isNotEmpty())
}
