package takagi.ru.monica.gitlab.feature

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import takagi.ru.monica.github.component.githubCollectionSurface
import takagi.ru.monica.github.component.githubCollectionPadding
import takagi.ru.monica.github.component.GithubSearchField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.gitlab.navigation.gitLabFileUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun GitLabProjectsScreen(
    state: GitLabProjectsUiState,
    onAction: (GitLabProjectsAction) -> Unit,
    onProject: (Long) -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    var displayedQuery by rememberSaveable { mutableStateOf(state.query) }
    var displayedAccount by rememberSaveable { mutableStateOf(state.account?.key) }
    LaunchedEffect(state.query, state.account?.key) {
        if (displayedQuery != state.query || displayedAccount != state.account?.key) listState.scrollToItem(0)
        displayedQuery = state.query
        displayedAccount = state.account?.key
    }
    GithubDetailScaffold(
        title = stringResource(R.string.gitlab_projects), subtitle = state.account?.instanceUrl, showGithubServiceStatus = false,
        backContentDescription = stringResource(R.string.github_back), onBack = onBack,
        actions = { IconButton(onClick = { onAction(GitLabProjectsAction.Refresh) }, enabled = state.account != null && !state.isLoading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.gitlab_refresh))
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.account == null) item { GitLabAccountRequired(onAccounts) }
            else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.gitlab_projects_hint), style = MaterialTheme.typography.bodySmall)
                        GithubSearchField(state.searchInput, { onAction(GitLabProjectsAction.SearchChanged(it)) },
                            label = stringResource(R.string.gitlab_project_search),
                            keyboardActions = KeyboardActions(onSearch = { onAction(GitLabProjectsAction.Search) }),
                            modifier = Modifier.fillMaxWidth())
                        FilledTonalButton(onClick = { onAction(GitLabProjectsAction.Search) }) { Text(stringResource(R.string.gitlab_project_search)) }
                    }
                }
                if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(state.items, key = { it.id }) { project ->
                    Column(Modifier.fillMaxWidth().githubCollectionSurface()
                        .clickable(role = Role.Button, onClick = { onProject(project.id) }).githubCollectionPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(project.pathWithNamespace, style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                        if (project.description.isNotBlank()) Text(project.description, maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 32.dp))
                        Column(Modifier.padding(start = 32.dp)) { GitLabProjectMetadata(project) }
                    }
                }
                item {
                    Column {
                        GithubPagedListStatus(state.items.size, state.isLoading, state.isLoadingMore,
                            state.error != null, state.nextPage != null, gitLabReadErrorText(state.error),
                            stringResource(R.string.gitlab_no_projects),
                            { onAction(GitLabProjectsAction.Retry) }, { onAction(GitLabProjectsAction.LoadMore) })
                        GitLabReadAccessAction(state.error, onAccounts)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitLabProjectScreen(
    state: GitLabProjectUiState,
    onAction: (GitLabProjectAction) -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit,
    onSection: (GitLabWorkSection) -> Unit = {}
) {
    val canGoUp = state.filePath != null || state.path.isNotEmpty()
    var showRevision by rememberSaveable(state.account?.key) { mutableStateOf(false) }
    val back = { if (canGoUp) onAction(GitLabProjectAction.Up) else onBack() }
    BackHandler(enabled = canGoUp, onBack = back)
    val lines = remember(state.file) { state.file?.text?.lineSequence()?.take(5001)?.toList().orEmpty() }
    val truncated = lines.size > 5000 || lines.any { it.length > 20_000 }
    val markdown = state.filePath?.substringAfterLast('.')?.lowercase() in setOf("md", "markdown", "mdown", "mkd")
    val fileUrls = remember(state.account?.instanceUrl, state.project?.pathWithNamespace, state.revision?.sha, state.filePath) {
        gitLabFileUrls(state.account?.instanceUrl.orEmpty(), state.project?.pathWithNamespace.orEmpty(),
            state.revision?.sha.orEmpty(), state.filePath.orEmpty())
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.path, state.filePath, state.revision?.sha, state.account?.key) { listState.scrollToItem(0) }
    GithubDetailScaffold(
        title = state.project?.pathWithNamespace ?: stringResource(R.string.gitlab_project), showGithubServiceStatus = false,
        subtitle = state.account?.instanceUrl,
        backContentDescription = stringResource(R.string.github_back), onBack = back,
        actions = { IconButton(onClick = { onAction(GitLabProjectAction.Refresh) }, enabled = state.account != null && !state.isLoading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.gitlab_refresh))
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.account == null) item { GitLabAccountRequired(onAccounts) }
            else {
                state.project?.let { project ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!canGoUp) {
                                if (project.description.isNotBlank()) SelectionContainer {
                                    Text(project.description)
                                }
                                GitLabProjectMetadata(project)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GitLabWorkSection.entries.forEach { section ->
                                        FilledTonalButton(onClick = { onSection(section) }) { Text(gitLabSectionTitle(section)) }
                                    }
                                }
                            }
                            TextButton(onClick = { showRevision = true }, enabled = !state.isLoading) {
                                Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(state.ref.ifBlank { stringResource(R.string.gitlab_ref) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (canGoUp) TextButton(onClick = { onAction(GitLabProjectAction.Up) }) { Text(stringResource(R.string.gitlab_parent_directory)) }
                            SelectionContainer { Text(state.filePath ?: state.path.ifEmpty { "/" }, style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                }
                if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.filePath != null) {
                    if (state.error != null) item {
                        Column {
                            GithubMessageState(title = gitLabReadErrorText(state.error), actionLabel = stringResource(R.string.github_retry),
                                onAction = { onAction(GitLabProjectAction.Retry) })
                            GitLabReadAccessAction(state.error, onAccounts)
                        }
                    }
                    if (state.file != null) {
                        if (!markdown) item { Text(stringResource(R.string.gitlab_file_preview), style = MaterialTheme.typography.bodySmall) }
                        if (markdown) item {
                            MarkdownPreviewText(state.file.text, documentBaseUrl = fileUrls?.document, imageBaseUrl = fileUrls?.image)
                        } else {
                            if (truncated) item { Text(stringResource(R.string.gitlab_preview_truncated), color = MaterialTheme.colorScheme.primary) }
                            itemsIndexed(lines.take(5000), key = { index, _ -> "${state.revision?.sha}:${state.filePath}:$index" }) { index, line ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text("${index + 1}", Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    SelectionContainer(Modifier.weight(1f)) {
                                        Text(line.take(20_000).ifEmpty { " " }, Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, softWrap = true)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(state.entries, key = { it.path }) { entry ->
                        Surface(onClick = { onAction(GitLabProjectAction.Open(entry)) },
                            enabled = !state.isLoading && !state.isLoadingMore && entry.type != GitLabEntryType.SUBMODULE,
                            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (entry.type == GitLabEntryType.DIRECTORY) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null)
                                Column {
                                    Text(entry.name)
                                    if (entry.type == GitLabEntryType.SUBMODULE) Text(stringResource(R.string.gitlab_submodule), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            GithubPagedListStatus(state.entries.size, state.isLoading, state.isLoadingMore,
                                state.error != null, state.nextPage != null, gitLabReadErrorText(state.error),
                                stringResource(if (state.ref.isEmpty()) R.string.gitlab_empty_repository else R.string.gitlab_empty_directory),
                                { onAction(GitLabProjectAction.Retry) }, { onAction(GitLabProjectAction.LoadMore) })
                            GitLabReadAccessAction(state.error, onAccounts)
                        }
                    }
                }
            }
        }
    }
    if (showRevision && state.account != null && state.project != null) {
        val dismiss = {
            showRevision = false
            onAction(GitLabProjectAction.RefChanged(state.ref))
        }
        val apply = {
            onAction(GitLabProjectAction.ApplyRef)
            showRevision = false
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.gitlab_ref)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.refInput, { onAction(GitLabProjectAction.RefChanged(it)) },
                        label = { Text(stringResource(R.string.gitlab_ref)) }, singleLine = true, enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (state.refInput.isNotBlank() && !state.isLoading) apply() }),
                        modifier = Modifier.fillMaxWidth())
                    state.revision?.let { revision ->
                        SelectionContainer { Text(stringResource(R.string.gitlab_revision, revision.name, revision.sha),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = apply, enabled = state.refInput.isNotBlank() && !state.isLoading) {
                    Text(stringResource(R.string.gitlab_apply_ref))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.github_cancel)) } }
        )
    }
}

@Composable
private fun GitLabProjectMetadata(project: GitLabProject) {
    Column {
        Text(stringResource(R.string.gitlab_project_counts, project.stars, project.forks), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(when (project.visibility) {
            "public" -> R.string.gitlab_visibility_public
            "internal" -> R.string.gitlab_visibility_internal
            else -> R.string.gitlab_visibility_private
        }), style = MaterialTheme.typography.labelMedium)
        if (project.archived) Text(stringResource(R.string.gitlab_archived), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GitLabReadAccessAction(error: GitLabReadError?, onAccounts: () -> Unit) {
    if (error == GitLabReadError.AUTHENTICATION || error == GitLabReadError.PERMISSION) {
        TextButton(onClick = onAccounts) { Text(stringResource(R.string.gitlab_accounts)) }
    }
}

@Composable
private fun GitLabAccountRequired(onAccounts: () -> Unit) {
    GithubMessageState(title = stringResource(R.string.gitlab_account_required), actionLabel = stringResource(R.string.gitlab_accounts), onAction = onAccounts)
}

@Composable
private fun gitLabReadErrorText(error: GitLabReadError?): String = stringResource(when (error) {
    GitLabReadError.AUTHENTICATION -> R.string.gitlab_read_authentication
    GitLabReadError.PERMISSION -> R.string.gitlab_read_permission
    GitLabReadError.NOT_FOUND -> R.string.gitlab_read_not_found
    GitLabReadError.RATE_LIMIT -> R.string.gitlab_read_rate_limit
    GitLabReadError.UNSUPPORTED_FILE -> R.string.gitlab_read_unsupported
    GitLabReadError.INVALID_RESPONSE -> R.string.gitlab_read_invalid
    else -> R.string.gitlab_read_network
})
