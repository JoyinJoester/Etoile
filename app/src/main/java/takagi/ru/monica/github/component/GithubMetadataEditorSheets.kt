package takagi.ru.monica.github.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubUserSummary

@Composable
fun GithubLabelsEditorSheet(
    labels: List<GithubIssueLabel>,
    selected: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    isSaving: Boolean,
    saveError: Boolean,
    saveErrorMessage: String,
    onToggle: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = stringResource(R.string.github_edit_labels),
            subtitle = stringResource(R.string.github_labels_selected_count, selected.size),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(labels, key = GithubIssueLabel::name) { label ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !isSaving) { onToggle(label.name) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = label.name in selected,
                        onCheckedChange = { onToggle(label.name) },
                        enabled = !isSaving
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        GithubLabelRow(listOf(label))
                        label.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item(key = "labels-status") {
                GithubMetadataListStatus(
                    isEmpty = labels.isEmpty(),
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    hasError = hasError,
                    canLoadMore = canLoadMore,
                    loadError = stringResource(R.string.github_labels_load_error),
                    emptyMessage = stringResource(R.string.github_no_labels),
                    onRetry = onRetry,
                    onLoadMore = onLoadMore
                )
            }
        }
        GithubMetadataEditorFooter(
            isSaving = isSaving,
            saveError = saveError,
            saveErrorMessage = saveErrorMessage,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun GithubAssigneesEditorSheet(
    assignees: List<GithubUserSummary>,
    selected: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    isSaving: Boolean,
    saveError: Boolean,
    saveErrorMessage: String,
    onToggle: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubUsersEditorSheet(
        users = assignees,
        selected = selected,
        sheetTitle = stringResource(R.string.github_edit_assignees),
        sheetSubtitle = stringResource(R.string.github_assignees_selected_count, selected.size),
        loadErrorMessage = stringResource(R.string.github_assignees_load_error),
        emptyMessage = stringResource(R.string.github_no_assignees_available),
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasError = hasError,
        canLoadMore = canLoadMore,
        isSaving = isSaving,
        saveError = saveError,
        saveErrorMessage = saveErrorMessage,
        onToggle = onToggle,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

@Composable
fun GithubReviewersEditorSheet(
    users: List<GithubUserSummary>,
    selected: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    isSaving: Boolean,
    validationError: Boolean,
    saveError: Boolean,
    onToggle: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubUsersEditorSheet(
        users = users,
        selected = selected,
        sheetTitle = stringResource(R.string.github_edit_reviewers),
        sheetSubtitle = stringResource(R.string.github_reviewers_selected_count, selected.size),
        loadErrorMessage = stringResource(R.string.github_reviewers_load_error),
        emptyMessage = stringResource(R.string.github_no_reviewers_available),
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasError = hasError,
        canLoadMore = canLoadMore,
        isSaving = isSaving,
        saveError = validationError || saveError,
        saveErrorMessage = stringResource(
            if (validationError) {
                R.string.github_reviewers_validation_error
            } else {
                R.string.github_reviewers_update_error
            }
        ),
        onToggle = onToggle,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

@Composable
private fun GithubUsersEditorSheet(
    users: List<GithubUserSummary>,
    selected: Set<String>,
    sheetTitle: String,
    sheetSubtitle: String,
    loadErrorMessage: String,
    emptyMessage: String,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    isSaving: Boolean,
    saveError: Boolean,
    saveErrorMessage: String,
    onToggle: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = sheetTitle,
            subtitle = sheetSubtitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(users, key = GithubUserSummary::login) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !isSaving) { onToggle(user.login) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = user.login in selected,
                        onCheckedChange = { onToggle(user.login) },
                        enabled = !isSaving
                    )
                    GithubAvatar(
                        login = user.login,
                        avatarUrl = user.avatarUrl,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        text = user.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item(key = "users-status") {
                GithubMetadataListStatus(
                    isEmpty = users.isEmpty(),
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    hasError = hasError,
                    canLoadMore = canLoadMore,
                    loadError = loadErrorMessage,
                    emptyMessage = emptyMessage,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore
                )
            }
        }
        GithubMetadataEditorFooter(
            isSaving = isSaving,
            saveError = saveError,
            saveErrorMessage = saveErrorMessage,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun GithubMilestoneEditorSheet(
    milestones: List<GithubIssueMilestone>,
    selected: Int?,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    isSaving: Boolean,
    saveError: Boolean,
    saveErrorMessage: String,
    onSelect: (Int?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = stringResource(R.string.github_edit_milestone),
            subtitle = stringResource(R.string.github_select_one_milestone),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            item(key = "no-milestone") {
                GithubMilestoneRow(
                    selected = selected == null,
                    enabled = !isSaving,
                    title = stringResource(R.string.github_no_milestone),
                    onClick = { onSelect(null) }
                )
            }
            items(milestones, key = GithubIssueMilestone::number) { milestone ->
                val progressText = stringResource(
                    R.string.github_milestone_progress,
                    milestone.openIssues,
                    milestone.closedIssues
                )
                val dueText = milestone.dueOn?.take(10)?.let { due ->
                    stringResource(R.string.github_milestone_due, due)
                }
                GithubMilestoneRow(
                    selected = selected == milestone.number,
                    enabled = !isSaving,
                    title = milestone.title,
                    supportingText = listOfNotNull(progressText, dueText),
                    onClick = { onSelect(milestone.number) }
                )
            }
            item(key = "milestones-status") {
                GithubMetadataListStatus(
                    isEmpty = false,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    hasError = hasError,
                    canLoadMore = canLoadMore,
                    loadError = stringResource(R.string.github_milestones_load_error),
                    emptyMessage = "",
                    onRetry = onRetry,
                    onLoadMore = onLoadMore
                )
            }
        }
        GithubMetadataEditorFooter(
            isSaving = isSaving,
            saveError = saveError,
            saveErrorMessage = saveErrorMessage,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun GithubMilestoneRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    supportingText: List<String> = emptyList(),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (supportingText.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            supportingText.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun GithubMetadataListStatus(
    isEmpty: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    loadError: String,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    when {
        hasError -> GithubMessageState(
            title = loadError,
            color = MaterialTheme.colorScheme.error,
            actionLabel = stringResource(R.string.github_retry),
            onAction = onRetry
        )
        isLoadingMore -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        canLoadMore -> TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.github_load_more))
        }
        isEmpty && !isLoading && emptyMessage.isNotEmpty() -> GithubMessageState(title = emptyMessage)
    }
}

@Composable
private fun GithubMetadataEditorFooter(
    isSaving: Boolean,
    saveError: Boolean,
    saveErrorMessage: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (saveError) {
            Text(
                text = saveErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss, enabled = !isSaving) {
            Text(stringResource(R.string.github_cancel))
        }
        TextButton(onClick = onSave, enabled = !isSaving) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.github_save))
        }
    }
}
