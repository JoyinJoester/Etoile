package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

internal enum class GithubPagedListIndicator {
    ERROR,
    EMPTY,
    EMPTY_WITH_LOAD_MORE,
    LOADING_MORE,
    LOAD_MORE,
    NONE
}

internal fun githubPagedListIndicator(
    itemCount: Int,
    isInitialLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean
): GithubPagedListIndicator = when {
    hasError -> GithubPagedListIndicator.ERROR
    itemCount == 0 && !isInitialLoading && canLoadMore -> GithubPagedListIndicator.EMPTY_WITH_LOAD_MORE
    itemCount == 0 && !isInitialLoading -> GithubPagedListIndicator.EMPTY
    isLoadingMore -> GithubPagedListIndicator.LOADING_MORE
    canLoadMore -> GithubPagedListIndicator.LOAD_MORE
    else -> GithubPagedListIndicator.NONE
}

/**
 * Error, empty, and pagination affordances for a paged list.
 *
 * A list that owns its screen gets the full [GithubEmptyState] treatment. Pass [compact] for a
 * paginated section nested inside a longer detail page, where a centred icon block would outweigh
 * the content around it.
 */
@Composable
fun GithubPagedListStatus(
    itemCount: Int,
    isInitialLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    canLoadMore: Boolean,
    errorMessage: String,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector = Icons.Default.Inbox,
    emptyDescription: String? = null,
    compact: Boolean = false
) {
    when (
        githubPagedListIndicator(
            itemCount = itemCount,
            isInitialLoading = isInitialLoading,
            isLoadingMore = isLoadingMore,
            hasError = hasError,
            canLoadMore = canLoadMore
        )
    ) {
        // An error alongside existing rows is a note about the failed page, not a whole-surface
        // state, so it stays compact and leaves the loaded rows in place.
        GithubPagedListIndicator.ERROR -> if (compact || itemCount > 0) {
            GithubMessageState(
                title = errorMessage,
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = onRetry,
                modifier = modifier
            )
        } else {
            GithubEmptyState(
                title = errorMessage,
                icon = Icons.Default.CloudOff,
                accent = MaterialTheme.colorScheme.error,
                accentContainer = MaterialTheme.colorScheme.errorContainer,
                actionLabel = stringResource(R.string.github_retry),
                onAction = onRetry,
                modifier = modifier
            )
        }

        GithubPagedListIndicator.EMPTY -> EmptyIndicator(
            message = emptyMessage,
            icon = emptyIcon,
            description = emptyDescription,
            compact = compact,
            modifier = modifier
        )

        GithubPagedListIndicator.EMPTY_WITH_LOAD_MORE -> Column(modifier = modifier) {
            EmptyIndicator(
                message = emptyMessage,
                icon = emptyIcon,
                description = emptyDescription,
                compact = compact
            )
            GithubPaginationFooter(
                canLoadMore = true,
                isLoading = false,
                onLoadMore = onLoadMore
            )
        }

        GithubPagedListIndicator.LOADING_MORE -> GithubPaginationFooter(
            canLoadMore = false,
            isLoading = true,
            onLoadMore = onLoadMore,
            modifier = modifier
        )

        GithubPagedListIndicator.LOAD_MORE -> GithubPaginationFooter(
            canLoadMore = true,
            isLoading = false,
            onLoadMore = onLoadMore,
            modifier = modifier
        )

        GithubPagedListIndicator.NONE -> Unit
    }
}

@Composable
private fun EmptyIndicator(
    message: String,
    icon: ImageVector,
    description: String?,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (compact) {
        GithubMessageState(title = message, modifier = modifier)
    } else {
        GithubEmptyState(
            title = message,
            icon = icon,
            description = description,
            modifier = modifier
        )
    }
}

@Composable
fun GithubPaginationFooter(
    canLoadMore: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!canLoadMore && !isLoading) return
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.github_load_more))
            }
        }
    }
}
