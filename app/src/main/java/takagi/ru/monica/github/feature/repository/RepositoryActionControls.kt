package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRepository

@Composable
internal fun RepositoryActionControls(
    state: RepositoryDetailUiState,
    isSignedIn: Boolean,
    onAction: (RepositoryDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    var showForkConfirmation by androidx.compose.runtime.saveable.rememberSaveable(state.fullName, state.viewerAccountId) { mutableStateOf(false) }
    Column(modifier = modifier) {
        val viewer = state.viewerState
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            RepositoryQuickAction(
                label = stringResource(if (viewer?.isStarred == true) R.string.github_unstar_repository else R.string.github_star_repository),
                icon = Icons.Default.Star,
                count = state.details?.repository?.stars,
                selected = viewer?.isStarred == true,
                busy = state.isUpdatingStar,
                enabled = !isSignedIn || (viewer != null && !state.isUpdatingStar),
                onClick = { if (isSignedIn) onAction(RepositoryDetailAction.ToggleStar) else onSignIn() },
                modifier = Modifier.weight(1f)
            )
            RepositoryQuickAction(
                label = stringResource(if (viewer?.isWatching == true) R.string.github_unwatch_repository else R.string.github_watch_repository),
                icon = Icons.Default.Visibility,
                count = state.details?.watchers,
                selected = viewer?.isWatching == true,
                busy = state.isUpdatingWatch,
                enabled = !isSignedIn || (viewer != null && !state.isUpdatingWatch),
                onClick = { if (isSignedIn) onAction(RepositoryDetailAction.ToggleWatch) else onSignIn() },
                modifier = Modifier.weight(1f)
            )
            RepositoryQuickAction(
                label = stringResource(if (state.forkedRepository != null) R.string.github_open_fork else R.string.github_fork_repository),
                icon = Icons.AutoMirrored.Filled.CallSplit,
                count = state.details?.forks,
                selected = state.forkedRepository != null,
                busy = state.isForking,
                enabled = !state.isForking,
                onClick = {
                    val fork = state.forkedRepository
                    when {
                        !isSignedIn -> onSignIn()
                        fork != null -> onOpenRepository(fork)
                        else -> showForkConfirmation = true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (isSignedIn && state.isLoadingViewerState) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (isSignedIn && state.viewerStateError) {
            GithubMessageState(
                title = stringResource(R.string.github_repository_viewer_state_error),
                color = MaterialTheme.colorScheme.error,
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(RepositoryDetailAction.RetryViewerState) }
            )
        }
        state.starFailure?.let { ActionError(stringResource(R.string.github_star_update_error), repositoryActionFailureText(it)) }
        state.watchFailure?.let { ActionError(stringResource(R.string.github_watch_update_error), repositoryActionFailureText(it)) }
        state.forkFailure?.let { ActionError(stringResource(R.string.github_fork_error), repositoryActionFailureText(it)) }
        state.forkedRepository?.let { fork ->
            Text(stringResource(R.string.github_fork_success, fork.fullName),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))
        }

        if (showForkConfirmation && isSignedIn) {
            AlertDialog(
                onDismissRequest = { if (!state.isForking) showForkConfirmation = false },
                title = { Text(stringResource(R.string.github_fork_confirmation_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.github_fork_confirmation_message,
                            state.fullName
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showForkConfirmation = false
                            onAction(RepositoryDetailAction.Fork)
                        },
                        enabled = !state.isForking
                    ) {
                        Text(stringResource(R.string.github_create_fork))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForkConfirmation = false },
                        enabled = !state.isForking
                    ) {
                        Text(stringResource(R.string.github_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionError(message: String, reason: String) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun RepositoryQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    selected: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = GithubExpressiveShapes.control,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 92.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            count?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
    }
}
