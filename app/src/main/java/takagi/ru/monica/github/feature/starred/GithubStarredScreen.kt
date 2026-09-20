package takagi.ru.monica.github.feature.starred

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.NewLabel
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubFlatCard
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubSearchField
import takagi.ru.monica.github.component.GithubSegmentedBar
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.component.GithubTechnicalSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalTag
import takagi.ru.monica.github.domain.GithubLabeledStar
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubStarFilter
import takagi.ru.monica.github.domain.GithubStarLabel
import takagi.ru.monica.github.domain.GithubStarLabelError
import takagi.ru.monica.github.domain.MAX_STAR_LABEL_NAME_LENGTH

@Composable
fun GithubStarredScreen(
    state: StarredUiState,
    onAction: (StarredAction) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = if (state.selectionMode) {
            stringResource(R.string.github_star_selection_count, state.selectionCount)
        } else {
            stringResource(R.string.github_star_collections)
        },
        subtitle = stringResource(R.string.github_star_collections_subtitle),
        backContentDescription = stringResource(R.string.github_back),
        onBack = { if (state.selectionMode) onAction(StarredAction.SelectionCleared) else onBack() },
        actions = {
            if (state.selectionMode) {
                IconButton(onClick = { onAction(StarredAction.SelectAllVisibleToggled) }) {
                    Icon(
                        imageVector = if (state.allVisibleSelected) {
                            Icons.Default.Deselect
                        } else {
                            Icons.Default.SelectAll
                        },
                        contentDescription = stringResource(
                            if (state.allVisibleSelected) {
                                R.string.github_star_select_none
                            } else {
                                R.string.github_star_select_all
                            }
                        )
                    )
                }
                IconButton(onClick = { onAction(StarredAction.BatchLabelRequested) }) {
                    Icon(
                        imageVector = Icons.Default.NewLabel,
                        contentDescription = stringResource(R.string.github_star_batch_label)
                    )
                }
                IconButton(onClick = { onAction(StarredAction.SelectionCleared) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.github_star_selection_clear)
                    )
                }
            } else {
                IconButton(onClick = { onAction(StarredAction.LabelManagerRequested) }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.github_star_manage_labels)
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.requiresAuthentication) {
                GithubMessageState(
                    title = stringResource(R.string.github_star_sign_in_required),
                    actionLabel = stringResource(R.string.github_sign_in),
                    onAction = onSignIn,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                GithubPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(StarredAction.PullToRefresh) },
                    enabled = !state.isLoading && !state.isLoadingMore,
                    modifier = Modifier.fillMaxSize()
                ) {
                    StarredContent(
                        state = state,
                        onAction = onAction,
                        onOpenRepository = onOpenRepository
                    )
                }
            }
        }
    }

    val editing = state.editingRepository
    if (editing != null) {
        StarLabelAssignmentSheet(
            target = editing,
            labels = state.labels,
            labelError = state.labelError,
            onToggle = { labelId ->
                onAction(StarredAction.LabelToggled(editing.repository.id, labelId))
            },
            onCreate = { name -> onAction(StarredAction.LabelCreated(name)) },
            onDismiss = { onAction(StarredAction.LabelEditorDismissed) }
        )
    }
    if (state.labelManagerVisible) {
        StarLabelManagerSheet(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(StarredAction.LabelManagerDismissed) }
        )
    }
    if (state.batchLabelVisible) {
        BatchLabelSheet(
            state = state,
            onToggle = { labelId, assigned ->
                onAction(StarredAction.BatchLabelApplied(labelId, assigned))
            },
            onDismiss = { onAction(StarredAction.BatchLabelDismissed) }
        )
    }
}

