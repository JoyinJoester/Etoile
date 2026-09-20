package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubGitRef
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.navigation.GithubWebUrls
import androidx.compose.foundation.lazy.grid.items

@Composable
fun RepositoryTagsScreen(
    state: RepositoryTagsUiState,
    onAction: (RepositoryTagsAction) -> Unit,
    onBack: () -> Unit,
    onOpenTag: (GithubTag) -> Unit,
    onOpenExternal: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var createVisible by rememberSaveable { mutableStateOf(false) }
    var tagName by rememberSaveable { mutableStateOf("") }
    var sourceRef by rememberSaveable { mutableStateOf(state.defaultBranch) }
    var deleteCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    val creating = state.pendingWrite is RepositoryTagWrite.Create
    val pendingDelete = state.pendingWrite as? RepositoryTagWrite.Delete
    val deleting = pendingDelete != null
    val failedWrite = state.writeOutcome as? RepositoryTagWriteOutcome.Failed
    val newTagName = tagName.trim()
    val isTagNameValid = GithubGitRef.isValidTagName(newTagName)
    val isSourceNameValid = GithubGitRef.isValidBranchName(sourceRef.trim())
    val outcome = state.writeOutcome
    LaunchedEffect(outcome) {
        if (outcome is RepositoryTagWriteOutcome.Succeeded) {
            when (outcome.write) {
                is RepositoryTagWrite.Create -> {
                    createVisible = false
                    tagName = ""
                }
                is RepositoryTagWrite.Delete -> deleteCandidate = null
            }
            onAction(RepositoryTagsAction.DismissWriteOutcome)
        }
    }
    if (deleteCandidate != null) {
        val candidate = deleteCandidate!!
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteCandidate = null },
            title = { Text(stringResource(R.string.github_delete_tag)) },
            text = { Column {
                Text(stringResource(R.string.github_delete_tag_confirm, candidate))
                failedWrite?.takeIf { it.write is RepositoryTagWrite.Delete }
                    ?.let { RepositoryRefFailureMessage(it.failure) }
            } },
            confirmButton = { TextButton(enabled = !deleting, onClick = {
                onAction(RepositoryTagsAction.DeleteTag(candidate))
            }) { Text(stringResource(R.string.github_delete_tag)) } },
            dismissButton = { TextButton(
                enabled = !deleting,
                onClick = { deleteCandidate = null; onAction(RepositoryTagsAction.DismissWriteOutcome) }
            ) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    if (createVisible) {
        AlertDialog(
            onDismissRequest = { if (!creating) createVisible = false },
            title = { Text(stringResource(R.string.github_new_tag)) },
            text = { Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    enabled = !creating,
                    singleLine = true,
                    isError = newTagName.isNotEmpty() && !isTagNameValid,
                    supportingText = {
                        if (newTagName.isNotEmpty() && !isTagNameValid) {
                            Text(stringResource(R.string.github_tag_name_invalid))
                        }
                    },
                    label = { Text(stringResource(R.string.github_tag_name)) }
                )
                OutlinedTextField(
                    value = sourceRef,
                    onValueChange = { sourceRef = it },
                    enabled = !creating,
                    singleLine = true,
                    isError = sourceRef.isNotBlank() && !isSourceNameValid,
                    supportingText = {
                        if (sourceRef.isNotBlank() && !isSourceNameValid) {
                            Text(stringResource(R.string.github_tag_source_invalid))
                        } else {
                            Text(stringResource(R.string.github_tag_source_hint))
                        }
                    },
                    label = { Text(stringResource(R.string.github_tag_source)) }
                )
                failedWrite?.takeIf { it.write is RepositoryTagWrite.Create }
                    ?.let { RepositoryRefFailureMessage(it.failure) }
            } },
            confirmButton = { TextButton(
                enabled = !creating && isTagNameValid && isSourceNameValid,
                onClick = {
                    onAction(RepositoryTagsAction.CreateTag(newTagName, sourceRef.trim()))
                }
            ) { Text(stringResource(R.string.github_create)) } },
            dismissButton = { TextButton(
                enabled = !creating,
                onClick = { createVisible = false; onAction(RepositoryTagsAction.DismissWriteOutcome) }
            ) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_tags),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (state.canWrite) {
                TextButton(onClick = {
                    onAction(RepositoryTagsAction.DismissWriteOutcome)
                    createVisible = true
                }) { Text(stringResource(R.string.github_create)) }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.tags(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(RepositoryTagsAction.Search(it)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.github_search_tags)) },
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
                items(state.filteredItems, key = GithubTag::name) { tag ->
                    RepositoryTagRow(
                        tag = tag,
                        isBusy = state.pendingWrite != null,
                        isDeleting = pendingDelete?.tag == tag.name,
                        canDelete = state.canWrite,
                        onClick = { onOpenTag(tag) },
                        onDelete = {
                            onAction(RepositoryTagsAction.DismissWriteOutcome)
                            deleteCandidate = tag.name
                        }
                    )
                }
                githubFullSpanItem(key = "tags-status") {
                    GithubPagedListStatus(
                        itemCount = state.filteredItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_tags_error),
                        emptyMessage = stringResource(R.string.github_no_tags),
                        onRetry = { onAction(RepositoryTagsAction.Refresh) },
                        emptyIcon = Icons.Default.LocalOffer,
                        onLoadMore = { onAction(RepositoryTagsAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryTagRow(
    tag: GithubTag,
    isBusy: Boolean,
    isDeleting: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            } else if (canDelete) {
                IconButton(onClick = onDelete, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.github_delete_tag)
                    )
                }
            }
        }
        Text(
            text = tag.sha.take(12),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp, top = 4.dp)
        )
    }
}
