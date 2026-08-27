package takagi.ru.monica.github.feature.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCommentCard
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubCommentComposer
import takagi.ru.monica.github.component.GithubConversationContentEditor
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubAssigneesEditorSheet
import takagi.ru.monica.github.component.GithubLabelRow
import takagi.ru.monica.github.component.GithubListOrderingSheet
import takagi.ru.monica.github.component.GithubListSearchField
import takagi.ru.monica.github.component.GithubLabelsEditorSheet
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubMilestoneEditorSheet
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.component.GithubUserMetadataLine
import takagi.ru.monica.github.component.GithubUserGroup
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IssuesScreen(
    state: IssuesUiState,
    onAction: (IssuesAction) -> Unit,
    onBack: () -> Unit,
    onOpenIssue: (GithubIssue) -> Unit,
    canCreateIssue: Boolean,
    onCreateIssue: () -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val issueStates = GithubIssueState.entries
    var orderingOpen by remember { mutableStateOf(false) }
    GithubDetailScaffold(
        title = state.name,
        subtitle = stringResource(R.string.github_issues),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = if (canCreateIssue) onCreateIssue else onSignIn) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.github_new_issue))
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.issues(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GithubFilterRow(
                labels = listOf(
                    stringResource(R.string.github_issue_open),
                    stringResource(R.string.github_issue_closed)
                ),
                selectedIndex = issueStates.indexOf(state.selectedState),
                onSelected = { onAction(IssuesAction.SelectState(issueStates[it])) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            GithubListSearchField(
                value = state.searchQuery,
                onValueChange = { onAction(IssuesAction.SearchChanged(it)) },
                label = stringResource(R.string.github_search_loaded_issues),
                clearContentDescription = stringResource(R.string.github_clear_search),
                orderingContentDescription = stringResource(R.string.github_list_sort_and_filter),
                onOpenOrdering = { orderingOpen = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.visibleItems, key = GithubIssue::id) { issue ->
                    IssueRow(
                        issue = issue,
                        onClick = { onOpenIssue(issue) },
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
                        errorMessage = stringResource(R.string.github_issue_list_error),
                        emptyMessage = stringResource(
                            if (state.hasLocalFilters) {
                                R.string.github_no_loaded_issues_match
                            } else {
                                R.string.github_no_issues
                            }
                        ),
                        onRetry = { onAction(IssuesAction.Retry) },
                        onLoadMore = { onAction(IssuesAction.LoadMore) }
                    )
                }
            }
        }
    }
    if (orderingOpen) {
        GithubListOrderingSheet(
            title = stringResource(R.string.github_sort_issues),
            subtitle = stringResource(R.string.github_list_sort_subtitle),
            sort = state.sort,
            direction = state.direction,
            onSelectOrdering = { sort, direction ->
                orderingOpen = false
                onAction(IssuesAction.SelectOrdering(sort, direction))
            },
            onDismissRequest = { orderingOpen = false }
        )
    }
}

