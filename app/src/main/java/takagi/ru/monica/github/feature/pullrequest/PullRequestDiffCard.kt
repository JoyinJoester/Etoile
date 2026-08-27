package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubPullRequestFile

@Composable
internal fun PullRequestDiffCard(
    file: GithubPullRequestFile,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val diff = remember(file.patch) { file.patch?.let(::parsePullRequestDiff) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = pullRequestFileStatusLabel(file.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${file.additions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "-${file.deletions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            GithubOpenOnGithubButton(onClick = { onOpenExternal(file.blobUrl) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (diff == null || diff.lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.github_diff_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val annotatedDiff = pullRequestDiffAnnotatedString(diff)
                SelectionContainer {
                    Text(
                        text = annotatedDiff,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
                if (diff.isTruncated) {
                    Text(
                        text = stringResource(R.string.github_diff_truncated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun pullRequestDiffAnnotatedString(diff: PullRequestDiff): AnnotatedString {
    val additionBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
    val additionContent = MaterialTheme.colorScheme.onPrimaryContainer
    val deletionBackground = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    val deletionContent = MaterialTheme.colorScheme.onErrorContainer
    val hunkBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
    val hunkContent = MaterialTheme.colorScheme.onSecondaryContainer
    val metadataContent = MaterialTheme.colorScheme.onSurfaceVariant
    val contextContent = MaterialTheme.colorScheme.onSurface
    return remember(
        diff,
        additionBackground,
        additionContent,
        deletionBackground,
        deletionContent,
        hunkBackground,
        hunkContent,
        metadataContent,
        contextContent
    ) {
        buildAnnotatedString {
            diff.lines.forEachIndexed { index, line ->
                val style = when (line.kind) {
                    PullRequestDiffLineKind.ADDITION -> SpanStyle(
                        color = additionContent,
                        background = additionBackground
                    )
                    PullRequestDiffLineKind.DELETION -> SpanStyle(
                        color = deletionContent,
                        background = deletionBackground
                    )
                    PullRequestDiffLineKind.HUNK -> SpanStyle(
                        color = hunkContent,
                        background = hunkBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    PullRequestDiffLineKind.METADATA -> SpanStyle(color = metadataContent)
                    PullRequestDiffLineKind.CONTEXT -> SpanStyle(color = contextContent)
                }
                withStyle(style) { append(line.text.ifEmpty { " " }) }
                if (index != diff.lines.lastIndex) append('\n')
            }
        }
    }
}

@Composable
private fun pullRequestFileStatusLabel(status: String): String = when (status.lowercase()) {
    "added" -> stringResource(R.string.github_file_added)
    "removed" -> stringResource(R.string.github_file_removed)
    "renamed" -> stringResource(R.string.github_file_renamed)
    "copied" -> stringResource(R.string.github_file_copied)
    "modified", "changed" -> stringResource(R.string.github_file_modified)
    else -> status
}
