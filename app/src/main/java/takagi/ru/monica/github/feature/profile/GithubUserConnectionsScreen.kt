package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubUserRow
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun GithubUserConnectionsScreen(
    state: GithubUserConnectionsUiState,
    onAction: (GithubUserConnectionsAction) -> Unit,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (state.kind) {
        GithubUserConnectionKind.FOLLOWERS -> stringResource(R.string.github_followers)
        GithubUserConnectionKind.FOLLOWING -> stringResource(R.string.github_following)
    }
    val errorMessage = when (state.kind) {
        GithubUserConnectionKind.FOLLOWERS -> stringResource(R.string.github_followers_error)
        GithubUserConnectionKind.FOLLOWING -> stringResource(R.string.github_following_error)
    }
    val emptyMessage = when (state.kind) {
        GithubUserConnectionKind.FOLLOWERS -> stringResource(R.string.github_no_followers)
        GithubUserConnectionKind.FOLLOWING -> stringResource(R.string.github_not_following_anyone)
    }
    val externalUrl = remember(state.login, state.kind) {
        when (state.kind) {
            GithubUserConnectionKind.FOLLOWERS -> GithubWebUrls.userFollowers(state.login)
            GithubUserConnectionKind.FOLLOWING -> GithubWebUrls.userFollowing(state.login)
        }
    }

    GithubDetailScaffold(
        title = title,
        subtitle = stringResource(R.string.github_user_handle, state.login),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton(onClick = { onOpenExternal(externalUrl) })
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(GithubUserConnectionsAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (state.isLoading) {
                    item(key = "loading") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                items(state.users, key = GithubUserSummary::login) { user ->
                    GithubUserRow(
                        login = user.login,
                        avatarUrl = user.avatarUrl,
                        supportingText = stringResource(R.string.github_user),
                        onClick = { onOpenUser(user.login) }
                    )
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.users.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = errorMessage,
                        emptyMessage = emptyMessage,
                        onRetry = { onAction(GithubUserConnectionsAction.Retry) },
                        onLoadMore = { onAction(GithubUserConnectionsAction.LoadMore) }
                    )
                }
            }
        }
    }
}
