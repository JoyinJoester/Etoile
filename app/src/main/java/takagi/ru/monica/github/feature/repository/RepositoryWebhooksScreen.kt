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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.design.GithubExpressiveShapes
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = GithubRepositoryWebhook::id) { webhook ->
                    RepositoryWebhookRow(webhook)
                }
                item(key = "webhooks-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_webhooks_error),
                        emptyMessage = stringResource(R.string.github_no_webhooks),
                        onRetry = { onAction(RepositoryWebhooksAction.Retry) },
                        onLoadMore = { onAction(RepositoryWebhooksAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryWebhookRow(webhook: GithubRepositoryWebhook) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.compact,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (webhook.isActive) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                    contentDescription = stringResource(
                        if (webhook.isActive) R.string.github_webhook_active else R.string.github_webhook_inactive
                    ),
                    tint = if (webhook.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = webhook.events.joinToString(", ").ifBlank {
                    stringResource(R.string.github_webhook_no_events)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val response = listOfNotNull(
                webhook.lastResponseCode?.toString(),
                webhook.lastResponseStatus?.takeIf(String::isNotBlank),
                webhook.lastResponseMessage?.takeIf(String::isNotBlank)
            ).joinToString(" · ")
            if (response.isNotBlank()) {
                Text(
                    text = stringResource(R.string.github_webhook_last_response, response),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
