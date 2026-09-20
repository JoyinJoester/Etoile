package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubBranchComparison
import takagi.ru.monica.github.domain.GithubPullRequestFile
import takagi.ru.monica.github.feature.pullrequest.PullRequestDiffCard
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun RepositoryCompareScreen(
    state: RepositoryCompareUiState,
    onAction: (RepositoryCompareAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val comparison = state.comparison
    val files = comparison?.files ?: emptyList()
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_compare_title),
        subtitle = state.refRange,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.compare(state.fullName, state.base, state.head))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (comparison != null) {
                item(key = "summary") {
                    RepositoryCompareSummary(
                        base = state.base,
                        head = state.head,
                        comparison = comparison
                    )
                }
            }
            if (state.isLoading && files.isEmpty()) {
                item(key = "loading") { GithubCenteredProgress() }
            }
            if (files.isNotEmpty()) {
                item(key = "files-heading") {
                    GithubSectionHeader(
                        title = stringResource(R.string.github_commit_files),
                        compact = true
                    )
                }
                if (comparison?.fileLimitReached == true) {
                    item(key = "file-limit") {
                        Text(
                            text = stringResource(R.string.github_compare_file_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
                items(files, key = GithubPullRequestFile::filename) { file ->
                    PullRequestDiffCard(file = file, onOpenExternal = onOpenExternal)
                }
            }
            item(key = "files-status") {
                GithubPagedListStatus(
                    itemCount = files.size,
                    isInitialLoading = state.isLoading,
                    isLoadingMore = false,
                    hasError = state.failure != null,
                    canLoadMore = false,
                    errorMessage = state.failure?.let { repositoryComparisonFailureText(it) }
                        ?: stringResource(R.string.github_compare_error),
                    emptyMessage = stringResource(R.string.github_compare_empty),
                    onRetry = { onAction(RepositoryCompareAction.Retry) },
                    onLoadMore = {},
                    emptyIcon = Icons.AutoMirrored.Filled.CallSplit,
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun RepositoryCompareSummary(
    base: String,
    head: String,
    comparison: GithubBranchComparison
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefName(name = base, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RefName(name = head, modifier = Modifier.weight(1f))
            }
            Text(
                text = comparisonStatusLabel(comparison.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                GithubMetric(
                    value = comparison.aheadBy.toString(),
                    label = stringResource(R.string.github_compare_ahead),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = comparison.behindBy.toString(),
                    label = stringResource(R.string.github_compare_behind),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = comparison.files.size.toString(),
                    label = stringResource(R.string.github_pr_files),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RefName(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun comparisonStatusLabel(status: String): String = when (status.lowercase()) {
    "identical" -> stringResource(R.string.github_compare_identical)
    "ahead" -> stringResource(R.string.github_compare_ahead)
    "behind" -> stringResource(R.string.github_compare_behind)
    "diverged" -> stringResource(R.string.github_compare_diverged)
    // GitHub can add comparison statuses without an app release, so an unknown one stays visible.
    else -> status
}
