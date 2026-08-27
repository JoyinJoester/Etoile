package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubUserRow
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.navigation.GithubWebUrls

@Composable
fun RepositoryCollaboratorsScreen(
    state: RepositoryCollaboratorsUiState,
    onAction: (RepositoryCollaboratorsAction) -> Unit,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_collaborators),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.repositoryCollaboratorsSettings(state.fullName))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(RepositoryCollaboratorsAction.Search(it)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.github_search_collaborators)) },
                singleLine = true,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.filteredItems, key = { it.user.login }) { collaborator ->
                    GithubUserRow(
                        login = collaborator.user.login,
                        avatarUrl = collaborator.user.avatarUrl,
                        supportingText = collaboratorRoleLabel(collaborator),
                        onClick = { onOpenUser(collaborator.user.login) }
                    )
                }
                item(key = "collaborators-status") {
                    GithubPagedListStatus(
                        itemCount = state.filteredItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_collaborators_error),
                        emptyMessage = stringResource(R.string.github_no_collaborators),
                        onRetry = { onAction(RepositoryCollaboratorsAction.Retry) },
                        onLoadMore = { onAction(RepositoryCollaboratorsAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
private fun collaboratorRoleLabel(collaborator: GithubCollaborator): String = stringResource(
    when (collaborator.role) {
        GithubCollaboratorRole.READ -> R.string.github_role_read
        GithubCollaboratorRole.TRIAGE -> R.string.github_role_triage
        GithubCollaboratorRole.WRITE -> R.string.github_role_write
        GithubCollaboratorRole.MAINTAIN -> R.string.github_role_maintain
        GithubCollaboratorRole.ADMIN -> R.string.github_role_admin
        GithubCollaboratorRole.UNKNOWN -> R.string.github_role_unknown
    }
)
