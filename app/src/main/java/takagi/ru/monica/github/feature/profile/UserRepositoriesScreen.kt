package takagi.ru.monica.github.feature.profile

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Close
import takagi.ru.monica.github.component.GithubSearchField
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun UserRepositoriesScreen(
    state: UserRepositoriesUiState,
    accountLogin: String,
    onAction: (UserRepositoriesAction) -> Unit,
    onBack: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenExternal: (String) -> Unit,
    onCreateRepository: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable(accountLogin) { mutableStateOf("") }
    val search = query.trim()
    val visibleItems = if (search.isEmpty()) state.items else state.items.filter {
        it.fullName.contains(search, ignoreCase = true) ||
            it.description.orEmpty().contains(search, ignoreCase = true) ||
            it.language.orEmpty().contains(search, ignoreCase = true)
    }
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_your_repositories),
        subtitle = "@$accountLogin",
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onCreateRepository) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.github_create_repository))
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.userRepositories(accountLogin))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(UserRepositoriesAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.items.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                githubFullSpanItem(key = "search") {
                    GithubSearchField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.github_search_repositories),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.github_clear_search))
                                }
                            }
                        } else null
                    )
                    Text(
                        text = stringResource(R.string.github_repository_filter_scope, visibleItems.size, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                items(visibleItems, key = GithubRepository::id) { repository ->
                    GithubRepositoryRow(
                        repository = repository,
                        descriptionFallback = stringResource(R.string.github_no_description),
                        languageFallback = stringResource(R.string.github_unknown_language),
                        updatedFallback = stringResource(R.string.github_updated_recently),
                        onClick = { onOpenRepository(repository) }
                    )
                }
                githubFullSpanItem(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = visibleItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_user_repositories_error),
                        emptyMessage = stringResource(
                            if (search.isEmpty()) R.string.github_no_repositories
                            else R.string.github_repository_filter_empty
                        ),
                        onRetry = { onAction(UserRepositoriesAction.Retry) },
                        emptyIcon = Icons.Default.Folder,
                        onLoadMore = { onAction(UserRepositoriesAction.LoadMore) }
                    )
                }
            }
          }
        }
    }
}