@Composable
private fun StarredContent(
    state: StarredUiState,
    onAction: (StarredAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit
) {
    GithubAdaptiveGrid(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
    ) {
        githubFullSpanItem(key = "overview") {
            StarredOverview(state)
            Spacer(Modifier.height(16.dp))
            GithubSearchField(
                value = state.query,
                onValueChange = { onAction(StarredAction.QueryChanged(it)) },
                label = stringResource(R.string.github_search_starred),
                compact = true
            )
            Spacer(Modifier.height(12.dp))
            StarFilterRow(
                state = state,
                onSelected = { onAction(StarredAction.FilterSelected(it)) }
            )
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.visibleRepositories.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        items(state.visibleRepositories, key = { it.repository.id }) { item ->
            StarredRepositoryEntry(
                item = item,
                selectionMode = state.selectionMode,
                selected = item.repository.id in state.selectedRepositories,
                onOpen = {
                    if (state.selectionMode) {
                        onAction(StarredAction.SelectionToggled(item.repository.id))
                    } else {
                        onOpenRepository(item.repository)
                    }
                },
                onLongPress = { onAction(StarredAction.SelectionToggled(item.repository.id)) },
                onEditLabels = { onAction(StarredAction.LabelsRequested(item.repository.id)) }
            )
        }
        githubFullSpanItem(key = "list-status") {
            GithubPagedListStatus(
                itemCount = state.visibleRepositories.size,
                isInitialLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                hasError = state.error,
                canLoadMore = state.canLoadMore,
                errorMessage = stringResource(R.string.github_star_error),
                emptyMessage = stringResource(R.string.github_no_starred_results),
                onRetry = { onAction(StarredAction.Retry) },
                onLoadMore = { onAction(StarredAction.LoadMore) },
                emptyIcon = Icons.Default.Star
            )
        }
    }
}

/**
 * The instrument-panel readout: totals as monospace numerals, plus a segmented
 * bar showing how much of the library has been filed into labels.
 */
@Composable
private fun StarredOverview(state: StarredUiState) {
    val total = state.repositories.size
    val labeled = total - state.unlabeledCount
    GithubFlatCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                GithubTechnicalLabel(stringResource(R.string.github_star_overview_total))
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                GithubTechnicalLabel(stringResource(R.string.github_star_overview_labeled))
                Text(
                    text = stringResource(R.string.github_star_overview_ratio, labeled, total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GithubSegmentedBar(filled = labeled, total = total.coerceAtLeast(1))
    }
}

@Composable
private fun StarFilterRow(
    state: StarredUiState,
    onSelected: (GithubStarFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GithubTechnicalTag(
            label = stringResource(R.string.github_filter_all),
            selected = state.selectedFilter == GithubStarFilter.All,
            count = state.repositories.size,
            onClick = { onSelected(GithubStarFilter.All) }
        )
        GithubTechnicalTag(
            label = stringResource(R.string.github_star_filter_unlabeled),
            selected = state.selectedFilter == GithubStarFilter.Unlabeled,
            count = state.unlabeledCount,
            onClick = { onSelected(GithubStarFilter.Unlabeled) }
        )
        state.labels.forEach { label ->
            val filter = GithubStarFilter.Label(label.id)
            GithubTechnicalTag(
                label = label.name,
                selected = state.selectedFilter == filter,
                count = state.count(label),
                onClick = { onSelected(filter) }
            )
        }
    }
}

/** A repository row plus its assigned labels, separated by a hairline divider. */
@Composable
private fun StarredRepositoryEntry(
    item: GithubLabeledStar,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onEditLabels: () -> Unit
) {
    Column {
        GithubRepositoryRow(
            repository = item.repository,
            descriptionFallback = stringResource(R.string.github_no_description),
            languageFallback = stringResource(R.string.github_unknown_language),
            updatedFallback = stringResource(R.string.github_updated_recently),
            leadingContent = if (selectionMode) {
                {
                    Icon(
                        imageVector = if (selected) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = stringResource(R.string.github_star_select_repository),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                null
            },
            showDivider = false,
            onLongClick = onLongPress,
            onClick = onOpen
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.labels.isEmpty()) {
                    GithubTechnicalLabel(stringResource(R.string.github_star_filter_unlabeled))
                } else {
                    item.labels.forEach { label -> GithubTechnicalTag(label = label.name) }
                }
            }
            TextButton(onClick = onEditLabels) {
                GithubTechnicalLabel(
                    text = stringResource(R.string.github_star_edit_labels),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

/** Toggles which labels one repository belongs to, and can create a new one inline. */
@Composable
private fun StarLabelAssignmentSheet(
    target: GithubLabeledStar,
    labels: List<GithubStarLabel>,
    labelError: GithubStarLabelError?,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            GithubTechnicalLabel(stringResource(R.string.github_star_assign_labels))
            Spacer(Modifier.height(4.dp))
            Text(
                text = target.repository.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            if (labels.isEmpty()) {
                Text(
                    text = stringResource(R.string.github_star_labels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val assigned = target.labels.map { it.id }.toSet()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        GithubTechnicalTag(
                            label = label.name,
                            selected = label.id in assigned,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onToggle(label.id) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            StarLabelNameField(
                key = labels.size,
                labelError = labelError,
                actionLabel = stringResource(R.string.github_star_create_label),
                onSubmit = onCreate
            )
        }
    }
}

/**
 * Applies one label to every selected repository at once. A label already on all
 * of them toggles off instead, so the same row both adds and clears.
 */
@Composable
private fun BatchLabelSheet(
    state: StarredUiState,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            GithubTechnicalLabel(
                stringResource(R.string.github_star_batch_label_title, state.selectionCount)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.github_star_batch_label_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (state.labels.isEmpty()) {
                Text(
                    text = stringResource(R.string.github_star_labels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.labels.forEach { label ->
                        val carrying = state.selectedCount(label)
                        val allCarry = carrying == state.selectionCount
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GithubTechnicalTag(
                                label = label.name,
                                selected = allCarry,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onToggle(label.id, !allCarry) }
                            )
                            GithubTechnicalLabel(
                                text = stringResource(
                                    R.string.github_star_batch_label_partial,
                                    carrying,
                                    state.selectionCount
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Creates, renames, and deletes labels. */
@Composable
private fun StarLabelManagerSheet(
    state: StarredUiState,
    onAction: (StarredAction) -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            GithubTechnicalSectionHeader(stringResource(R.string.github_star_manage_labels))
            Spacer(Modifier.height(12.dp))
            if (state.labels.isEmpty()) {
                Text(
                    text = stringResource(R.string.github_star_labels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.labels.forEach { label ->
                    key(label.id) {
                    StarLabelManagerRow(
                        label = label,
                        labelError = state.labelError.takeIf { state.labelErrorTargetId == label.id },
                        assignedCount = state.count(label),
                        onRename = { name -> onAction(StarredAction.LabelRenamed(label.id, name)) },
                        onDelete = { onAction(StarredAction.LabelDeleted(label.id)) }
                    )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            StarLabelNameField(
                key = state.labels.size,
                labelError = state.labelError.takeIf { state.labelErrorTargetId == null },
                actionLabel = stringResource(R.string.github_star_create_label),
                onSubmit = { name -> onAction(StarredAction.LabelCreated(name)) }
            )
        }
    }
}

@Composable
private fun StarLabelManagerRow(
    label: GithubStarLabel,
    labelError: GithubStarLabelError?,
    assignedCount: Int,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var renaming by rememberSaveable(label.id) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(label.id) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                GithubTechnicalLabel(
                    text = stringResource(R.string.github_star_label_usage, assignedCount)
                )
            }
            TextButton(onClick = { renaming = !renaming }) {
                GithubTechnicalLabel(
                    text = stringResource(R.string.github_star_rename_label),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { confirmingDelete = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.github_star_delete_label),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        if (renaming) {
            StarLabelNameField(
                key = label,
                labelError = labelError,
                actionLabel = stringResource(R.string.github_star_rename_label),
                initialValue = label.name,
                onSubmit = { name ->
                    onRename(name)
                }
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.github_star_delete_label)) },
            text = {
                Text(stringResource(R.string.github_star_delete_label_confirm, label.name, assignedCount))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.github_star_delete_label),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * [key] rebuilds the field when the row it belongs to changes, so a rename
 * pre-fills the label being edited rather than a recycled neighbour's name.
 */
@Composable
private fun StarLabelNameField(
    labelError: GithubStarLabelError?,
    actionLabel: String,
    onSubmit: (String) -> Unit,
    key: Any? = null,
    initialValue: String = ""
) {
    var name by rememberSaveable(key) { mutableStateOf(initialValue) }
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= MAX_STAR_LABEL_NAME_LENGTH) name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = labelError != null,
            label = { Text(stringResource(R.string.github_star_label_name)) },
            supportingText = labelError?.let { error ->
                { Text(starLabelErrorMessage(error)) }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSubmit(name) }, enabled = name.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                GithubTechnicalLabel(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun starLabelErrorMessage(error: GithubStarLabelError): String = when (error) {
    GithubStarLabelError.BLANK_NAME -> stringResource(R.string.github_star_label_error_blank)
    GithubStarLabelError.NAME_TOO_LONG -> stringResource(R.string.github_star_label_error_too_long)
    GithubStarLabelError.DUPLICATE_NAME -> stringResource(R.string.github_star_label_error_duplicate)
    GithubStarLabelError.LIMIT_REACHED -> stringResource(R.string.github_star_label_error_limit)
}
