package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.domain.GithubRepositoryDetails

/** Shares its repository state with the parent entry, including account and permission changes. */
@Composable
fun RepositoryMoreScreen(
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenBranches: (GithubRepositoryDetails) -> Unit,
    onOpenTags: (GithubRepositoryDetails) -> Unit,
    onOpenCollaborators: (GithubRepositoryDetails) -> Unit,
    onOpenWebhooks: (GithubRepositoryDetails) -> Unit,
    onOpenActions: (GithubRepositoryDetails) -> Unit,
    onOpenDiscussions: (GithubRepositoryDetails) -> Unit,
    onOpenCommits: (GithubRepositoryDetails) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = state.details
    var showAdministration by rememberSaveable(state.fullName, state.viewerAccountId) { mutableStateOf(false) }
    GithubDetailScaffold(
        title = stringResource(R.string.github_repository_more),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        if (details == null && state.isLoadingDetails) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (details == null) {
            GithubMessageState(
                title = stringResource(R.string.github_repository_load_error),
                actionLabel = stringResource(R.string.github_retry),
                onAction = { onAction(RepositoryDetailAction.RetryDetails) },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            GithubCompactNavRow(Icons.Default.Forum, GithubSectionTints.discussions,
                                stringResource(R.string.github_discussions)) {
                                onOpenDiscussions(details)
                            }
                            GithubCompactNavRow(Icons.Default.PlayArrow, GithubSectionTints.actions,
                                stringResource(R.string.github_view_actions)) { onOpenActions(details) }
                            GithubCompactNavRow(Icons.Default.Code, GithubSectionTints.neutral,
                                stringResource(R.string.github_commits)) { onOpenCommits(details) }
                            GithubCompactNavRow(Icons.Default.AccountTree, GithubSectionTints.neutral,
                                stringResource(R.string.github_view_branches)) { onOpenBranches(details) }
                            GithubCompactNavRow(Icons.Default.LocalOffer, GithubSectionTints.neutral,
                                stringResource(R.string.github_view_tags)) { onOpenTags(details) }
                            GithubCompactNavRow(Icons.Default.Group, GithubSectionTints.neutral,
                                stringResource(R.string.github_view_collaborators), showDivider = false) {
                                onOpenCollaborators(details)
                            }
                        }
                    }
                }
                item {
                    GithubSectionHeader(stringResource(R.string.github_repository_information))
                    GithubMetadataRow(Icons.Default.AccountTree, stringResource(R.string.github_default_branch), details.defaultBranch)
                    details.license?.let {
                        GithubMetadataRow(Icons.Default.LocalOffer, stringResource(R.string.github_license), it)
                    }
                    details.repository.updatedAt?.let {
                        GithubMetadataRow(Icons.Default.Update, stringResource(R.string.github_updated_recently), githubRelativeTime(it))
                    }
                    if (state.isLoadingBranchProtection || state.branchProtectionError || state.branchProtection != null) {
                        RepositoryBranchProtection(state) { onAction(RepositoryDetailAction.RetryBranchProtection) }
                    }
                }
                if (state.canEditTopics || state.canAdminister || !isSignedIn) {
                    item {
                        GithubSectionHeader(stringResource(R.string.github_repository_settings))
                        if (state.canEditTopics) {
                            androidx.compose.runtime.key(state.fullName, state.viewerAccountId) {
                                RepositoryTopicsEditor(details.topics, state.isUpdatingTopics, state.topicsFailure,
                                    enabled = true, onUpdate = { onAction(RepositoryDetailAction.UpdateTopics(it)) })
                            }
                        }
                        if (state.canManageSettings) {
                            GithubCompactNavRow(Icons.Default.Settings, GithubSectionTints.neutral,
                                stringResource(R.string.github_repository_administration), showDivider = false) {
                                showAdministration = true
                            }
                        }
                        if (state.canAdminister || !isSignedIn) {
                            GithubCompactNavRow(Icons.Default.Link, GithubSectionTints.neutral,
                                stringResource(R.string.github_view_webhooks), showDivider = false) {
                                if (state.canAdminister) onOpenWebhooks(details) else onSignIn()
                            }
                        }
                    }
                }
                item {
                    RepositorySettingsMenu(state.fullName, onOpenExternal, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (showAdministration && details != null && state.canManageSettings) {
        androidx.compose.runtime.key(state.fullName, state.viewerAccountId) {
            RepositoryAdministrationDialog(details, state, onAction) { showAdministration = false }
        }
    }
}
