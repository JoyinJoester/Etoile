package takagi.ru.monica.github.feature.repository

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun RepositoryWebhooksScreen(
    state: RepositoryWebhooksUiState,
    onAction: (RepositoryWebhooksAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_webhooks),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.repositoryWebhooksSettings(state.fullName))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(RepositoryWebhooksAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.items.isNotEmpty(),
                row = GithubSkeletonRow.COMPACT,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.items, key = GithubRepositoryWebhook::id) { webhook ->
                    RepositoryWebhookRow(webhook, onOpen = {
                        onOpenExternal(GithubWebUrls.repositoryWebhookSettings(state.fullName, webhook.id))
                    })
                }
                githubFullSpanItem(key = "webhooks-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_webhooks_error),
                        emptyMessage = stringResource(R.string.github_no_webhooks),
                        onRetry = { onAction(RepositoryWebhooksAction.Retry) },
                        emptyIcon = Icons.Default.Webhook,
                        onLoadMore = { onAction(RepositoryWebhooksAction.LoadMore) }
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun RepositoryWebhookRow(webhook: GithubRepositoryWebhook, onOpen: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(role = Role.Button, onClick = onOpen)
        .semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(
                webhook.name.takeIf(String::isNotBlank),
                webhook.lastResponseCode?.let { "HTTP $it" }
            ).joinToString(", ")
        }
        .padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = webhook.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (webhook.isActive) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                contentDescription = stringResource(
                    if (webhook.isActive) R.string.github_webhook_active else R.string.github_webhook_inactive
                ),
                tint = if (webhook.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            GithubOpenOnGithubButton(onClick = onOpen)
        }
        Text(
            text = "#${webhook.id}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp, top = 4.dp)
        )
        Text(
            text = webhook.events.joinToString(", ").ifBlank {
                stringResource(R.string.github_webhook_no_events)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp, top = 5.dp)
        )
        val response = listOfNotNull(
            webhook.lastResponseCode?.toString(),
            webhook.lastResponseStatus?.takeIf(String::isNotBlank),
            webhook.lastResponseMessage?.takeIf(String::isNotBlank)
        ).joinToString(" · ")
        if (response.isNotBlank()) {
            SelectionContainer {
            Text(
                text = stringResource(R.string.github_webhook_last_response, response),
                style = MaterialTheme.typography.bodySmall,
                color = if ((webhook.lastResponseCode ?: 0) >= 400) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp, top = 4.dp)
            )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 13.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}
