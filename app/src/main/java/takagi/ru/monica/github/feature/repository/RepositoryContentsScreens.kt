package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubContentItem
import takagi.ru.monica.github.domain.GithubContentType
import takagi.ru.monica.github.domain.GithubBranch
import takagi.ru.monica.github.domain.GithubFileContent
import takagi.ru.monica.github.domain.GithubTag
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun RepositoryFilesScreen(
    state: RepositoryFilesUiState,
    onAction: (RepositoryFilesAction) -> Unit,
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (GithubContentItem) -> Unit,
    onSelectRef: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = state.name,
        subtitle = state.ref,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.tree(state.fullName, state.ref, state.path))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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
                            }
                        )
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
    var sheetVisible by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filteredRefs = remember(showTags, branches, tags, query) {
        val refs = if (showTags) {
            tags.map { RepositoryRefRow(it.name, isProtected = false) }
        } else {
            branches.map { RepositoryRefRow(it.name, isProtected = it.isProtected) }
        }
        refs.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = { sheetVisible = true },
            enabled = branches.isNotEmpty() || tags.isNotEmpty(),
            shape = GithubExpressiveShapes.control
        ) {
            Text(selectedRef, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.github_select_branch))
        }
        if (isLoadingBranches || isLoadingTags) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        if (branchesError || tagsError) {
            Text(
                text = stringResource(R.string.github_ref_load_error),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            TextButton(onClick = { onAction(RepositoryFilesAction.RetryBranches) }) {
                Text(stringResource(R.string.github_retry))
            }
        }
    }
    if (sheetVisible) {
        GithubModalBottomSheet(onDismissRequest = { sheetVisible = false }) {
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
                    onClick = { showTags = false },
                    label = { Text(stringResource(R.string.github_branches)) }
                )
                FilterChip(
                    selected = showTags,
                    onClick = {
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
            when {
                (showTags && tagsError) || (!showTags && branchesError) -> GithubMessageState(
                    title = stringResource(R.string.github_ref_load_error),
                    color = MaterialTheme.colorScheme.error,
                    actionLabel = stringResource(R.string.github_retry),
                    onAction = {
                        onAction(if (showTags) RepositoryFilesAction.LoadTags else RepositoryFilesAction.RetryBranches)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                filteredRefs.isEmpty() && !(showTags && isLoadingTags) -> GithubMessageState(
                    title = stringResource(R.string.github_no_refs),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = filteredRefs,
                        key = { it.name }
                    ) { ref ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheetVisible = false
                                    onSelect(ref.name)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(ref.name, style = MaterialTheme.typography.bodyLarge)
                            if (ref.isProtected) {
                                Text(
                                    stringResource(R.string.github_protected_branch),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    if (showTags && tagsLoaded && tagsHasNext && !isLoadingTags) {
                        item(key = "load-more-tags") {
                            TextButton(onClick = { onAction(RepositoryFilesAction.LoadMoreTags) }) {
                                Text(stringResource(R.string.github_load_more))
                            }
                        }
                    } else if (!showTags && branchesHasNext && !isLoadingBranches) {
                        item(key = "load-more-branches") {
                            TextButton(onClick = { onAction(RepositoryFilesAction.LoadMoreBranches) }) {
                                Text(stringResource(R.string.github_load_more))
                            }
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
    val content = state.content
    GithubDetailScaffold(
        title = state.fileName,
        subtitle = state.ref,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
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
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                content is GithubFileContent.TooLarge -> GithubMessageState(
                    title = stringResource(R.string.github_file_too_large),
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
        TextButton(onClick = { onOpenPath("") }) {
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
            TextButton(onClick = { onOpenPath(destination) }) {
                Text(segment, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RepositoryContentRow(item: GithubContentItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
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
                maxLines = 1,
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
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()
        val lineNumberColor = MaterialTheme.colorScheme.outline
        val code = remember(text, lineNumberColor) { codeWithLineNumbers(text, lineNumberColor) }
        Box(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(16.dp)
        ) {
            SelectionContainer {
                Surface(
                    shape = GithubExpressiveShapes.container,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = code,
                        modifier = Modifier.padding(18.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun codeWithLineNumbers(text: String, lineNumberColor: Color) = buildAnnotatedString {
    val lines = text.lines()
    val width = lines.size.toString().length.coerceAtLeast(2)
    lines.forEachIndexed { index, line ->
        withStyle(SpanStyle(color = lineNumberColor)) {
            append((index + 1).toString().padStart(width, ' '))
        }
        append("  ")
        append(line)
        if (index != lines.lastIndex) append('\n')
    }
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
