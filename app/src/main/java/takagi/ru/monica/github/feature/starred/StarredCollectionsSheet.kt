package takagi.ru.monica.github.feature.starred

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubSearchField
import takagi.ru.monica.github.component.GithubServiceStatusNotices
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubStarCategory

@Composable
fun StarredCollectionsSheet(
    state: StarredUiState,
    onAction: (StarredAction) -> Unit,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit
) {
    val categories = GithubStarCategory.entries
    val labels = listOf(
        stringResource(R.string.github_filter_all),
        stringResource(R.string.github_star_category_android),
        stringResource(R.string.github_star_category_kotlin),
        stringResource(R.string.github_star_category_tools)
    )

    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            GithubSheetHeader(
                title = stringResource(R.string.github_star_collections),
                subtitle = stringResource(R.string.github_star_collections_subtitle),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            when {
                state.requiresAuthentication -> GithubMessageState(
                    title = stringResource(R.string.github_star_sign_in_required),
                    actionLabel = stringResource(R.string.github_sign_in),
                    onAction = onSignIn
                )
                else -> {
                    GithubServiceStatusNotices(modifier = Modifier.padding(bottom = 12.dp))
                    GithubSearchField(
                        value = state.query,
                        onValueChange = { onAction(StarredAction.QueryChanged(it)) },
                        label = stringResource(R.string.github_search_starred)
                    )
                    Spacer(Modifier.height(12.dp))
                    GithubFilterRow(
                        labels = labels,
                        selectedIndex = categories.indexOf(state.selectedCategory),
                        onSelected = { onAction(StarredAction.CategorySelected(categories[it])) }
                    )
                    if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
                        items(state.visibleRepositories, key = { it.repository.id }) { item ->
                            GithubRepositoryRow(
                                repository = item.repository,
                                descriptionFallback = stringResource(R.string.github_no_description),
                                languageFallback = stringResource(R.string.github_unknown_language),
                                updatedFallback = stringResource(R.string.github_updated_recently),
                                trailingContent = {
                                    StarCategoryMenu(item.category) { category ->
                                        onAction(StarredAction.RepositoryCategorized(item.repository.id, category))
                                    }
                                },
                                onClick = { onOpenRepository(item.repository) }
                            )
                        }
                        item(key = "list-status") {
                            GithubPagedListStatus(
                                itemCount = state.visibleRepositories.size,
                                isInitialLoading = state.isLoading,
                                isLoadingMore = state.isLoadingMore,
                                hasError = state.error,
                                canLoadMore = state.canLoadMore,
                                errorMessage = stringResource(R.string.github_star_error),
                                emptyMessage = stringResource(R.string.github_no_starred_results),
                                onRetry = {
                                    onAction(
                                        if (state.repositories.isEmpty()) {
                                            StarredAction.Refresh
                                        } else {
                                            StarredAction.LoadMore
                                        }
                                    )
                                },
                                onLoadMore = { onAction(StarredAction.LoadMore) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarCategoryMenu(
    selected: GithubStarCategory,
    onSelected: (GithubStarCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(categoryLabel(selected), style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GithubStarCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(categoryLabel(category)) },
                    onClick = {
                        expanded = false
                        onSelected(category)
                    }
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(category: GithubStarCategory): String = when (category) {
    GithubStarCategory.ALL -> stringResource(R.string.github_star_category_uncategorized)
    GithubStarCategory.ANDROID -> stringResource(R.string.github_star_category_android)
    GithubStarCategory.KOTLIN -> stringResource(R.string.github_star_category_kotlin)
    GithubStarCategory.TOOLS -> stringResource(R.string.github_star_category_tools)
}