@Composable
fun IssueDetailScreen(
    state: IssueDetailUiState,
    onAction: (IssueDetailAction) -> Unit,
    onBack: () -> Unit,
    canWrite: Boolean,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var labelsOpen by remember { mutableStateOf(false) }
    var selectedLabels by remember { mutableStateOf(emptySet<String>()) }
    var labelSaveRequested by remember { mutableStateOf(false) }
    var assigneesOpen by remember { mutableStateOf(false) }
    var selectedAssignees by remember { mutableStateOf(emptySet<String>()) }
    var assigneeSaveRequested by remember { mutableStateOf(false) }
    var milestonesOpen by remember { mutableStateOf(false) }
    var selectedMilestone by remember { mutableStateOf<Int?>(null) }
    var milestoneSaveRequested by remember { mutableStateOf(false) }
    var managementOpen by remember { mutableStateOf(false) }
    var contentEditorOpen by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }
    var contentSaveRequested by remember { mutableStateOf(false) }
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
    LaunchedEffect(state.isUpdatingContent, state.contentUpdateError, contentSaveRequested) {
        if (contentSaveRequested && !state.isUpdatingContent) {
            if (!state.contentUpdateError && !state.contentValidationError) contentEditorOpen = false
            contentSaveRequested = false
        }
    }
    GithubDetailScaffold(
        title = "#${state.number}",
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.issue(state.fullName, state.number))
            }
        }
    ) { padding ->
        val issue = state.issue
        when {
            issue == null && state.isLoadingIssue -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            issue == null && state.issueError -> GithubMessageState(
                title = stringResource(R.string.github_issue_load_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(IssueDetailAction.RetryIssue) },
                modifier = Modifier.padding(padding).padding(horizontal = 20.dp)
            )
            issue != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    IssueBody(
                        issue = issue,
                        fullName = state.fullName,
                        canWrite = canWrite,
                        isManaging = state.isUpdatingState || state.isUpdatingLock ||
                            state.isUpdatingLabels || state.isUpdatingAssignees ||
                            state.isUpdatingMilestone || state.isUpdatingContent,
                        managementError = state.stateUpdateError || state.lockUpdateError ||
                            state.labelsUpdateError || state.assigneesUpdateError ||
                            state.milestoneUpdateError || state.contentUpdateError,
                        onManage = { managementOpen = true },
                        onOpenExternal = onOpenExternal
                    )
                }
                item { GithubSectionHeader(title = stringResource(R.string.github_comments)) }
                if (state.isLoadingComments) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                items(state.comments, key = GithubIssueComment::id) { comment ->
                    GithubCommentCard(
                        comment = comment,
                        fullName = state.fullName,
                        onOpenExternal = onOpenExternal,
                        activeReactions = state.activeReactions[comment.id].orEmpty(),
                        isReactionUpdating = comment.id in state.reactionBusyCommentIds,
                        hasReactionError = comment.id in state.reactionErrorCommentIds,
                        canReact = canWrite,
                        onReaction = { reaction ->
                            onAction(IssueDetailAction.ToggleCommentReaction(comment.id, reaction))
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
                        onRetry = { onAction(IssueDetailAction.RetryComments) },
                        onLoadMore = { onAction(IssueDetailAction.LoadMoreComments) }
                    )
                }
                item {
                    GithubCommentComposer(
                        value = state.commentDraft,
                        maxLength = GithubIssueCommentDraft.MAX_BODY_LENGTH,
                        canWrite = canWrite,
                        isValidationError = state.commentValidationError,
                        isSubmitError = state.commentSubmitError,
                        isSubmitting = state.isSubmittingComment,
                        onValueChange = { onAction(IssueDetailAction.CommentChanged(it)) },
                        onSubmit = { onAction(IssueDetailAction.SubmitComment) },
                        onSignIn = onSignIn,
                        disabledMessage = if (issue.isLocked) {
                            stringResource(R.string.github_locked_comment_disabled)
                        } else {
                            null
                        }
                    )
                }
            }
        }
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
            saveErrorMessage = stringResource(R.string.github_issue_labels_update_error),
            onToggle = { name ->
                selectedLabels = if (name in selectedLabels) selectedLabels - name else selectedLabels + name
            },
            onLoadMore = { onAction(IssueDetailAction.LoadMoreLabels) },
            onRetry = { onAction(IssueDetailAction.LoadLabels) },
            onSave = {
                labelSaveRequested = true
                onAction(IssueDetailAction.UpdateLabels(selectedLabels.toList()))
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
            saveErrorMessage = stringResource(R.string.github_issue_assignees_update_error),
            onToggle = { login ->
                selectedAssignees = if (login in selectedAssignees) {
                    selectedAssignees - login
                } else {
                    selectedAssignees + login
                }
            },
            onLoadMore = { onAction(IssueDetailAction.LoadMoreAssignees) },
            onRetry = { onAction(IssueDetailAction.LoadAssignees) },
            onSave = {
                assigneeSaveRequested = true
                onAction(IssueDetailAction.UpdateAssignees(selectedAssignees.toList()))
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
            saveErrorMessage = stringResource(R.string.github_issue_milestone_update_error),
            onSelect = { selectedMilestone = it },
            onLoadMore = { onAction(IssueDetailAction.LoadMoreMilestones) },
            onRetry = { onAction(IssueDetailAction.LoadMilestones) },
            onSave = {
                milestoneSaveRequested = true
                onAction(IssueDetailAction.UpdateMilestone(selectedMilestone))
            },
            onDismiss = { if (!state.isUpdatingMilestone) milestonesOpen = false }
        )
    }
    if (managementOpen && state.issue != null) {
        IssueManagementSheet(
            issue = state.issue,
            onEditContent = {
                managementOpen = false
                editTitle = state.issue.title
                editBody = state.issue.body.orEmpty()
                contentEditorOpen = true
            },
            onEditLabels = {
                managementOpen = false
                selectedLabels = state.issue.labels.map(GithubIssueLabel::name).toSet()
                labelsOpen = true
                onAction(IssueDetailAction.LoadLabels)
            },
            onEditAssignees = {
                managementOpen = false
                selectedAssignees = state.issue.assignees.map(GithubUserSummary::login).toSet()
                assigneesOpen = true
                onAction(IssueDetailAction.LoadAssignees)
            },
            onEditMilestone = {
                managementOpen = false
                selectedMilestone = state.issue.milestone?.number
                milestonesOpen = true
                onAction(IssueDetailAction.LoadMilestones)
            },
            onToggleState = {
                managementOpen = false
                onAction(IssueDetailAction.ToggleState)
            },
            onToggleLock = {
                managementOpen = false
                onAction(IssueDetailAction.ToggleLock)
            },
            onDismiss = { managementOpen = false }
        )
    }
    if (contentEditorOpen) {
        GithubConversationContentEditor(
            sheetTitle = stringResource(R.string.github_edit_issue_content),
            sheetSubtitle = stringResource(R.string.github_edit_issue_content_subtitle),
            title = editTitle,
            body = editBody,
            titleLabel = stringResource(R.string.github_issue_title),
            bodyLabel = stringResource(R.string.github_issue_body),
            validationMessage = stringResource(R.string.github_issue_content_validation_error),
            saveErrorMessage = stringResource(R.string.github_issue_content_update_error),
            maxTitleLength = takagi.ru.monica.github.domain.GithubIssueDraft.MAX_TITLE_LENGTH,
            maxBodyLength = takagi.ru.monica.github.domain.GithubIssueDraft.MAX_BODY_LENGTH,
            isSaving = state.isUpdatingContent,
            validationError = state.contentValidationError,
            saveError = state.contentUpdateError,
            onTitleChange = { if (it.length <= takagi.ru.monica.github.domain.GithubIssueDraft.MAX_TITLE_LENGTH) editTitle = it },
            onBodyChange = { if (it.length <= takagi.ru.monica.github.domain.GithubIssueDraft.MAX_BODY_LENGTH) editBody = it },
            onSave = {
                contentSaveRequested = true
                onAction(IssueDetailAction.UpdateContent(editTitle, editBody))
            },
            onDismiss = { if (!state.isUpdatingContent) contentEditorOpen = false }
        )
    }
}

