package takagi.ru.monica.github.feature.releases

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import takagi.ru.monica.github.component.GithubAdaptiveDetailLayout
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.items
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubMetadataRow
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubPullToRefreshBox
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubUserLink
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubAssetInput
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun ReleasesScreen(
    state: ReleasesUiState,
    onAction: (ReleasesAction) -> Unit,
    onBack: () -> Unit,
    onOpenRelease: (GithubRelease) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var formId by rememberSaveable { mutableStateOf(0L) }
    var tag by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var asDraft by rememberSaveable { mutableStateOf(false) }
    var asPrerelease by rememberSaveable { mutableStateOf(false) }
    var attachTarget by rememberSaveable { mutableStateOf(0L) }
    val busy = state.isMutating
    val attach = state.pendingMutation as? ReleaseMutation.Attach
    val outcome = state.mutationOutcome
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val releaseId = attachTarget
        attachTarget = 0L
        if (uri != null && releaseId > 0L) {
            val fileName = uri.attachmentName(context.contentResolver)
            onAction(
                ReleasesAction.Attach(
                    releaseId = releaseId,
                    fileName = fileName,
                    label = "",
                    contentType = context.contentResolver.getType(uri),
                    contentLength = uri.attachmentSize(context.contentResolver),
                    open = { uri.assetInput(context.contentResolver) }
                )
            )
        }
    }

    fun open(kind: String, release: GithubRelease?) {
        dialog = kind
        formId = release?.id ?: 0L
        tag = release?.tagName.orEmpty()
        title = release?.name.orEmpty()
        notes = release?.body.orEmpty()
        target = release?.targetCommitish.orEmpty()
        asDraft = release?.isDraft ?: false
        asPrerelease = release?.isPrerelease ?: false
        onAction(ReleasesAction.DismissMutation)
    }
    fun close() {
        dialog = null
        onAction(ReleasesAction.DismissMutation)
    }

    LaunchedEffect(outcome) {
        if (outcome is ReleaseMutationOutcome.Succeeded) {
            dialog = null
            onAction(ReleasesAction.DismissMutation)
        }
    }
    val editorKind = when (dialog) {
        "create" -> ReleaseMutationKind.Create
        "edit" -> ReleaseMutationKind.Edit
        else -> null
    }
    if (editorKind != null) {
        val editing = editorKind == ReleaseMutationKind.Edit
        val failure = (outcome as? ReleaseMutationOutcome.Failed)
            ?.takeIf { it.kind == editorKind }
            ?.failure
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = {
                Text(
                    stringResource(
                        if (editing) R.string.github_edit_release else R.string.github_new_release
                    )
                )
            },
            text = { Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(tag, { tag = it }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_tag_field)) })
                OutlinedTextField(title, { title = it }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_title_field)) })
                OutlinedTextField(target, { target = it }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_target_field)) })
                OutlinedTextField(notes, { notes = it }, enabled = !busy, minLines = 4, maxLines = 8,
                    label = { Text(stringResource(R.string.github_release_notes)) })
                ReleaseToggle(stringResource(R.string.github_release_draft), asDraft, { asDraft = it }, !busy)
                ReleaseToggle(stringResource(R.string.github_release_prerelease), asPrerelease, { asPrerelease = it }, !busy)
                failure?.let { ReleaseMutationFailureMessage(it) }
            } },
            confirmButton = { TextButton(
                enabled = !busy && tag.isNotBlank(),
                onClick = {
                    onAction(
                        ReleasesAction.Save(
                            releaseId = formId.takeIf { it > 0L },
                            tagName = tag,
                            title = title,
                            body = notes,
                            targetCommitish = target,
                            isDraft = asDraft,
                            isPrerelease = asPrerelease
                        )
                    )
                }
            ) { Text(stringResource(R.string.github_save_release)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { close() }) {
                Text(stringResource(R.string.discussion_cancel))
            } }
        )
    }
    val attachFailure = (outcome as? ReleaseMutationOutcome.Failed)
        ?.takeIf { it.kind == ReleaseMutationKind.Attach }
    if (attachFailure != null) {
        AlertDialog(
            onDismissRequest = { onAction(ReleasesAction.DismissMutation) },
            title = { Text(stringResource(R.string.github_release_attach_asset)) },
            text = { ReleaseMutationFailureMessage(attachFailure.failure) },
            confirmButton = { TextButton(onClick = { onAction(ReleasesAction.DismissMutation) }) {
                Text(stringResource(R.string.github_dismiss))
            } }
        )
    }
    if (dialog == "delete") {
        val failure = (outcome as? ReleaseMutationOutcome.Failed)
            ?.takeIf { it.kind == ReleaseMutationKind.Delete }
            ?.failure
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(R.string.github_delete_release)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tag, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.github_delete_release_confirm),
                    style = MaterialTheme.typography.bodySmall
                )
                failure?.let { ReleaseMutationFailureMessage(it) }
            } },
            confirmButton = { TextButton(
                enabled = !busy && formId > 0L,
                onClick = { onAction(ReleasesAction.Delete(formId)) }
            ) { Text(stringResource(R.string.github_delete_release)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { close() }) {
                Text(stringResource(R.string.discussion_cancel))
            } }
        )
    }
    GithubDetailScaffold(
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        title = state.name,
        subtitle = stringResource(R.string.github_releases),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { open("create", null) }, enabled = !busy) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.github_new_release))
            }
            IconButton(
                onClick = { onAction(ReleasesAction.Refresh) },
                enabled = !state.isRefreshing && !state.isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
            }
            GithubOpenOnGithubButton {
                onOpenExternal(GithubWebUrls.releases(state.fullName))
            }
        }
    ) { padding ->
        GithubPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(ReleasesAction.Refresh) },
            enabled = !state.isRefreshing && !state.isLoading && !state.isLoadingMore,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            GithubListLoadingState(
                isLoading = state.isLoading,
                hasItems = state.items.isNotEmpty(),
                row = GithubSkeletonRow.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(state.items, key = GithubRelease::id) { release ->
                    ReleaseListRow(
                        release = release,
                        isBusy = busy,
                        onClick = { onOpenRelease(release) },
                        onEdit = { open("edit", release) },
                        onSetDraft = { draft ->
                            onAction(
                                ReleasesAction.Save(
                                    releaseId = release.id,
                                    tagName = release.tagName,
                                    title = release.name.orEmpty(),
                                    body = release.body.orEmpty(),
                                    targetCommitish = release.targetCommitish,
                                    isDraft = draft,
                                    isPrerelease = release.isPrerelease
                                )
                            )
                        },
                        onDelete = { open("delete", release) },
                        onAttach = { attachTarget = release.id; picker.launch(arrayOf("*/*")) },
                        isUploading = attach?.releaseId == release.id
                    )
                }
                githubFullSpanItem(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.items.size,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_release_list_error),
                        emptyMessage = stringResource(R.string.github_no_releases),
                        onRetry = { onAction(ReleasesAction.Retry) },
                        emptyIcon = Icons.Default.LocalOffer,
                        onLoadMore = { onAction(ReleasesAction.LoadMore) },
                        compact = true
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseListRow(
    release: GithubRelease,
    isBusy: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onSetDraft: (Boolean) -> Unit,
    onAttach: () -> Unit,
    isUploading: Boolean,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.github_more_actions)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (release.isDraft) {
                                        R.string.github_publish_release
                                    } else {
                                        R.string.github_unpublish_release
                                    }
                                )
                            )
                        },
                        onClick = { menuExpanded = false; onSetDraft(!release.isDraft) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.github_release_attach_asset)) },
                        onClick = { menuExpanded = false; onAttach() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.github_edit_release)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.github_delete_release)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
            if (isUploading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp).size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(18.dp)
                )
            }
        }
        if (release.isDraft || release.isPrerelease) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (release.isDraft) {
                    ReleaseBadge(stringResource(R.string.github_release_draft), isProminent = true)
                }
                if (release.isPrerelease) {
                    ReleaseBadge(stringResource(R.string.github_release_prerelease))
                }
            }
        }
        release.body?.trim()?.takeIf(String::isNotEmpty)?.let { body ->
            Text(
                text = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().removePrefix("#").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = githubRelativeTime(release.publishedAt ?: release.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.github_release_assets_count,
                    release.assets.size,
                    release.assets.size
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 13.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun ReleaseBadge(text: String, isProminent: Boolean = false) {
    Surface(
        shape = GithubExpressiveShapes.control,
        color = if (isProminent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isProminent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ReleaseToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ReleaseMutationFailureMessage(failure: ReleaseMutationFailure) {
    val message = when (failure) {
        ReleaseMutationFailure.InvalidInput -> R.string.github_release_change_invalid
        ReleaseMutationFailure.Conflict -> R.string.github_release_change_conflict
        ReleaseMutationFailure.Forbidden -> R.string.github_release_change_forbidden
        ReleaseMutationFailure.NotFound -> R.string.github_release_change_not_found
        ReleaseMutationFailure.RateLimited -> R.string.github_write_rate_limited
        ReleaseMutationFailure.Network -> R.string.github_release_change_error
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}

private fun Uri.assetInput(resolver: ContentResolver): GithubAssetInput {
    val stream = resolver.openInputStream(this) ?: error("Unable to open the selected file")
    return object : GithubAssetInput {
        override fun read(target: ByteArray, offset: Int, count: Int) = stream.read(target, offset, count)

        override fun close() {
            stream.close()
        }
    }
}

private fun Uri.attachmentName(resolver: ContentResolver): String =
    resolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.getColumnCount() > 0) cursor.getString(0) else null
    } ?: lastPathSegment.orEmpty()

private fun Uri.attachmentSize(resolver: ContentResolver): Long =
    resolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
    } ?: 0L

@Composable
fun ReleaseDetailScreen(
    state: ReleaseDetailUiState,
    onAction: (ReleaseDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val release = state.release
    var removeTargetId by rememberSaveable { mutableStateOf(0L) }
    val removeTarget = release?.assets?.firstOrNull { it.id == removeTargetId }
    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { removeTargetId = 0L },
            title = { Text(stringResource(R.string.github_release_remove_asset)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(removeTarget.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.github_release_remove_asset_confirm),
                    style = MaterialTheme.typography.bodySmall
                )
            } },
            confirmButton = { TextButton(onClick = {
                removeTargetId = 0L
                onAction(ReleaseDetailAction.RemoveAsset(removeTarget.id))
            }) { Text(stringResource(R.string.github_release_remove_asset)) } },
            dismissButton = { TextButton(onClick = { removeTargetId = 0L }) {
                Text(stringResource(R.string.discussion_cancel))
            } }
        )
    }
    GithubDetailScaffold(
        title = release?.tagName ?: stringResource(R.string.github_release),
        contentMaxWidth = GithubAdaptiveLayout.wideContentMaxWidth,
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        actions = {
            release?.let {
                GithubOpenOnGithubButton(onClick = { onOpenExternal(it.htmlUrl) })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                release == null && state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                release == null && state.error -> {
                    GithubMessageState(
                        title = stringResource(R.string.github_release_load_error),
                        color = MaterialTheme.colorScheme.error,
                        actionLabel = stringResource(R.string.github_retry),
                        onAction = { onAction(ReleaseDetailAction.Retry) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                release != null -> {
                    ReleaseDetailContent(
                        release = release,
                        fullName = state.fullName,
                        onOpenExternal = onOpenExternal,
                        onRemoveAsset = { asset -> removeTargetId = asset.id },
                        removingAssetId = state.removingAssetId,
                        assetRemovalFailed = state.assetRemovalFailed,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
            if (state.isLoading && release != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun ReleaseDetailContent(
    release: GithubRelease,
    fullName: String,
    onOpenExternal: (String) -> Unit,
    onRemoveAsset: (GithubReleaseAsset) -> Unit,
    removingAssetId: Long?,
    assetRemovalFailed: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val assetsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    GithubAdaptiveDetailLayout(
        modifier = modifier.fillMaxSize(),
        sidebar = { paneModifier ->
            LazyColumn(
                state = assetsState,
                modifier = paneModifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item(key = "summary") { ReleaseSummary(release) }
                releaseAssets(
                    release = release,
                    onOpenExternal = onOpenExternal,
                    onRemoveAsset = onRemoveAsset,
                    removingAssetId = removingAssetId,
                    assetRemovalFailed = assetRemovalFailed
                )
            }
        }
    ) { paneModifier, expanded ->
        LazyColumn(
            state = listState,
            modifier = paneModifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (!expanded) item(key = "summary") {
                ReleaseSummary(release)
            }
            item(key = "notes-heading") {
                GithubSectionHeader(
                    title = stringResource(R.string.github_release_notes),
                    action = if (release.assets.isNotEmpty()) stringResource(R.string.github_release_assets) else null,
                    onAction = {
                        scope.launch {
                            if (expanded) assetsState.animateScrollToItem(1)
                            else listState.animateScrollToItem(3)
                        }
                    }
                )
            }
            item(key = "notes") {
                if (release.body.isNullOrBlank()) {
                    GithubMessageState(title = stringResource(R.string.github_release_no_notes))
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = GithubExpressiveShapes.container,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        MarkdownPreviewText(
                            markdown = release.body,
                            imageBitmaps = emptyMap(),
                            onOpenExternalLink = { link ->
                                onOpenExternal(
                                    GithubWebUrls.resolveMarkdownLink(
                                        fullName = fullName,
                                        ref = release.targetCommitish,
                                        sourcePath = "",
                                        target = link
                                    )
                                )
                            },
                            renderImages = false,
                            maxElements = 160,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
            if (!expanded) releaseAssets(
                release = release,
                onOpenExternal = onOpenExternal,
                onRemoveAsset = onRemoveAsset,
                removingAssetId = removingAssetId,
                assetRemovalFailed = assetRemovalFailed
            )
            item(key = "bottom-space") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun LazyListScope.releaseAssets(
    release: GithubRelease,
    onOpenExternal: (String) -> Unit,
    onRemoveAsset: (GithubReleaseAsset) -> Unit,
    removingAssetId: Long?,
    assetRemovalFailed: Boolean
) {
    item(key = "assets-heading") {
        GithubSectionHeader(title = stringResource(R.string.github_release_assets))
    }
    if (assetRemovalFailed) item(key = "asset-removal-error") {
        Text(
            text = stringResource(R.string.github_release_asset_remove_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
    if (release.assets.isEmpty()) {
        item(key = "no-assets") {
            GithubMessageState(title = stringResource(R.string.github_release_no_assets))
        }
    } else {
        items(release.assets, key = GithubReleaseAsset::id) { asset ->
            ReleaseAssetRow(
                asset = asset,
                onOpenExternal = onOpenExternal,
                isRemoving = removingAssetId == asset.id,
                isBusy = removingAssetId != null,
                onRemove = { onRemoveAsset(asset) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseSummary(release: GithubRelease) {
    val nothing = LocalDesignStyle.current == DesignStyle.NOTHING
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = if (nothing) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(if (nothing) 0.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!nothing) {
                Surface(
                    shape = GithubExpressiveShapes.control,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (release.isDraft || release.isPrerelease) {
                FlowRow(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (release.isDraft) {
                        ReleaseBadge(stringResource(R.string.github_release_draft), isProminent = true)
                    }
                    if (release.isPrerelease) {
                        ReleaseBadge(stringResource(R.string.github_release_prerelease))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GithubMetadataRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.github_release_author),
                value = release.author.login,
                valueContent = { GithubUserLink(release.author.login, avatarUrl = release.author.avatarUrl) }
            )
            GithubMetadataRow(
                icon = Icons.Default.Schedule,
                title = stringResource(
                    if (release.publishedAt == null) {
                        R.string.github_release_created
                    } else {
                        R.string.github_release_published
                    }
                ),
                value = githubRelativeTime(release.publishedAt ?: release.createdAt)
            )
            GithubMetadataRow(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(R.string.github_release_target),
                value = release.targetCommitish
            )
            GithubMetadataRow(
                icon = Icons.Default.Inventory2,
                title = stringResource(R.string.github_release_assets),
                value = pluralStringResource(
                    R.plurals.github_release_assets_count,
                    release.assets.size,
                    release.assets.size
                )
            )
        }
    }
}

@Composable
private fun ReleaseAssetRow(
    asset: GithubReleaseAsset,
    onOpenExternal: (String) -> Unit,
    isRemoving: Boolean,
    isBusy: Boolean,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val formattedSize = Formatter.formatShortFileSize(context, asset.sizeBytes)
    Surface(
        onClick = { onOpenExternal(asset.downloadUrl) },
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = GithubExpressiveShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.label?.takeIf(String::isNotBlank) ?: asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    // Architecture and extension often occur at the end of a filename.
                    // Keep the complete value available when choosing a download.
                )
                if (!asset.label.isNullOrBlank()) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.github_release_asset_metadata,
                        formattedSize,
                        pluralStringResource(
                            R.plurals.github_release_downloads_count,
                            asset.downloadCount,
                            asset.downloadCount
                        )
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (isRemoving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.github_release_download_asset, asset.name),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.github_more_actions)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.github_release_remove_asset)) },
                        onClick = { menuExpanded = false; onRemove() }
                    )
                }
            }
        }
    }
}
