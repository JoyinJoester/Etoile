package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAuthPromptCard
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubUserTile
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.domain.GithubUserSummary

/**
 * The list of accounts the viewer blocked. Reading it is the point of this page; lifting a block
 * happens on the blocked account's own profile, where the tile below leads.
 */
@Composable
fun GithubBlockedUsersScreen(
    state: GithubBlockedUsersUiState,
    onAction: (GithubBlockedUsersAction) -> Unit,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_blocked_users),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        if (state.requiresAuthentication) {
            GithubAuthPromptCard(
                title = stringResource(R.string.github_sign_in),
                description = stringResource(R.string.github_blocked_users_sign_in_required),
                actionLabel = stringResource(R.string.github_sign_in),
                icon = Icons.Default.Block,
                onAction = onSignIn,
                modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp)
            )
            return@GithubDetailScaffold
        }
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(GithubBlockedUsersAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                    GithubScreenIntro(
                        subtitle = stringResource(R.string.github_blocked_users_subtitle)
                    )
                }
                if (state.isLoading) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        GithubListLoadingState(
                            isLoading = true,
                            hasItems = state.users.isNotEmpty(),
                            row = GithubSkeletonRow.LIST
                        )
                    }
                }
                items(state.users, key = GithubUserSummary::login) { user ->
                    GithubUserTile(
                        login = user.login,
                        avatarUrl = user.avatarUrl,
                        supportingText = stringResource(R.string.github_user_blocked_short),
                        onClick = { onOpenUser(user.login) }
                    )
                }
                item(key = "list-status", span = { GridItemSpan(maxLineSpan) }) {
                    GithubPagedListStatus(
                        itemCount = state.users.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error != null,
                        canLoadMore = state.canLoadMore,
                        errorMessage = state.error?.let { userBlockFailureText(it) }
                            ?: stringResource(R.string.github_block_error),
                        emptyMessage = stringResource(R.string.github_blocked_users_empty),
                        onRetry = { onAction(GithubBlockedUsersAction.Retry) },
                        emptyIcon = Icons.Default.Block,
                        onLoadMore = { onAction(GithubBlockedUsersAction.LoadMore) }
                    )
                }
            }
        }
    }
}
