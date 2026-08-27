package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRepository

@Composable
internal fun RepositoryActionControls(
    state: RepositoryDetailUiState,
    canWrite: Boolean,
    onAction: (RepositoryDetailAction) -> Unit,
    onSignIn: () -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        GithubSectionHeader(title = stringResource(R.string.github_repository_actions))
        if (!canWrite) {
            GithubMessageState(
                title = stringResource(R.string.github_sign_in_to_manage_repository),
                actionLabel = stringResource(R.string.github_sign_in),
                onAction = onSignIn
            )
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val viewerState = state.viewerState
                when {
                    viewerState != null -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { onAction(RepositoryDetailAction.ToggleStar) },
                                enabled = !state.isUpdatingStar,
                                modifier = Modifier.weight(1f),
                                shape = GithubExpressiveShapes.control
                            ) {
                                if (state.isUpdatingStar) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    stringResource(
                                        if (viewerState.isStarred) {
                                            R.string.github_unstar_repository
                                        } else {
                                            R.string.github_star_repository
                                        }
                                    )
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onAction(RepositoryDetailAction.ToggleWatch) },
                                enabled = !state.isUpdatingWatch,
                                modifier = Modifier.weight(1f),
                                shape = GithubExpressiveShapes.control
                            ) {
                                if (state.isUpdatingWatch) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Visibility, contentDescription = null)
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    stringResource(
                                        if (viewerState.isWatching) {
                                            R.string.github_unwatch_repository
                                        } else {
                                            R.string.github_watch_repository
                                        }
                                    )
                                )
                            }
                        }
                    }
                    state.isLoadingViewerState -> GithubCenteredProgress()
                    state.viewerStateError -> GithubMessageState(
                        title = stringResource(R.string.github_repository_viewer_state_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(RepositoryDetailAction.RetryViewerState) }
                    )
                }

                if (state.starError) {
                    ActionError(stringResource(R.string.github_star_update_error))
                }
                if (state.watchError) {
                    ActionError(stringResource(R.string.github_watch_update_error))
                }
                if (state.forkError) {
                    ActionError(stringResource(R.string.github_fork_error))
                }

                val forkedRepository = state.forkedRepository
                if (forkedRepository == null) {
                    Button(
                        onClick = { onAction(RepositoryDetailAction.Fork) },
                        enabled = !state.isForking,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = GithubExpressiveShapes.control
                    ) {
                        if (state.isForking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.github_create_fork))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.github_fork_success, forkedRepository.fullName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    TextButton(
                        onClick = { onOpenRepository(forkedRepository) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.github_open_fork))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 10.dp)
    )
}
