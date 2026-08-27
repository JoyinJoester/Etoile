package takagi.ru.monica.github.feature.commits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
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
        title = state.name,
        subtitle = stringResource(R.string.github_commits),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.commits(state.fullName, state.ref))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                if (state.isLoading) LinearProgressIndicator(modifier = Modifier.width(72.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = GithubCommit::sha) { commit ->
                    CommitListCard(commit = commit, onClick = { onOpenCommit(commit) })
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_commit_list_error),
                        emptyMessage = stringResource(R.string.github_no_commits),
                        onRetry = { onAction(CommitsAction.Retry) },
                        onLoadMore = { onAction(CommitsAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitListCard(commit: GithubCommit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = commit.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = commit.shortSha,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (commit.isVerified) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.github_commit_verified),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
            commit.authorLogin?.let { login ->
                GithubUserLink(
                    login = login,
                    avatarUrl = commit.authorAvatarUrl,
                    modifier = Modifier.padding(start = 36.dp, top = 12.dp)
                )
            } ?: Text(
                text = commit.authorName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp, top = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = commit.authoredAt.take(10),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp, top = 3.dp)
            )
        }
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
    LazyColumn(
        modifier = modifier.widthIn(max = GithubAdaptiveLayout.contentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item(key = "summary") { CommitSummary(details) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
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
                    Text(
                        text = patch.take(MAX_PATCH_PREVIEW_CHARS),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
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
