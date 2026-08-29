package takagi.ru.monica.github.feature.mywork

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubIssueSearchResultRow
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubSession

@Composable
fun MyConversationsScreen(
    kind: MyConversationsKind,
    state: MyConversationsUiState,
    session: GithubSession,
    onAction: (MyConversationsAction) -> Unit,
    onBack: () -> Unit,
    onOpenConversation: (GithubIssueSearchResult) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(
            when (kind) {
                MyConversationsKind.ISSUES -> R.string.github_my_issues
                MyConversationsKind.PULL_REQUESTS -> R.string.github_my_pull_requests
            }
        ),
        subtitle = stringResource(R.string.github_my_conversations_subtitle),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.requiresAuthentication && session is GithubSession.SignedOut -> {
                    GithubAuthPromptCard(
                        title = stringResource(R.string.github_sign_in),
                        description = stringResource(R.string.github_my_conversations_sign_in_description),
                        actionLabel = stringResource(R.string.github_sign_in),
                        icon = when (kind) {
                            MyConversationsKind.ISSUES -> Icons.Default.RadioButtonChecked
                            MyConversationsKind.PULL_REQUESTS -> Icons.AutoMirrored.Filled.CallSplit
                        },
                        onAction = onSignIn,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                else -> {
                    GithubListLoadingState(
                        isLoading = state.isLoading,
                        hasItems = state.items.isNotEmpty(),
                        row = GithubSkeletonRow.CARD,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(state.items, key = GithubIssueSearchResult::id) { result ->
                            GithubIssueSearchResultRow(
                                result = result,
                                onClick = { onOpenConversation(result) }
                            )
                        }
                        item(key = "list-status") {
                            GithubPagedListStatus(
                                itemCount = state.items.size,
                                isInitialLoading = state.isLoading,
                                isLoadingMore = state.isLoadingMore,
                                hasError = state.error,
                                canLoadMore = state.canLoadMore,
                                errorMessage = stringResource(R.string.github_my_conversations_error),
                                emptyMessage = stringResource(R.string.github_my_conversations_empty),
                                onRetry = { onAction(MyConversationsAction.Retry) },
                                emptyIcon = Icons.Default.Forum,
                                onLoadMore = { onAction(MyConversationsAction.LoadMore) }
                            )
                        }
                    }
                }
            }
        }
    }
}
