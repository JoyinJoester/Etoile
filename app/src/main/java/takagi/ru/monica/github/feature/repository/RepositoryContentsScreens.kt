package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCenteredProgress
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText
import kotlinx.coroutines.launch

@Composable
fun RepositoryFilesScreen(
    state: RepositoryFilesUiState,
    onAction: (RepositoryFilesAction) -> Unit,
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (GithubContentItem) -> Unit,
    onSelectRef: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canMutate = state.isOnBranch && state.canWrite
    var mutation by rememberSaveable { mutableStateOf<String?>(null) }
    var mutationPath by rememberSaveable { mutableStateOf("") }
    var mutationSha by rememberSaveable { mutableStateOf("") }
    var mutationMessage by rememberSaveable { mutableStateOf("") }
    var mutationBody by rememberSaveable { mutableStateOf("") }
    val pendingMutation = state.pendingMutation
    val outcome = state.mutationOutcome
    LaunchedEffect(outcome) {
        if (outcome is RepositoryFileMutationOutcome.Succeeded) {
            mutation = null
            mutationPath = ""
            mutationSha = ""
            mutationMessage = ""
            mutationBody = ""
            onAction(RepositoryFilesAction.DismissMutation)
        }
    }
    mutation?.let { kind ->
        val deleting = kind == "delete"
        val busy = pendingMutation != null
        val failure = (outcome as? RepositoryFileMutationOutcome.Failed)
            ?.takeIf { (it.mutation is RepositoryFileMutation.Delete) == deleting }
            ?.failure
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    mutation = null
                    onAction(RepositoryFilesAction.DismissMutation)
                }
            },
            title = { Text(stringResource(if (deleting) R.string.github_delete_file else R.string.github_new_file)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(mutationPath, { mutationPath = it }, enabled = !busy && !deleting,
                    label = { Text(stringResource(R.string.github_file_path)) }, singleLine = true)
                if (!deleting) OutlinedTextField(mutationBody, { if (it.length <= 512 * 1024) mutationBody = it }, enabled = !busy,
                    label = { Text(stringResource(R.string.github_file_content)) }, minLines = 5, maxLines = 10)
                OutlinedTextField(mutationMessage, { mutationMessage = it }, enabled = !busy,
                    label = { Text(stringResource(R.string.github_commit_message)) }, singleLine = true)
                if (deleting) Text(stringResource(R.string.github_delete_file_confirm), style = MaterialTheme.typography.bodySmall)
                failure?.let { RepositoryFileFailureMessage(it) }
            } },
            confirmButton = { TextButton(
                enabled = !busy && mutationPath.isNotBlank() && mutationMessage.isNotBlank() &&
                    (!deleting || mutationSha.isNotBlank()),
                onClick = {
                    if (deleting) {
                        onAction(RepositoryFilesAction.DeleteFile(mutationPath, mutationSha, mutationMessage))
                    } else {
                        onAction(RepositoryFilesAction.CreateFile(mutationPath, mutationMessage, mutationBody))
                    }
                }
            ) { Text(stringResource(if (deleting) R.string.github_delete_file else R.string.github_save_file)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = {
                mutation = null
                onAction(RepositoryFilesAction.DismissMutation)
            }) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    GithubDetailScaffold(
        title = state.name,
        subtitle = state.ref,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (canMutate) {
                IconButton(onClick = {
                    onAction(RepositoryFilesAction.DismissMutation)
                    mutation = "new"
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.github_new_file))
                }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.tree(state.fullName, state.ref, state.path))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(RepositoryFilesAction.Refresh) },
            enabled = !state.isLoading && !state.isLoadingBranches && !state.isLoadingTags,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            RepositoryBreadcrumb(
                path = state.path,
                onOpenPath = onOpenPath,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            RepositoryRefSelector(
                selectedRef = state.ref,
                branches = state.branches.items,
                tags = state.tags.items,
                branchesHasNext = state.branches.hasNextPage,
                tagsHasNext = state.tags.hasNextPage,
                isLoadingBranches = state.isLoadingBranches,
                isLoadingTags = state.isLoadingTags,
                branchesError = state.branchesError,
                tagsError = state.tagsError,
                tagsLoaded = state.tagsLoaded,
                onSelect = onSelectRef,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            RepositoryWriteAccessHint(
                viewerLogin = state.viewerLogin,
                viewerRole = state.viewerRole,
                onSignIn = onSignIn,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.items.isNotEmpty(),
                row = GithubSkeletonRow.COMPACT,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when {
                state.error -> GithubMessageState(
                    title = stringResource(R.string.github_directory_load_error),
                    color = MaterialTheme.colorScheme.error,
                    actionLabel = stringResource(R.string.github_retry),
                    onAction = { onAction(RepositoryFilesAction.Retry) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                !state.isLoading && state.items.isEmpty() -> GithubMessageState(
                    title = stringResource(R.string.github_empty_directory),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(state.items, key = { "${it.type}:${it.sha}:${it.path}" }) { item ->
                        RepositoryContentRow(
                            item = item,
                            onClick = {
                                if (item.type == GithubContentType.DIRECTORY) onOpenPath(item.path)
                                else onOpenFile(item)
                            },
                            onDelete = if (item.type == GithubContentType.FILE && canMutate) {
                                {
                                    onAction(RepositoryFilesAction.DismissMutation)
                                    mutationPath = item.path
                                    mutationSha = item.sha
                                    mutationMessage = "Delete ${item.name}"
                                    mutation = "delete"
                                }
                            } else null
                        )
                    }
                }
            }
          }
        }
    }
}

private data class RepositoryRefRow(val name: String, val isProtected: Boolean)

@Composable
private fun RepositoryRefSelector(
    selectedRef: String,
    branches: List<GithubBranch>,
    tags: List<GithubTag>,
    branchesHasNext: Boolean,
    tagsHasNext: Boolean,
    isLoadingBranches: Boolean,
    isLoadingTags: Boolean,
    branchesError: Boolean,
    tagsError: Boolean,
    tagsLoaded: Boolean,
    onSelect: (String) -> Unit,
    onAction: (RepositoryFilesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredRefs = remember(showTags, branches, tags, query) {
        val refs = if (showTags) {
            tags.map { RepositoryRefRow(it.name, isProtected = false) }
        } else {
            branches.map { RepositoryRefRow(it.name, isProtected = it.isProtected) }
        }
        refs.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Column(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = { sheetVisible = true },
            enabled = true,
            modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Text(selectedRef, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.github_select_branch))
        }
        if (isLoadingBranches || isLoadingTags) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
    }
        if (branchesError || tagsError) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.github_ref_load_error),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                onAction(if (tagsError && !branchesError) RepositoryFilesAction.LoadTags else RepositoryFilesAction.RetryBranches)
            }) {
                Text(stringResource(R.string.github_retry))
            }
          }
        }
    }
    if (sheetVisible) {
        GithubModalBottomSheet(onDismissRequest = {
            sheetVisible = false
            query = ""
        }) {
            GithubSheetHeader(
                title = stringResource(R.string.github_select_ref),
                subtitle = stringResource(if (showTags) R.string.github_tags else R.string.github_branches),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showTags,
                    onClick = {
                        if (showTags) query = ""
                        showTags = false
                    },
                    label = { Text(stringResource(R.string.github_branches)) }
                )
                FilterChip(
                    selected = showTags,
                    onClick = {
                        if (!showTags) query = ""
                        showTags = true
                        onAction(RepositoryFilesAction.LoadTags)
                    },
                    label = { Text(stringResource(R.string.github_tags)) }
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.github_search_refs)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
            val loading = if (showTags) isLoadingTags else isLoadingBranches
            val hasError = if (showTags) tagsError else branchesError
            val hasNext = if (showTags) tagsLoaded && tagsHasNext else branchesHasNext
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                items(filteredRefs, key = { it.name }) { ref ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = ref.name == selectedRef,
                                role = Role.RadioButton,
                                onClick = {
                                    sheetVisible = false
                                    query = ""
                                    onSelect(ref.name)
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                        Text(
                            ref.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ref.isProtected) {
                            Text(
                                stringResource(R.string.github_protected_branch),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        }
                        RadioButton(selected = ref.name == selectedRef, onClick = null)
                    }
                    HorizontalDivider()
                }
                if (loading) {
                    item(key = "refs-loading") { GithubCenteredProgress() }
                }
                item(key = "refs-status") {
                    GithubPagedListStatus(
                        itemCount = filteredRefs.size,
                        isInitialLoading = loading,
                        isLoadingMore = false,
                        hasError = hasError,
                        canLoadMore = hasNext && !loading,
                        errorMessage = stringResource(R.string.github_ref_load_error),
                        emptyMessage = stringResource(R.string.github_no_refs),
                        onRetry = {
                            onAction(if (showTags) RepositoryFilesAction.LoadTags else RepositoryFilesAction.RetryBranches)
                        },
                        onLoadMore = {
                            onAction(if (showTags) RepositoryFilesAction.LoadMoreTags else RepositoryFilesAction.LoadMoreBranches)
                        },
                        compact = true
                    )
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = "" }) {
                            Text(stringResource(R.string.github_clear_search))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun RepositoryFileScreen(
    state: RepositoryFileUiState,
    onAction: (RepositoryFileAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedCommit = state.writtenCommit
    val savedMessage = if (savedCommit == null) {
        null
    } else {
        stringResource(R.string.github_save_file_success, savedCommit.take(7))
    }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            snackbarHostState.showSnackbar(savedMessage)
            onAction(RepositoryFileAction.DismissSaved)
        }
    }
    if (state.editing && state.draftText != null) {
        AlertDialog(
            onDismissRequest = { if (!state.writing) onAction(RepositoryFileAction.CancelEdit) },
            title = { Text(stringResource(R.string.github_edit_file)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.draftText,
                    onValueChange = { onAction(RepositoryFileAction.DraftChanged(it)) },
                    enabled = !state.writing,
                    minLines = 8,
                    maxLines = 18,
                    modifier = Modifier.fillMaxWidth()
                )
                state.writeFailure?.let { RepositoryFileFailureMessage(it) }
            } },
            confirmButton = { TextButton(enabled = !state.writing, onClick = { onAction(RepositoryFileAction.Save) }) { Text(stringResource(R.string.github_save_file)) } },
            dismissButton = { TextButton(enabled = !state.writing, onClick = { onAction(RepositoryFileAction.CancelEdit) }) { Text(stringResource(R.string.discussion_cancel)) } }
        )
    }
    val content = state.content
    GithubDetailScaffold(
        title = state.fileName,
        subtitle = state.ref,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (content is GithubFileContent.Text && !state.fileName.isMarkdownFile() && state.canWrite) {
                TextButton(onClick = { onAction(RepositoryFileAction.StartEdit) }, enabled = !state.writing) {
                    Text(stringResource(R.string.github_edit_file))
                }
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.blob(state.fullName, state.ref, state.path))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error -> GithubMessageState(
                    title = stringResource(R.string.github_file_load_error),
                    color = MaterialTheme.colorScheme.error,
                    actionLabel = stringResource(R.string.github_retry),
                    onAction = { onAction(RepositoryFileAction.Retry) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                content is GithubFileContent.Binary -> GithubMessageState(
                    title = stringResource(R.string.github_binary_file),
                    description = state.path,
                    actionLabel = stringResource(R.string.github_open_on_github),
                    onAction = { onOpenExternal(GithubWebUrls.blob(state.fullName, state.ref, state.path)) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                content is GithubFileContent.TooLarge -> GithubMessageState(
                    title = stringResource(R.string.github_file_too_large),
                    description = state.path,
                    actionLabel = stringResource(R.string.github_open_on_github),
                    onAction = { onOpenExternal(GithubWebUrls.blob(state.fullName, state.ref, state.path)) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                content is GithubFileContent.Text -> RepositoryTextFile(
                    state = state,
                    text = content.value,
                    onOpenExternal = onOpenExternal
                )
            }
        }
    }
}

@Composable
private fun RepositoryBreadcrumb(
    path: String,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(path) { path.split('/').filter(String::isNotBlank) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { onOpenPath("") }) {
            Text(stringResource(R.string.github_root))
        }
        var accumulated = ""
        segments.forEach { segment ->
            accumulated = if (accumulated.isBlank()) segment else "$accumulated/$segment"
            val destination = accumulated
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { onOpenPath(destination) }) {
                Text(segment, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RepositoryContentRow(item: GithubContentItem, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val accessibilityLabel = when (item.type) {
        GithubContentType.DIRECTORY -> stringResource(R.string.github_directory_accessibility, item.path)
        GithubContentType.SYMLINK -> stringResource(R.string.github_symlink_accessibility, item.path)
        GithubContentType.SUBMODULE -> stringResource(R.string.github_submodule_accessibility, item.path)
        else -> stringResource(R.string.github_file_accessibility, item.path, formatBytes(item.size))
    }
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
            contentDescription = accessibilityLabel
            }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = GithubExpressiveShapes.compact,
            color = if (item.type == GithubContentType.DIRECTORY) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Icon(
                imageVector = when (item.type) {
                    GithubContentType.DIRECTORY -> Icons.Default.Folder
                    GithubContentType.SYMLINK, GithubContentType.SUBMODULE -> Icons.Default.Link
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = if (item.type == GithubContentType.DIRECTORY) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(10.dp).size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.type != GithubContentType.DIRECTORY && item.size > 0) {
                Text(
                    text = formatBytes(item.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.github_more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.github_delete_file)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun RepositoryTextFile(
    state: RepositoryFileUiState,
    text: String,
    onOpenExternal: (String) -> Unit
) {
    if (state.fileName.isMarkdownFile()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                MarkdownPreviewText(
                    markdown = text,
                    imageBitmaps = emptyMap(),
                    onOpenExternalLink = { target ->
                        onOpenExternal(
                            GithubWebUrls.resolveMarkdownLink(
                                fullName = state.fullName,
                                ref = state.ref,
                                sourcePath = state.path,
                                target = target
                            )
                        )
                    },
                    renderImages = false,
                    maxElements = 500
                )
            }
        }
    } else {
        val ranges by androidx.compose.runtime.produceState<IntArray?>(null, text) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                takagi.ru.monica.github.reader.NativeCodeIndex.lineRanges(text.toCharArray())
            }
        }
        val index = ranges
        if (index == null) {
            GithubCenteredProgress()
        } else {
            val horizontalScroll = rememberScrollState()
            var query by rememberSaveable { mutableStateOf("") }
            var matchCursor by rememberSaveable { mutableStateOf(0) }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val longestLine = remember(index) {
                (0 until index.size / 2).maxOf { index[it * 2 + 1] - index[it * 2] }
            }
            val codeWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
                MaterialTheme.typography.bodyMedium.fontSize.toDp() * longestLine.coerceAtLeast(1)
            }
            val visibleLines = remember(index, query) {
                (0 until index.size / 2).filter { line ->
                    query.isBlank() || text.substring(index[line * 2], index[line * 2 + 1]).contains(query, ignoreCase = true)
                }
            }
            matchCursor = matchCursor.coerceIn(0, (visibleLines.size - 1).coerceAtLeast(0))
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.github_search_code)) },
                    supportingText = {
                        if (query.isNotBlank()) Text(stringResource(
                            R.string.github_code_match_position,
                            if (visibleLines.isEmpty()) 0 else matchCursor + 1,
                            visibleLines.size
                        ))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.github_clear_search))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (query.isNotBlank() && visibleLines.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            matchCursor = (matchCursor - 1 + visibleLines.size) % visibleLines.size
                            scope.launch { listState.animateScrollToItem(matchCursor) }
                        }) { Text(stringResource(R.string.github_code_previous_match)) }
                        TextButton(onClick = {
                            matchCursor = (matchCursor + 1) % visibleLines.size
                            scope.launch { listState.animateScrollToItem(matchCursor) }
                        }) { Text(stringResource(R.string.github_code_next_match)) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    state = listState
                ) {
                items(visibleLines.size) { position ->
                    val line = visibleLines[position]
                    val lineDescription = stringResource(R.string.github_code_line_number, line + 1)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = lineDescription }
                    ) {
                        Text(
                            text = (line + 1).toString(),
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(64.dp).padding(horizontal = 8.dp)
                        )
                        SelectionContainer(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
                            Text(
                                text = highlightCodeLine(text.substring(index[line * 2], index[line * 2 + 1]).ifEmpty { " " }, query),
                                modifier = Modifier.width(codeWidth),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private fun highlightCodeLine(line: String, query: String = "") = buildAnnotatedString {
    val queryToken = if (query.isBlank()) "" else Regex.escape(query)
    val token = Regex("//.*|\\\"(?:\\\\.|[^\\\"])*\\\"|\\b(fun|val|var|class|object|interface|if|else|when|for|while|return|import|package|public|private|override)\\b${if (queryToken.isEmpty()) "" else "|$queryToken"}", RegexOption.IGNORE_CASE)
    var cursor = 0
    token.findAll(line).forEach { match ->
        append(line.substring(cursor, match.range.first))
        val value = match.value
        val style = when {
            value.startsWith("//") -> SpanStyle(color = Color(0xFF777777), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            value.startsWith("\"") -> SpanStyle(color = Color(0xFFB8D878))
            query.isNotBlank() && value.equals(query, ignoreCase = true) -> SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            else -> SpanStyle(color = Color(0xFFE64A8A), fontWeight = FontWeight.SemiBold)
        }
        withStyle(style) { append(value) }
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}

private fun String.isMarkdownFile(): Boolean {
    val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in setOf("md", "markdown", "mdown", "mkd")
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024f)
    else -> "$bytes B"
}
