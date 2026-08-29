package takagi.ru.monica.github.feature.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import takagi.ru.monica.github.component.GithubIssueSearchResultRow
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubScreenIntro
import takagi.ru.monica.github.component.GithubSearchField
import takagi.ru.monica.github.component.GithubSearchScopePicker
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubUserRow
import takagi.ru.monica.github.component.GithubWrappedFilterRow
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onAction: (ExploreAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenConversation: (GithubIssueSearchResult) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = ExploreTopic.entries
    val topicLabels = listOf(
        stringResource(R.string.github_topic_for_you),
        stringResource(R.string.github_topic_kotlin),
        stringResource(R.string.github_topic_android),
        stringResource(R.string.github_topic_compose)
    )
    val searchKinds = ExploreSearchKind.entries
    val searchKindLabels = listOf(
        stringResource(R.string.github_search_repositories),
        stringResource(R.string.github_search_users),
        stringResource(R.string.github_search_code),
        stringResource(R.string.github_search_issues),
        stringResource(R.string.github_search_pull_requests)
    )
    val searchLabel = when (state.searchKind) {
        ExploreSearchKind.REPOSITORIES -> stringResource(R.string.github_search_repositories)
        ExploreSearchKind.USERS -> stringResource(R.string.github_search_users)
        ExploreSearchKind.CODE -> stringResource(R.string.github_search_code)
        ExploreSearchKind.ISSUES -> stringResource(R.string.github_search_issues)
        ExploreSearchKind.PULL_REQUESTS -> stringResource(R.string.github_search_pull_requests)
    }
    val resultTitle = when {
        state.isCurated -> stringResource(R.string.github_trending_repositories)
        state.searchKind == ExploreSearchKind.USERS -> stringResource(R.string.github_search_users)
        state.searchKind == ExploreSearchKind.CODE -> stringResource(R.string.github_search_code)
        state.searchKind == ExploreSearchKind.ISSUES -> stringResource(R.string.github_search_issues)
        state.searchKind == ExploreSearchKind.PULL_REQUESTS ->
            stringResource(R.string.github_search_pull_requests)
        else -> stringResource(R.string.github_search_results)
    }

    // One scroll container keeps the search controls and results in the same
    // reading order and avoids a cramped fixed header on small phones.
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
    ) {
        item(key = "intro") {
            GithubScreenIntro(
                subtitle = stringResource(R.string.github_explore_subtitle)
            )
        }
        item(key = "search") {
            GithubSearchField(
                value = state.query,
                onValueChange = { onAction(ExploreAction.QueryChanged(it)) },
                label = stringResource(R.string.github_search_placeholder),
                modifier = Modifier.padding(top = 8.dp),
                trailingIcon = if (state.query.isNotBlank()) {
                    {
                        IconButton(onClick = { onAction(ExploreAction.QueryChanged("")) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.github_clear_search)
                            )
                        }
                    }
                } else {
                    null
                }
            )
        }
        item(key = "search-scope") {
            GithubSearchScopePicker(
                label = stringResource(R.string.github_search_scope),
                selectedLabel = searchLabel,
                options = searchKindLabels,
                selectedIndex = searchKinds.indexOf(state.searchKind),
                onSelected = { onAction(ExploreAction.SearchKindSelected(searchKinds[it])) },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (state.searchKind == ExploreSearchKind.REPOSITORIES) {
            item(key = "topics") {
                GithubWrappedFilterRow(
                    labels = topicLabels,
                    selectedIndex = topics.indexOf(state.selectedTopic),
                    onSelected = { onAction(ExploreAction.TopicSelected(topics[it])) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        item(key = "results-header") {
            GithubSectionHeader(title = resultTitle, compact = true)
        }
        if (state.isLoading) {
            item(key = "loading") {
                GithubListLoadingState(
                    isLoading = true,
                    hasItems = state.itemCount > 0,
                    row = GithubSkeletonRow.LIST,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        when (state.searchKind) {
            ExploreSearchKind.REPOSITORIES -> items(state.repositories, key = GithubRepository::id) { repository ->
                GithubRepositoryRow(
                    repository = repository,
                    descriptionFallback = stringResource(R.string.github_no_description),
                    languageFallback = stringResource(R.string.github_unknown_language),
                    updatedFallback = stringResource(R.string.github_updated_recently),
                    onClick = { onOpenRepository(repository) }
                )
            }
            ExploreSearchKind.USERS -> items(state.users, key = GithubUserSearchResult::id) { user ->
                GithubUserSearchRow(user = user, onOpenUser = onOpenUser)
            }
            ExploreSearchKind.CODE -> items(state.code, key = GithubCodeSearchResult::id) { result ->
                GithubCodeSearchRow(result = result, onOpenExternal = onOpenExternal)
            }
            ExploreSearchKind.ISSUES,
            ExploreSearchKind.PULL_REQUESTS -> items(
                state.conversations,
                key = GithubIssueSearchResult::id
            ) { result ->
                GithubIssueSearchResultRow(
                    result = result,
                    onClick = { onOpenConversation(result) }
                )
            }
        }
        item(key = "list-status") {
            GithubPagedListStatus(
                itemCount = state.itemCount,
                isInitialLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                hasError = state.error,
                canLoadMore = state.canLoadMore,
                errorMessage = stringResource(R.string.github_search_error),
                emptyMessage = stringResource(
                    if (state.query.isBlank() && state.searchKind != ExploreSearchKind.REPOSITORIES) {
                        R.string.github_search_enter_query
                    } else {
                        R.string.github_no_search_results
                    }
                ),
                onRetry = { onAction(ExploreAction.Retry) },
                emptyIcon = Icons.Default.Search,
                onLoadMore = { onAction(ExploreAction.LoadMore) }
            )
        }
    }
}

@Composable
private fun GithubUserSearchRow(
    user: GithubUserSearchResult,
    onOpenUser: (String) -> Unit
) {
    GithubUserRow(
        login = user.login,
        avatarUrl = user.avatarUrl,
        supportingText = if (user.accountType.equals("Organization", ignoreCase = true)) {
            stringResource(R.string.github_organization)
        } else {
            stringResource(R.string.github_user)
        },
        onClick = { onOpenUser(user.login) }
    )
}

@Composable
private fun GithubCodeSearchRow(
    result: GithubCodeSearchResult,
    onOpenExternal: (String) -> Unit
) {
    Surface(
        onClick = { onOpenExternal(result.htmlUrl) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = result.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 6.dp)
            )
            Text(
                text = result.repositoryFullName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 6.dp)
            )
        }
    }
}
