package takagi.ru.monica.github.feature.repository

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.component.GithubTechnicalSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalTag
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
    onSignIn: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showingContributors = state.items.any(GithubCollaborator::isContributor)
    val snackbarHostState = remember { SnackbarHostState() }
    // A write either lands with its own sentence or explains why GitHub refused it.
    val feedbackMessage = state.outcome?.let { collaboratorOutcomeText(it) }
        ?: state.failure?.let { repositoryActionFailureText(it) }
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onAction(RepositoryCollaboratorsAction.DismissFeedback)
        }
    }
    var pendingRemoval by remember(state.fullName, state.viewerAccountId, state.canManage) { mutableStateOf<String?>(null) }
    var pendingRole by remember(state.fullName, state.viewerAccountId, state.canManage) {
        mutableStateOf<Pair<String, GithubCollaboratorRole>?>(null)
    }
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = stringResource(R.string.github_collaborators),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.canManage) {
                IconButton(onClick = onInvite) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = stringResource(R.string.github_invite_collaborator)
                    )
                }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.repositoryCollaboratorsSettings(state.fullName))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(RepositoryCollaboratorsAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingMore && !state.isWriting,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(RepositoryCollaboratorsAction.Search(it)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.github_search_collaborators)) },
                singleLine = true,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            if (!state.canManage && !state.isLoading) {
                RepositoryWriteAccessHint(
                    viewerLogin = state.viewerLogin,
                    viewerRole = state.viewerRole,
                    onSignIn = onSignIn,
                    gate = GithubCollaboratorRole::canAdmin,
                    deniedMessage = R.string.github_collaborators_admin_only,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.filteredItems.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (showingContributors && !state.isLoading) {
                GithubTechnicalSectionHeader(
                    label = stringResource(R.string.github_collaborators_fallback_hint),
                    trailing = {
                        GithubTechnicalTag(
                            label = stringResource(R.string.github_contributor_role)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalSpacing = 10.dp
            ) {
                items(state.filteredItems, key = { it.user.login }) { collaborator ->
                    GithubCollaboratorRow(
                        collaborator = collaborator,
                        manageable = state.isManageable(collaborator),
                        isWriting = state.isWriting,
                        onClick = { onOpenUser(collaborator.user.login) },
                        onChangeRole = { role ->
                            pendingRole = collaborator.user.login to role
                        },
                        onRemove = { pendingRemoval = collaborator.user.login }
                    )
                }
                item(key = "collaborators-status", span = { GridItemSpan(maxLineSpan) }) {
                    GithubPagedListStatus(
                        itemCount = state.filteredItems.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_collaborators_error),
                        emptyMessage = stringResource(R.string.github_no_collaborators),
                        onRetry = { onAction(RepositoryCollaboratorsAction.Retry) },
                        emptyIcon = Icons.Default.Group,
                        onLoadMore = { onAction(RepositoryCollaboratorsAction.LoadMore) }
                    )
                }
            }
          }
        }
    }
    pendingRole?.let { (login, role) ->
        val collaborator = state.items.firstOrNull { it.user.login == login }
        if (collaborator != null && state.isManageable(collaborator)) {
            AlertDialog(
                onDismissRequest = { pendingRole = null },
                title = { Text(stringResource(R.string.github_change_permission)) },
                text = { Text(stringResource(R.string.github_confirm_collaborator_role, login, state.fullName, collaboratorRoleText(role))) },
                confirmButton = {
                    TextButton(enabled = !state.isWriting, onClick = {
                        pendingRole = null
                        onAction(RepositoryCollaboratorsAction.ChangeRole(login, role))
                    }) { Text(stringResource(R.string.github_comment_confirm)) }
                },
                dismissButton = { TextButton(onClick = { pendingRole = null }) { Text(stringResource(R.string.github_cancel)) } }
            )
        }
    }
    pendingRemoval?.let { login ->
        val collaborator = state.items.firstOrNull { it.user.login == login }
        if (collaborator != null && state.isManageable(collaborator)) AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.github_remove_collaborator_title, login)) },
            text = {
                Text(stringResource(R.string.github_remove_collaborator_message, login, state.fullName))
            },
            confirmButton = {
                TextButton(enabled = !state.isWriting, onClick = {
                    pendingRemoval = null
                    onAction(RepositoryCollaboratorsAction.Remove(login))
                }) {
                    Text(
                        text = stringResource(R.string.github_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

/** Keep identity and contribution/permission information together in one scan line. */
@Composable
private fun GithubCollaboratorRow(
    collaborator: GithubCollaborator,
    manageable: Boolean,
    isWriting: Boolean,
    onClick: () -> Unit,
    onChangeRole: (GithubCollaboratorRole) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.control,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GithubAvatar(login = collaborator.user.login, avatarUrl = collaborator.user.avatarUrl, size = 44.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(collaborator.user.login, Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (collaborator.isContributor) {
                    Text(formatContributions(collaborator.contributions ?: 0), Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(collaboratorRoleLabel(collaborator), Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (manageable) {
                CollaboratorManageMenu(collaborator.role, !isWriting, onChangeRole, onRemove)
            }
        }
    }
}

@Composable
private fun CollaboratorManageMenu(
    currentRole: GithubCollaboratorRole,
    enabled: Boolean,
    onChangeRole: (GithubCollaboratorRole) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.github_manage_collaborator)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { GithubTechnicalLabel(text = stringResource(R.string.github_change_permission)) },
                onClick = {},
                enabled = false
            )
            // One endpoint covers every level, so the menu is the whole permission vocabulary.
            GithubCollaboratorRole.assignable.forEach { role ->
                DropdownMenuItem(
                    text = { Text(collaboratorRoleText(role)) },
                    enabled = role != currentRole,
                    onClick = {
                        expanded = false
                        onChangeRole(role)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.github_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun formatContributions(contributions: Int): String = stringResource(
    R.string.github_contributions_count, contributions
)

@Composable
private fun collaboratorRoleLabel(collaborator: GithubCollaborator): String = when {
    collaborator.isContributor -> stringResource(R.string.github_contributor_role)
    else -> collaboratorRoleText(collaborator.role)
}
