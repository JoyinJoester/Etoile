package takagi.ru.monica.github.feature.commits

import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.GithubAdaptiveDetailLayout
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubUserLink
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubCommit
import takagi.ru.monica.github.domain.GithubCommitDetails
import takagi.ru.monica.github.domain.GithubCommitFile
import takagi.ru.monica.github.domain.GithubCommitFileStatus
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun CommitsScreen(
    state: CommitsUiState,
    onAction: (CommitsAction) -> Unit,
    onBack: () -> Unit,
    onOpenCommit: (GithubCommit) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = state.name,
        subtitle = stringResource(R.string.github_commits),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { onAction(CommitsAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.commits(state.fullName, state.ref))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(CommitsAction.Refresh) },
            enabled = !state.isRefreshing && !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.github_commit_branch, state.ref),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isLoading && state.items.isNotEmpty()) {
                        LinearProgressIndicator(modifier = Modifier.width(72.dp))
                    }
                }
                GithubListLoadingState(
                    isLoading = state.isLoading,
                    hasItems = state.items.isNotEmpty(),
                    row = GithubSkeletonRow.LIST,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                GithubAdaptiveGrid(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(state.items, key = GithubCommit::sha) { commit ->
                        CommitListRow(commit = commit, onClick = { onOpenCommit(commit) })
                    }
                    githubFullSpanItem(key = "list-status") {
                        GithubPagedListStatus(
                            itemCount = state.items.size,
                            isInitialLoading = state.isLoading,
                            isLoadingMore = state.isLoadingMore,
                            hasError = state.error,
                            canLoadMore = state.canLoadMore,
                            errorMessage = stringResource(R.string.github_commit_list_error),
                            emptyMessage = stringResource(R.string.github_no_commits),
                            onRetry = { onAction(CommitsAction.Retry) },
                            emptyIcon = Icons.Default.History,
                            onLoadMore = { onAction(CommitsAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitListRow(commit: GithubCommit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = commit.shortSha,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (commit.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.github_commit_verified),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp)
                        )
                    }
                    Text(
                        text = " · ${commit.authoredAt.take(10)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                commit.authorLogin?.let { login ->
                    GithubUserLink(
                        login = login,
                        avatarUrl = commit.authorAvatarUrl,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } ?: Text(
                    text = commit.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(18.dp)
            )
        }
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(top = 13.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
fun CommitDetailScreen(
    state: CommitDetailUiState,
    onAction: (CommitDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = state.details
    GithubDetailScaffold(
        title = details?.commit?.shortSha ?: stringResource(R.string.github_commit),
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            details?.let { GithubOpenOnGithubButton(onClick = { onOpenExternal(it.commit.htmlUrl) }) }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                details == null && state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                details == null && state.error -> GithubMessageState(
                    title = stringResource(R.string.github_commit_load_error),
                    color = MaterialTheme.colorScheme.error,
                    actionLabel = stringResource(R.string.github_retry),
                    onAction = { onAction(CommitDetailAction.Retry) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                details != null -> CommitDetailContent(
                    details = details,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            if (details != null && state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun CommitDetailContent(details: GithubCommitDetails, modifier: Modifier = Modifier) {
    GithubAdaptiveDetailLayout(
        modifier = modifier.fillMaxSize(),
        sidebar = { paneModifier ->
            LazyColumn(
                modifier = paneModifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item(key = "summary") { CommitSummary(details) }
            }
        }
    ) { paneModifier, expanded ->
        LazyColumn(
            modifier = paneModifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (!expanded) item(key = "summary") { CommitSummary(details) }
            item(key = "files-heading") {
                GithubSectionHeader(title = stringResource(R.string.github_commit_files))
            }
            if (details.files.isEmpty()) {
                item(key = "no-files") {
                    GithubMessageState(title = stringResource(R.string.github_commit_no_files))
                }
            } else {
                items(details.files, key = { it.filename }) { file ->
                    CommitFileCard(file)
                    Spacer(Modifier.height(10.dp))
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun CommitSummary(details: GithubCommitDetails) {
    val commit = details.commit
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(commit.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (commit.message != commit.title) {
                Text(
                    text = commit.message.substringAfter('\n').trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommitBadge(commit.shortSha)
                if (commit.isVerified) CommitBadge(stringResource(R.string.github_commit_verified))
            }
            Spacer(Modifier.height(14.dp))
            GithubMetadataRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.github_commit_author),
                value = commit.authorName,
                valueContent = {
                    commit.authorLogin?.let { GithubUserLink(it, avatarUrl = commit.authorAvatarUrl) }
                        ?: Text(commit.authorName, style = MaterialTheme.typography.bodyMedium)
                }
            )
            GithubMetadataRow(
                icon = Icons.Default.Code,
                title = stringResource(R.string.github_commit_date),
                value = commit.authoredAt.take(10)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                GithubMetric(
                    value = details.additions.toString(),
                    label = stringResource(R.string.github_commit_additions),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = details.deletions.toString(),
                    label = stringResource(R.string.github_commit_deletions),
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = details.totalChanges.toString(),
                    label = stringResource(R.string.github_commit_changes),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CommitBadge(text: String) {
    Surface(shape = GithubExpressiveShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CommitFileCard(file: GithubCommitFile) {
    var expanded by rememberSaveable(file.filename, file.patch) { mutableStateOf(false) }
    val changeDescription = stringResource(
        R.string.github_commit_change_summary, file.additions, file.deletions, file.changes
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.filename, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    file.previousFilename?.let {
                        Text(
                            text = stringResource(R.string.github_commit_renamed_from, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(commitStatusString(file.status)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 10.dp).clearAndSetSemantics {
                    contentDescription = changeDescription
                }
            ) {
                Text("+${file.additions}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text("-${file.deletions}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                Text("${file.changes} ${stringResource(R.string.github_commit_changes_short)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            file.patch?.takeIf(String::isNotBlank)?.let { patch ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = GithubExpressiveShapes.compact,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    SelectionContainer {
                        Text(
                            text = if (expanded) patch else patch.take(MAX_PATCH_PREVIEW_CHARS),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                        )
                    }
                }
                if (patch.length > MAX_PATCH_PREVIEW_CHARS) {
                    Text(
                        text = stringResource(R.string.github_commit_patch_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.github_collapse_diff else R.string.github_expand_diff))
                    }
                }
            }
        }
    }
}

private fun commitStatusString(status: GithubCommitFileStatus): Int = when (status) {
    GithubCommitFileStatus.ADDED -> R.string.github_commit_status_added
    GithubCommitFileStatus.MODIFIED -> R.string.github_commit_status_modified
    GithubCommitFileStatus.REMOVED -> R.string.github_commit_status_removed
    GithubCommitFileStatus.RENAMED -> R.string.github_commit_status_renamed
    GithubCommitFileStatus.COPIED -> R.string.github_commit_status_copied
    GithubCommitFileStatus.CHANGED -> R.string.github_commit_status_changed
    GithubCommitFileStatus.UNKNOWN -> R.string.github_commit_status_unknown
}

private const val MAX_PATCH_PREVIEW_CHARS = 6_000
