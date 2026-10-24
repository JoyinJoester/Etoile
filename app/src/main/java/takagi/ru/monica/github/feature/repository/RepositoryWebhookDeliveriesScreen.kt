package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubEmptyState
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.githubConfirmedAction
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.domain.GithubWebhookDelivery
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun RepositoryWebhookDeliveriesScreen(
    state: RepositoryWebhookDeliveriesUiState,
    onAction: (RepositoryWebhookDeliveriesAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_webhook_deliveries),
        subtitle = stringResource(R.string.github_webhook_subject, state.fullName, state.webhookId),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.repositoryWebhookSettings(state.fullName, state.webhookId))
            }
        },
        modifier = modifier
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(RepositoryWebhookDeliveriesAction.Refresh) },
            enabled = !state.isReading && state.viewerId != null,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                GithubListLoadingState(
                    isLoading = state.isLoading,
                    hasItems = state.items.isNotEmpty(),
                    row = GithubSkeletonRow.COMPACT,
                    modifier = Modifier.padding(16.dp)
                )
                GithubAdaptiveGrid(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    githubFullSpanItem(key = "delivery-feedback") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.acceptedDeliveryId?.let {
                                Text(
                                    stringResource(R.string.github_webhook_redelivery_accepted, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            state.redeliveryFailure?.let {
                                Text(
                                    stringResource(R.string.github_webhook_redelivery_failed, repositoryActionFailureText(it)),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                    items(state.items, key = GithubWebhookDelivery::id) { delivery ->
                        WebhookDeliveryRow(delivery, state, onAction)
                    }
                    githubFullSpanItem(key = "delivery-status") {
                        if (state.viewerId == null && !state.isLoading) {
                            GithubEmptyState(
                                title = stringResource(R.string.github_webhook_deliveries_forbidden),
                                icon = Icons.Default.Webhook,
                                actionLabel = stringResource(R.string.github_sign_in),
                                onAction = onSignIn
                            )
                        } else GithubPagedListStatus(
                            itemCount = state.items.size,
                            isInitialLoading = state.isLoading,
                            isLoadingMore = state.isLoadingMore,
                            hasError = state.loadFailure != null,
                            canLoadMore = state.canLoadMore,
                            errorMessage = when (state.loadFailure) {
                                RepositoryWriteFailure.Forbidden -> stringResource(R.string.github_webhook_deliveries_forbidden)
                                RepositoryWriteFailure.NotFound -> stringResource(R.string.github_webhook_deliveries_not_found)
                                RepositoryWriteFailure.RateLimited -> stringResource(R.string.github_action_rate_limited)
                                else -> stringResource(R.string.github_webhook_deliveries_error)
                            },
                            emptyMessage = stringResource(R.string.github_webhook_deliveries_empty),
                            emptyIcon = Icons.Default.Webhook,
                            onRetry = { onAction(RepositoryWebhookDeliveriesAction.Retry) },
                            onLoadMore = { onAction(RepositoryWebhookDeliveriesAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhookDeliveryRow(
    delivery: GithubWebhookDelivery,
    state: RepositoryWebhookDeliveriesUiState,
    onAction: (RepositoryWebhookDeliveriesAction) -> Unit
) {
    val requestRedelivery = key(state.viewerId) {
        githubConfirmedAction(
            title = stringResource(R.string.github_webhook_redeliver),
            message = stringResource(R.string.github_webhook_redeliver_confirm),
            subject = stringResource(R.string.github_webhook_delivery_subject, state.fullName, state.webhookId, delivery.id),
            enabled = state.canRedeliver,
            onConfirm = { onAction(RepositoryWebhookDeliveriesAction.Redeliver(delivery.id)) }
        )
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            listOfNotNull(delivery.event?.takeIf(String::isNotBlank), delivery.action?.takeIf(String::isNotBlank))
                .joinToString(" · ").ifBlank { stringResource(R.string.github_webhook_delivery_event_unknown) },
            style = MaterialTheme.typography.titleMedium
        )
        SelectionContainer {
            Text(stringResource(R.string.github_webhook_delivery_id, delivery.id), style = MaterialTheme.typography.labelMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            delivery.statusCode?.let {
                Text(
                    stringResource(R.string.github_webhook_delivery_http, it),
                    color = if (it >= 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            delivery.durationMs?.let {
                Text(stringResource(R.string.github_webhook_delivery_duration, it), style = MaterialTheme.typography.labelMedium)
            }
            if (delivery.redelivery) {
                Text(stringResource(R.string.github_webhook_delivery_is_redelivery), style = MaterialTheme.typography.labelMedium)
            }
        }
        delivery.status?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        delivery.deliveredAt?.takeIf(String::isNotBlank)?.let {
            Text(githubRelativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        delivery.guid?.takeIf(String::isNotBlank)?.let {
            SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.canManage) {
            TextButton(onClick = requestRedelivery, enabled = state.canRedeliver) {
                Text(stringResource(
                    if (state.redeliveringId == delivery.id) R.string.github_webhook_redelivering
                    else R.string.github_webhook_redeliver
                ))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}
