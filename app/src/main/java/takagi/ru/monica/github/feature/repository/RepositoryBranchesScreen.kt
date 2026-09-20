package takagi.ru.monica.github.feature.repository

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubGitRef
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun RepositoryBranchesScreen(
    state: RepositoryBranchesUiState,
    onAction: (RepositoryBranchesAction) -> Unit,
    onBack: () -> Unit,
    onOpenBranch: (GithubBranch) -> Unit,
    onCompare: (GithubBranch) -> Unit,
    onOpenExternal: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var createVisible by rememberSaveable { mutableStateOf(false) }
    var branchName by rememberSaveable { mutableStateOf("") }
    var sourceBranch by rememberSaveable(state.defaultBranch) { mutableStateOf(state.defaultBranch) }
    var deleteCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    var renameCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    val creating = state.pendingWrite is RepositoryBranchWrite.Create
    val deleting = state.pendingWrite is RepositoryBranchWrite.Delete
    val renaming = state.pendingWrite is RepositoryBranchWrite.Rename
    val failedWrite = state.writeOutcome as? RepositoryBranchWriteOutcome.Failed
    val newBranchName = branchName.trim()
    val isBranchNameValid = GithubGitRef.isValidBranchName(newBranchName)
    val isSourceNameValid = GithubGitRef.isValidBranchName(sourceBranch.trim())
    val outcome = state.writeOutcome
    LaunchedEffect(outcome) {
        if (outcome is RepositoryBranchWriteOutcome.Succeeded) {
            when (outcome.write) {
                is RepositoryBranchWrite.Create -> {
                    createVisible = false
                    branchName = ""
                }
                is RepositoryBranchWrite.Delete -> deleteCandidate = null
                is RepositoryBranchWrite.Rename -> {
                    renameCandidate = null
                    renameName = ""
                }
            }
            onAction(RepositoryBranchesAction.DismissWriteOutcome)
        }
    }
    if (deleteCandidate != null) {
        val candidate = deleteCandidate!!
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteCandidate = null },
            title = { Text(stringResource(R.string.github_delete_branch)) },
            text = { Column {
                Text(stringResource(R.string.github_delete_branch_confirm, candidate))
                failedWrite?.takeIf { it.write is RepositoryBranchWrite.Delete }
                    ?.let { RepositoryRefFailureMessage(it.failure) }
            } },
            confirmButton = { TextButton(enabled = !deleting, onClick = {
                onAction(RepositoryBranchesAction.DeleteBranch(candidate))
            }) { Text(stringResource(R.string.github_delete_branch)) } },
            dismissButton = { TextButton(
                enabled = !deleting,
                onClick = { deleteCandidate = null; onAction(RepositoryBranchesAction.DismissWriteOutcome) }
            ) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    if (createVisible) {
        AlertDialog(
            onDismissRequest = { if (!creating) createVisible = false },
            title = { Text(stringResource(R.string.github_new_branch)) },
            text = { Column {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    enabled = !creating,
                    singleLine = true,
                    isError = newBranchName.isNotEmpty() && !isBranchNameValid,
                    supportingText = {
                        if (newBranchName.isNotEmpty() && !isBranchNameValid) {
                            Text(stringResource(R.string.github_branch_name_invalid))
                        }
                    },
                    label = { Text(stringResource(R.string.github_branch_name)) }
                )
                OutlinedTextField(
                    value = sourceBranch,
                    onValueChange = { sourceBranch = it },
                    enabled = !creating,
                    singleLine = true,
                    isError = sourceBranch.isNotBlank() && !isSourceNameValid,
                    supportingText = {
                        if (sourceBranch.isNotBlank() && !isSourceNameValid) {
                            Text(stringResource(R.string.github_branch_name_invalid))
                        } else {
                            Text(stringResource(R.string.github_branch_source_hint))
                        }
                    },
                    label = { Text(stringResource(R.string.github_branch_source)) }
                )
                failedWrite?.takeIf { it.write is RepositoryBranchWrite.Create }
                    ?.let { RepositoryRefFailureMessage(it.failure) }
            } },
            confirmButton = { TextButton(
                enabled = !creating && isBranchNameValid && isSourceNameValid,
                onClick = {
                    onAction(RepositoryBranchesAction.CreateBranch(newBranchName, sourceBranch.trim()))
                }
            ) { Text(stringResource(R.string.github_create)) } },
            dismissButton = { TextButton(
                enabled = !creating,
                onClick = { createVisible = false; onAction(RepositoryBranchesAction.DismissWriteOutcome) }
            ) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    if (renameCandidate != null) {
        val candidate = renameCandidate!!
        val newName = renameName.trim()
        val isNewNameValid = GithubGitRef.isValidBranchName(newName)
        AlertDialog(
            onDismissRequest = { if (!renaming) renameCandidate = null },
            title = { Text(stringResource(R.string.github_rename_branch)) },
            text = { Column {
                Text(stringResource(R.string.github_rename_branch_from, candidate), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    enabled = !renaming,
                    singleLine = true,
                    isError = !isNewNameValid,
                    supportingText = {
                        if (!isNewNameValid) Text(stringResource(R.string.github_branch_name_invalid))
                    },
                    label = { Text(stringResource(R.string.github_branch_name)) }
                )
                failedWrite?.takeIf { it.write is RepositoryBranchWrite.Rename }
                    ?.let { RepositoryRefFailureMessage(it.failure) }
            } },
            confirmButton = { TextButton(enabled = !renaming && isNewNameValid, onClick = {
                onAction(RepositoryBranchesAction.RenameBranch(candidate, newName))
            }) { Text(stringResource(R.string.github_save)) } },
            dismissButton = { TextButton(
                enabled = !renaming,
                onClick = { renameCandidate = null; onAction(RepositoryBranchesAction.DismissWriteOutcome) }
            ) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_branches),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (state.canWrite) {
                TextButton(onClick = {
                    onAction(RepositoryBranchesAction.DismissWriteOutcome)
                    createVisible = true
                }) { Text(stringResource(R.string.github_create)) }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.repositoryBranchesSettings(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(RepositoryBranchesAction.Search(it)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.github_search_branches)) },
                singleLine = true,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            RepositoryWriteAccessHint(
                viewerLogin = state.viewerLogin,
                viewerRole = state.viewerRole,
                onSignIn = onSignIn,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.filteredItems.isNotEmpty(),
                row = GithubSkeletonRow.COMPACT,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.filteredItems, key = GithubBranch::name) { branch ->
                    RepositoryBranchRow(
                        branch = branch,
                        isDefault = branch.name == state.defaultBranch,
                        onClick = { onOpenBranch(branch) },
                        onCompare = { onCompare(branch) },
                        onDelete = if (state.canWrite && !branch.isProtected && branch.name != state.defaultBranch) {
                            {
                                onAction(RepositoryBranchesAction.DismissWriteOutcome)
                                deleteCandidate = branch.name
                            }
                        } else null,
                        onRename = if (state.canWrite && !branch.isProtected && branch.name != state.defaultBranch) {
                            {
                                onAction(RepositoryBranchesAction.DismissWriteOutcome)
                                renameName = branch.name
                                renameCandidate = branch.name
                            }
                        } else null
                    )
                }
                githubFullSpanItem(key = "branches-status") {
                    GithubPagedListStatus(
                        itemCount = state.filteredItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_branches_error),
                        emptyMessage = stringResource(R.string.github_no_branches),
                        onRetry = { onAction(RepositoryBranchesAction.Refresh) },
                        emptyIcon = Icons.AutoMirrored.Filled.CallSplit,
                        onLoadMore = { onAction(RepositoryBranchesAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryBranchRow(
    branch: GithubBranch,
    isDefault: Boolean,
    onClick: () -> Unit,
    onCompare: () -> Unit,
    onDelete: (() -> Unit)?,
    onRename: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = branch.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.github_more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.github_compare_branch)) }, onClick = { menuExpanded = false; onCompare() })
                    if (onRename != null) DropdownMenuItem(text = { Text(stringResource(R.string.github_rename_branch)) }, onClick = { menuExpanded = false; onRename() })
                    if (onDelete != null) DropdownMenuItem(text = { Text(stringResource(R.string.github_delete_branch)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
            if (branch.isProtected) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.github_protected_branch),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (isDefault) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.github_default),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Text(
            text = branch.sha.take(12),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp, top = 5.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}