@Composable
private fun IssueManagementSheet(
    issue: GithubIssue,
    onEditContent: () -> Unit,
    onEditLabels: () -> Unit,
    onEditAssignees: () -> Unit,
    onEditMilestone: () -> Unit,
    onToggleState: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = stringResource(R.string.github_manage_issue),
            subtitle = stringResource(R.string.github_issue_number, issue.number),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            IssueManagementButton(stringResource(R.string.github_edit_issue_content), onEditContent)
            IssueManagementButton(stringResource(R.string.github_edit_labels), onEditLabels)
            IssueManagementButton(stringResource(R.string.github_edit_assignees), onEditAssignees)
            IssueManagementButton(stringResource(R.string.github_edit_milestone), onEditMilestone)
            IssueManagementButton(
                stringResource(
                    if (issue.state == GithubIssueState.OPEN) R.string.github_close_issue
                    else R.string.github_reopen_issue
                ),
                onToggleState
            )
            IssueManagementButton(
                stringResource(
                    if (issue.isLocked) R.string.github_unlock_issue else R.string.github_lock_issue
                ),
                onToggleLock
            )
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun IssueManagementButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = GithubExpressiveShapes.control
    ) {
        Text(label)
    }
}

@Composable
private fun IssueRow(
    issue: GithubIssue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (issue.state == GithubIssueState.OPEN) {
                    Icons.Default.RadioButtonChecked
                } else {
                    Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = if (issue.state == GithubIssueState.OPEN) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.padding(top = 2.dp).size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                GithubUserMetadataLine(
                    prefix = stringResource(R.string.github_issue_metadata_prefix, issue.number),
                    login = issue.author.login,
                    avatarUrl = issue.author.avatarUrl,
                    suffix = stringResource(
                        R.string.github_issue_metadata_suffix,
                        issue.createdAt.take(10)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (issue.comments > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        issue.comments.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (issue.labels.isNotEmpty()) {
            GithubLabelRow(issue.labels, modifier = Modifier.padding(start = 32.dp, top = 10.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp, start = 32.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun IssueBody(
    issue: GithubIssue,
    fullName: String,
    canWrite: Boolean,
    isManaging: Boolean,
    managementError: Boolean,
    onManage: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IssueStateBadge(issue.state)
                if (issue.isLocked) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = GithubExpressiveShapes.control,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                stringResource(R.string.github_locked),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Text(
                text = issue.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp)
            )
            GithubUserMetadataLine(
                prefix = stringResource(R.string.github_issue_metadata_prefix, issue.number),
                login = issue.author.login,
                avatarUrl = issue.author.avatarUrl,
                suffix = stringResource(
                    R.string.github_issue_metadata_suffix,
                    issue.createdAt.take(10)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (issue.labels.isNotEmpty()) {
                GithubLabelRow(issue.labels, modifier = Modifier.padding(top = 12.dp))
            }
            GithubUserGroup(
                title = stringResource(R.string.github_assignees),
                users = issue.assignees,
                modifier = Modifier.padding(top = 16.dp)
            )
            issue.milestone?.let { milestone ->
                GithubMetadataRow(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.github_milestone),
                    value = milestone.title,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
            if (issue.body.isNullOrBlank()) {
                Text(
                    stringResource(R.string.github_issue_body_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownPreviewText(
                    markdown = issue.body,
                    imageBitmaps = emptyMap(),
                    onOpenExternalLink = { target ->
                        onOpenExternal(
                            GithubWebUrls.resolveMarkdownLink(fullName, "HEAD", "", target)
                        )
                    },
                    renderImages = false,
                    maxElements = 300
                )
            }
            if (managementError) {
                Text(
                    stringResource(R.string.github_issue_management_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            if (canWrite) {
                OutlinedButton(
                    onClick = onManage,
                    enabled = !isManaging,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = GithubExpressiveShapes.control
                ) {
                    if (isManaging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.github_manage_issue))
                }
            }
        }
    }
}

@Composable
private fun IssueStateBadge(state: GithubIssueState) {
    val open = state == GithubIssueState.OPEN
    Surface(
        shape = GithubExpressiveShapes.control,
        color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = stringResource(if (open) R.string.github_issue_open else R.string.github_issue_closed),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
