package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryFeature

import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings

@Composable
internal fun RepositoryAdministrationButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.github_repository_administration))
    }
}

private data class SettingsRequest(
    val action: RepositoryDetailAction,
    val title: Int,
    val message: Int,
    val requiresName: Boolean = true
)

private data class FeatureRow(
    val feature: GithubRepositoryFeature,
    val icon: ImageVector,
    val label: Int,
    val enabled: Boolean
)

@Composable
internal fun RepositoryAdministrationDialog(
    details: GithubRepositoryDetails,
    state: RepositoryDetailUiState,
    onAction: (RepositoryDetailAction) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.canManageSettings) return
    var confirmation by remember { mutableStateOf("") }
    var visibilityMenu by remember { mutableStateOf(false) }
    var submittingPending by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SettingsRequest?>(null) }
    var descriptionDraft by remember { mutableStateOf<String?>(null) }
    var submittingDescription by remember { mutableStateOf(false) }
    // The dialog holds its draft open until the repository reply lands, so a refused write keeps what the admin typed.
    LaunchedEffect(submittingDescription, state.isUpdatingSettings, state.settingsFailure) {
        if (submittingDescription && !state.isUpdatingSettings && state.settingsFailure == null) {
            submittingDescription = false
            descriptionDraft = null
        }
    }
    var branchConfirmation by remember { mutableStateOf("") }
    var defaultBranchDraft by remember { mutableStateOf<String?>(null) }
    var submittingDefaultBranch by remember { mutableStateOf(false) }
    LaunchedEffect(submittingDefaultBranch, state.isUpdatingSettings, state.settingsFailure) {
        if (submittingDefaultBranch && !state.isUpdatingSettings && state.settingsFailure == null) {
            submittingDefaultBranch = false
            defaultBranchDraft = null
        }
    }
    LaunchedEffect(submittingPending, state.isUpdatingSettings, state.settingsFailure) {
        if (submittingPending && !state.isUpdatingSettings) {
            submittingPending = false
            if (state.settingsFailure == null) pending = null
        }
    }
    LaunchedEffect(pending) { confirmation = "" }
    LaunchedEffect(details.repository.isPrivate, details.isArchived, details.defaultBranch, details.features) {
        if (!submittingPending) pending = null
    }
    val busy = state.isUpdatingSettings || state.isLoadingDetails || state.isRefreshing
    val isPrivate = details.repository.isPrivate
    val fullName = details.repository.fullName
    val featureRows = listOf(
        FeatureRow(
            feature = GithubRepositoryFeature.Issues,
            icon = Icons.Default.BugReport,
            label = R.string.github_feature_issues,
            enabled = details.features.hasIssues
        ),
        FeatureRow(
            feature = GithubRepositoryFeature.Wiki,
            icon = Icons.Default.MenuBook,
            label = R.string.github_feature_wiki,
            enabled = details.features.hasWiki
        ),
        FeatureRow(
            feature = GithubRepositoryFeature.Projects,
            icon = Icons.Default.AccountTree,
            label = R.string.github_feature_projects,
            enabled = details.features.hasProjects
        )
    )
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!state.isUpdatingSettings) onDismiss() }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.github_repository_administration), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.github_web_sign_in_close))
                    }
                }
                Text(fullName, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                if (state.isUpdatingSettings) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box {
                AdministrationEditRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.github_repository_visibility),
                    trailingIcon = Icons.Default.ArrowDropDown,
                    summary = stringResource(
                    if (isPrivate) R.string.github_visibility_private_label else R.string.github_visibility_public_label
                    ),
                    // GitHub rejects every other repository edit while it is archived.
                    enabled = !busy && !details.isArchived,
                    onClick = { visibilityMenu = true }
                )
                DropdownMenu(expanded = visibilityMenu, onDismissRequest = { visibilityMenu = false }) {
                    listOf(false, true).forEach { target ->
                        DropdownMenuItem(
                            text = { Text(stringResource(if (target) R.string.github_visibility_private_label else R.string.github_visibility_public_label)) },
                            enabled = target != isPrivate && !busy && !details.isArchived,
                            onClick = {
                                visibilityMenu = false
                                pending = SettingsRequest(
                                    action = RepositoryDetailAction.SetVisibility(target),
                                    title = if (target) R.string.github_make_private else R.string.github_make_public,
                                    message = if (target) R.string.github_make_private_message else R.string.github_make_public_message
                                )
                            }
                        )
                    }
                }
                }
                AdministrationEditRow(
                    icon = Icons.Default.Archive,
                    title = stringResource(R.string.github_archive_repository),
                    summary = stringResource(
                        if (details.isArchived) R.string.github_archived_repository else R.string.github_active_repository
                    ),
                    enabled = !busy,
                    onClick = {
                        val target = !details.isArchived
                        pending = SettingsRequest(
                            action = RepositoryDetailAction.SetArchived(target),
                            title = if (target) R.string.github_archive_repository else R.string.github_unarchive_repository,
                            message = if (target) {
                                R.string.github_archive_message
                            } else {
                                R.string.github_unarchive_message
                            }
                        )
                    }
                )
                featureRows.forEach { row ->
                    AdministrationToggle(
                        icon = row.icon,
                        title = stringResource(row.label),
                        summary = stringResource(
                            if (row.enabled) R.string.github_enabled else R.string.github_disabled
                        ),
                        checked = row.enabled,
                        enabled = !busy && !details.isArchived,
                        onClick = {
                            pending = SettingsRequest(
                                action = RepositoryDetailAction.SetFeature(row.feature, !row.enabled),
                                title = row.label,
                                message = if (row.enabled) R.string.github_disable_feature_confirm else R.string.github_enable_feature_confirm,
                                requiresName = false
                            )
                        }
                    )
                }
                AdministrationEditRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.github_repository_description),
                    summary = details.repository.description
                        ?: stringResource(R.string.github_no_description),
                    enabled = !busy && !details.isArchived,
                    onClick = { descriptionDraft = details.repository.description.orEmpty() }
                )
                AdministrationEditRow(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    title = stringResource(R.string.github_default_branch),
                    summary = details.defaultBranch,
                    enabled = !busy && !details.isArchived,
                    onClick = { branchConfirmation = ""; defaultBranchDraft = details.defaultBranch }
                )
                state.settingsFailure?.takeIf { descriptionDraft == null && defaultBranchDraft == null }?.let { failure ->
                    Text(
                        text = repositoryActionFailureText(failure),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
    pending?.let { request ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) pending = null
            },
            title = { Text(stringResource(request.title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(request.message, fullName))
                    if (request.requiresName) {
                        OutlinedTextField(
                            value = confirmation, onValueChange = { confirmation = it },
                            enabled = !busy, singleLine = true,
                            label = { Text(stringResource(R.string.github_confirm_repository_name)) },
                            supportingText = { Text(fullName) },
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    state.settingsFailure?.let { failure ->
                        Text(repositoryActionFailureText(failure), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && (!request.requiresName || confirmation == fullName),
                    onClick = {
                        submittingPending = true
                        onAction(when (val action = request.action) {
                            is RepositoryDetailAction.SetVisibility -> action.copy(confirmation = confirmation)
                            is RepositoryDetailAction.SetArchived -> action.copy(confirmation = confirmation)
                            else -> action
                        })
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pending = null }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    descriptionDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) {
                    submittingDescription = false
                    descriptionDraft = null
                }
            },
            title = { Text(stringResource(R.string.github_edit_description)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { descriptionDraft = it },
                        enabled = !busy,
                        singleLine = false,
                        minLines = 2,
                        label = { Text(stringResource(R.string.github_description_hint)) }
                    )
                    state.settingsFailure?.let { failure ->
                        Text(
                            text = repositoryActionFailureText(failure),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        submittingDescription = true
                        onAction(RepositoryDetailAction.UpdateDescription(draft))
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        submittingDescription = false
                        descriptionDraft = null
                    }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    defaultBranchDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingSettings) {
                    submittingDefaultBranch = false
                    defaultBranchDraft = null
                }
            },
            title = { Text(stringResource(R.string.github_edit_default_branch)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { defaultBranchDraft = it },
                        enabled = !busy,
                        singleLine = true,
                        label = { Text(stringResource(R.string.github_default_branch_hint)) }
                    )
                    Text(stringResource(R.string.github_default_branch_warning), modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = branchConfirmation, onValueChange = { branchConfirmation = it },
                        enabled = !busy, singleLine = true,
                        label = { Text(stringResource(R.string.github_confirm_repository_name)) },
                        supportingText = { Text(fullName) }
                    )
                    state.settingsFailure?.let { failure ->
                        Text(
                            text = repositoryActionFailureText(failure),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && draft.isNotBlank() && draft.trim() != details.defaultBranch && branchConfirmation == fullName,
                    onClick = {
                        submittingDefaultBranch = true
                        onAction(RepositoryDetailAction.SetDefaultBranch(draft, branchConfirmation))
                    }
                ) {
                    Text(stringResource(R.string.github_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        submittingDefaultBranch = false
                        defaultBranchDraft = null
                    }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

@Composable
private fun AdministrationRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

@Composable
private fun AdministrationToggle(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AdministrationRow(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = {
            // The switch only ever reflects what the repository confirmed, so a refused
            // or still pending write cannot leave it showing a state that never happened.
            Switch(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        },
        onClick = onClick
    )
}

@Composable
private fun AdministrationEditRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    trailingIcon: ImageVector = Icons.Default.Edit,
    onClick: () -> Unit
) {
    AdministrationRow(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}
