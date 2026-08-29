package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubLabelRow
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubUserMetadataLine
import takagi.ru.monica.github.component.GithubUserGroup
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
internal fun PullRequestListRow(
    pullRequest: GithubPullRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PullRequestStateIcon(pullRequest)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pullRequest.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                GithubUserMetadataLine(
                    prefix = stringResource(R.string.github_pr_metadata_prefix, pullRequest.number),
                    login = pullRequest.author.login,
                    avatarUrl = pullRequest.author.avatarUrl,
                    suffix = stringResource(
                        R.string.github_pr_metadata_suffix,
                        githubRelativeTime(pullRequest.createdAt)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(
                            R.string.github_branch_direction,
                            pullRequest.head.label,
                            pullRequest.base.label
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pullRequest.comments + pullRequest.reviewComments > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        (pullRequest.comments + pullRequest.reviewComments).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (pullRequest.labels.isNotEmpty()) {
            GithubLabelRow(pullRequest.labels, modifier = Modifier.padding(start = 32.dp, top = 10.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp, start = 32.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
internal fun PullRequestOverviewCard(
    pullRequest: GithubPullRequest,
    fullName: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            PullRequestStateBadge(pullRequest)
            Text(
                text = pullRequest.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp)
            )
            GithubUserMetadataLine(
                prefix = stringResource(R.string.github_pr_metadata_prefix, pullRequest.number),
                login = pullRequest.author.login,
                avatarUrl = pullRequest.author.avatarUrl,
                suffix = stringResource(
                    R.string.github_pr_metadata_suffix,
                    githubRelativeTime(pullRequest.createdAt)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = GithubExpressiveShapes.compact,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.github_branch_direction,
                            pullRequest.head.label,
                            pullRequest.base.label
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pullRequest.labels.isNotEmpty()) {
                GithubLabelRow(pullRequest.labels, modifier = Modifier.padding(top = 12.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
            if (pullRequest.body.isNullOrBlank()) {
                Text(
                    stringResource(R.string.github_pr_body_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownPreviewText(
                    markdown = pullRequest.body,
                    imageBitmaps = emptyMap(),
                    onOpenExternalLink = { target ->
                        onOpenExternal(
                            GithubWebUrls.resolveMarkdownLink(fullName, pullRequest.head.sha, "", target)
                        )
                    },
                    renderImages = false,
                    maxElements = 300
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GithubMetric(
                    value = pullRequest.commits.toString(),
                    label = stringResource(R.string.github_commits),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = pullRequest.changedFiles.toString(),
                    label = stringResource(R.string.github_changed_files),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GithubMetric(
                    value = "+${pullRequest.additions}",
                    label = stringResource(R.string.github_additions),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                GithubMetric(
                    value = "-${pullRequest.deletions}",
                    label = stringResource(R.string.github_deletions),
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
            MergeabilityPanel(pullRequest, modifier = Modifier.padding(top = 12.dp))
            pullRequest.milestone?.let { milestone ->
                GithubMetadataRow(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.github_milestone),
                    value = milestone.title,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
            GithubUserGroup(
                title = stringResource(R.string.github_assignees),
                users = pullRequest.assignees,
                modifier = Modifier.padding(top = 18.dp)
            )
            GithubUserGroup(
                title = stringResource(R.string.github_requested_reviewers),
                users = pullRequest.requestedReviewers,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

@Composable
private fun PullRequestStateIcon(pullRequest: GithubPullRequest) {
    val merged = pullRequest.isMerged
    val open = pullRequest.state == GithubPullRequestState.OPEN
    Icon(
        imageVector = when {
            merged -> Icons.Default.CheckCircle
            open -> Icons.Default.RadioButtonChecked
            else -> Icons.Default.Close
        },
        contentDescription = null,
        tint = when {
            pullRequest.isDraft -> MaterialTheme.colorScheme.secondary
            merged -> MaterialTheme.colorScheme.tertiary
            open -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        modifier = Modifier.padding(top = 2.dp).size(20.dp)
    )
}

@Composable
private fun PullRequestStateBadge(pullRequest: GithubPullRequest) {
    val container: Color
    val content: Color
    val label: String
    when {
        pullRequest.isDraft -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            label = stringResource(R.string.github_pr_draft)
        }
        pullRequest.isMerged -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
            label = stringResource(R.string.github_pr_merged)
        }
        pullRequest.state == GithubPullRequestState.OPEN -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            label = stringResource(R.string.github_pr_open)
        }
        else -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            label = stringResource(R.string.github_pr_closed)
        }
    }
    Surface(shape = GithubExpressiveShapes.control, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MergeabilityPanel(pullRequest: GithubPullRequest, modifier: Modifier = Modifier) {
    val merged = pullRequest.isMerged
    val mergeable = pullRequest.mergeable
    val container = when {
        merged || mergeable == true -> MaterialTheme.colorScheme.primaryContainer
        mergeable == false -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        merged || mergeable == true -> MaterialTheme.colorScheme.onPrimaryContainer
        mergeable == false -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        merged -> stringResource(R.string.github_pr_merged)
        mergeable == true -> stringResource(R.string.github_mergeable)
        mergeable == false -> stringResource(R.string.github_merge_conflicts)
        else -> stringResource(R.string.github_merge_status_unknown)
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = GithubExpressiveShapes.compact, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (mergeable == false) Icons.Default.Close else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}
