package takagi.ru.monica.github.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchType
import takagi.ru.monica.github.domain.GithubIssueState

@Composable
fun GithubIssueSearchResultRow(
    result: GithubIssueSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = GithubExpressiveMotion.quickTween(),
        label = "github-search-result-press"
    )
    val accent = if (result.state == GithubIssueState.OPEN) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = when (result.type) {
                    GithubIssueSearchType.PULL_REQUEST -> Icons.AutoMirrored.Filled.CallSplit
                    GithubIssueSearchType.ISSUE -> if (result.state == GithubIssueState.OPEN) {
                        Icons.Default.RadioButtonChecked
                    } else {
                        Icons.Default.CheckCircle
                    }
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(top = 2.dp).size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
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
                        text = stringResource(
                            R.string.github_search_conversation_location,
                            result.repositoryFullName,
                            result.number
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SearchResultTypeBadge(result = result, accent = accent)
                }
                GithubUserMetadataLine(
                    prefix = stringResource(R.string.github_search_result_author_prefix),
                    login = result.author.login,
                    avatarUrl = result.author.avatarUrl,
                    suffix = stringResource(
                        R.string.github_search_result_updated_suffix,
                        result.updatedAt.take(10)
                    ),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (result.comments > 0) {
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = result.comments.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp)
                )
            }
        }
        if (result.labels.isNotEmpty()) {
            GithubLabelRow(result.labels, modifier = Modifier.padding(start = 32.dp, top = 10.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp, start = 32.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun SearchResultTypeBadge(result: GithubIssueSearchResult, accent: Color) {
    val label = when {
        result.type == GithubIssueSearchType.ISSUE -> stringResource(R.string.github_issue_search_badge)
        result.isDraft -> stringResource(R.string.github_pr_draft)
        else -> stringResource(R.string.github_pr_search_badge)
    }
    Surface(
        shape = GithubExpressiveShapes.compact,
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
