package takagi.ru.monica.github.feature.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubMetric
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.githubSemanticColors
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubNotificationReason

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    state: InboxUiState,
    onAction: (InboxAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenNotification: (GithubNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    var unsubscribeCandidate by remember { mutableStateOf<GithubNotification?>(null) }
    val filters = InboxFilter.entries
    val labels = listOf(
        stringResource(R.string.github_filter_all),
        stringResource(R.string.github_filter_mentions),
        stringResource(R.string.github_filter_review)
    )

    GithubPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(InboxAction.Refresh) },
        modifier = modifier.fillMaxSize(),
        enabled = !state.requiresAuthentication &&
            !state.isLoading &&
            !state.isLoadingMore &&
            !state.isTriaging
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item(key = "header") {
                GithubScreenIntro(
                    subtitle = stringResource(R.string.github_inbox_subtitle),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (state.requiresAuthentication) {
                    GithubAuthPromptCard(
                        title = stringResource(R.string.github_sign_in),
                        description = stringResource(R.string.github_inbox_sign_in_required),
                        actionLabel = stringResource(R.string.github_sign_in),
                        icon = Icons.Default.Notifications,
                        onAction = onSignIn
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GithubMetric(
                            state.needsAttentionCount.toString(),
                            stringResource(R.string.github_needs_attention),
                            MaterialTheme.colorScheme.primary,
                            Modifier.weight(1f),
                            compact = true
                        )
                        GithubMetric(
                            state.unreadIds.size.toString(),
                            stringResource(R.string.github_unread),
                            MaterialTheme.colorScheme.tertiary,
                            Modifier.weight(1f),
                            compact = true
                        )
                        GithubMetric(
                            state.assignedCount.toString(),
                            stringResource(R.string.github_assigned),
                            MaterialTheme.colorScheme.secondary,
                            Modifier.weight(1f),
                            compact = true
                        )
                    }
                    GithubFilterRow(
                        labels = labels,
                        selectedIndex = filters.indexOf(state.selectedFilter),
                        onSelected = { onAction(InboxAction.SelectFilter(filters[it])) },
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    GithubSectionHeader(
                        title = stringResource(R.string.github_recent_activity),
                        action = stringResource(R.string.github_mark_all_read),
                        onAction = { onAction(InboxAction.MarkAllRead) },
                        compact = true
                    )
                }
            }

            if (state.isLoading && !state.requiresAuthentication) {
                item(key = "loading") {
                    GithubListLoadingState(
                        isLoading = true,
                        hasItems = state.visibleItems.isNotEmpty(),
                        row = GithubSkeletonRow.LIST
                    )
                }
            }

            items(state.visibleItems, key = GithubNotification::id) { item ->
                InboxNotificationRow(
                    item = item,
                    unread = item.id in state.unreadIds,
                    isTriageBusy = item.id in state.triageBusyIds,
                    hasTriageError = item.id in state.triageErrorIds,
                    onClick = {
                        onAction(InboxAction.OpenNotification(item.id))
                        onOpenNotification(item)
                    },
                    onMarkDone = { onAction(InboxAction.MarkDone(item.id)) },
                    onUnsubscribe = { unsubscribeCandidate = item },
                    modifier = Modifier.animateItem()
                )
            }

            if (state.actionError && !state.error && !state.requiresAuthentication) {
                item(key = "action-error") {
                    GithubMessageState(
                        title = stringResource(R.string.github_notifications_action_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(InboxAction.Refresh) }
                    )
                }
            }

            if (!state.requiresAuthentication) {
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.visibleItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_notifications_error),
                        emptyMessage = stringResource(R.string.github_inbox_empty),
                        onRetry = {
                            onAction(
                                if (state.items.isEmpty() || state.refreshError) {
                                    InboxAction.Refresh
                                } else {
                                    InboxAction.LoadMore
                                }
                            )
                        },
                        onLoadMore = { onAction(InboxAction.LoadMore) },
                        emptyIcon = Icons.Default.Inbox
                    )
                }
            }
        }
    }
    unsubscribeCandidate?.let { notification ->
        AlertDialog(
            onDismissRequest = { unsubscribeCandidate = null },
            title = { Text(stringResource(R.string.github_unsubscribe_notification_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.github_unsubscribe_notification_message,
                        notification.title
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        unsubscribeCandidate = null
                        onAction(InboxAction.Unsubscribe(notification.id))
                    }
                ) {
                    Text(stringResource(R.string.github_unsubscribe))
                }
            },
            dismissButton = {
                TextButton(onClick = { unsubscribeCandidate = null }) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

@Composable
private fun InboxNotificationRow(
    item: GithubNotification,
    unread: Boolean,
    isTriageBusy: Boolean,
    hasTriageError: Boolean,
    onClick: () -> Unit,
    onMarkDone: () -> Unit,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val semanticColors = githubSemanticColors()
    val accent = when (item.reason) {
        GithubNotificationReason.REVIEW_REQUESTED -> semanticColors.review
        GithubNotificationReason.MENTION -> semanticColors.mention
        GithubNotificationReason.ASSIGN -> semanticColors.assigned
        else -> semanticColors.release
    }

    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.padding(top = 4.dp).size(10.dp).background(if (unread) accent else Color.Transparent, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.repository, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                Text(item.subjectType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(githubRelativeTime(item.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(contentAlignment = Alignment.Center) {
                    if (isTriageBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 6.dp).size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.github_notification_more_actions)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.github_notification_mark_done)) },
                                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onMarkDone()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.github_unsubscribe)) },
                                leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onUnsubscribe()
                                }
                            )
                        }
                    }
                }
            }
        }
        if (hasTriageError) {
            Text(
                text = stringResource(R.string.github_notification_triage_error),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 22.dp, top = 8.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 15.dp, start = 22.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}
