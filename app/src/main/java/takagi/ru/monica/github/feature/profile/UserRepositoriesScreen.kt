package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
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
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_your_repositories),
        subtitle = "@$accountLogin",
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.userRepositories(accountLogin))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.items, key = GithubRepository::id) { repository ->
                    GithubRepositoryRow(
                        repository = repository,
                        descriptionFallback = stringResource(R.string.github_no_description),
                        languageFallback = stringResource(R.string.github_unknown_language),
                        updatedFallback = stringResource(R.string.github_updated_recently),
                        onClick = { onOpenRepository(repository) }
                    )
                }
                item(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_user_repositories_error),
                        emptyMessage = stringResource(R.string.github_no_repositories),
                        onRetry = { onAction(UserRepositoriesAction.Retry) },
                        onLoadMore = { onAction(UserRepositoriesAction.LoadMore) }
                    )
                }
            }
        }
    }
}
