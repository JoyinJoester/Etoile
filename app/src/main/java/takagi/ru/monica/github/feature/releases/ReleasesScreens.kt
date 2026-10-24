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
import takagi.ru.monica.github.component.githubCollectionSurface
import takagi.ru.monica.github.component.githubCollectionPadding
import takagi.ru.monica.github.component.GithubCollectionDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
import takagi.ru.monica.github.component.githubConfirmedAction
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubAssetInput
import takagi.ru.monica.github.domain.GithubActionKind
import takagi.ru.monica.github.domain.GithubActionOperation
import takagi.ru.monica.github.domain.GithubRelease
import takagi.ru.monica.github.domain.GithubReleaseAsset
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText
import takagi.ru.monica.ui.components.MarkdownImageContext

@Composable
fun ReleasesScreen(
    state: ReleasesUiState,
    onAction: (ReleasesAction) -> Unit,
    onBack: () -> Unit,
    onOpenRelease: (GithubRelease) -> Unit,
    onOpenExternal: (String) -> Unit,
    enableAssetAttachments: Boolean = true,
    modifier: Modifier = Modifier
) {
    var dialog by remember(state.fullName, state.viewerAccountId) { mutableStateOf<String?>(null) }
    var formId by remember(state.fullName, state.viewerAccountId) { mutableStateOf(0L) }
    var tag by remember(state.fullName, state.viewerAccountId) { mutableStateOf("") }
    // File picker consent and streams are intentionally not restored after reconstruction.
    var attachTarget by remember(state.fullName, state.viewerAccountId) { mutableStateOf(0L) }
    var readingAttachment by remember(state.fullName, state.viewerAccountId) { mutableStateOf(false) }
    var attachmentReadFailed by remember(state.fullName, state.viewerAccountId) { mutableStateOf(false) }
    var repeatDeleteRequested by remember(state.fullName, state.viewerAccountId, formId, state.canManage, state.journalReady) { mutableStateOf(false) }
    val busy = state.isMutating || readingAttachment
    val writable = state.canManage && state.journalReady && !busy
    val attach = state.pendingMutation as? ReleaseMutation.Attach
    val outcome = state.mutationOutcome
    val uncertain = state.unconfirmedOperations
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    var pendingAttach by remember(state.fullName, state.viewerAccountId) { mutableStateOf<ReleasesAction.Attach?>(null) }
    val editorForRepeat = state.editor
    val repeatSave = githubConfirmedAction(
        title = stringResource(R.string.github_actions_repeat),
        message = stringResource(R.string.github_release_repeat_warning),
        subject = state.fullName,
        enabled = editorForRepeat != null && writable && editorForRepeat.accountId == state.viewerAccountId,
        confirmationKey = listOf(state.viewerAccountId, editorForRepeat?.releaseId, editorForRepeat?.tagName,
            editorForRepeat?.title, editorForRepeat?.body, editorForRepeat?.targetCommitish,
            editorForRepeat?.isDraft, editorForRepeat?.isPrerelease),
        onConfirm = {
            editorForRepeat?.let { editor -> onAction(ReleasesAction.Save(editor.releaseId, editor.tagName, editor.title,
                editor.body, editor.targetCommitish, editor.isDraft, editor.isPrerelease, editor.accountId, true)) }
        }
    )
    val picker = if (enableAssetAttachments) {
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val releaseId = attachTarget
            attachTarget = 0L
            val accountId = state.viewerAccountId
            val fullName = state.fullName
            if (writable && uri != null && releaseId > 0L) {
                readingAttachment = true
                attachmentReadFailed = false
                scope.launch {
                    try {
                        val attachAction = withContext(Dispatchers.IO) {
                            ReleasesAction.Attach(
                                releaseId = releaseId,
                                fileName = uri.attachmentName(context.contentResolver),
                                label = "",
                                contentType = context.contentResolver.getType(uri),
                                contentLength = uri.attachmentSize(context.contentResolver),
                                open = { uri.assetInput(context.contentResolver) },
                                expectedAccountId = accountId
                            )
                        }
                        val latest = currentState
                        if (latest.fullName == fullName && latest.viewerAccountId == accountId &&
                            latest.canManage && latest.journalReady && !latest.isMutating) {
                            if (GithubActionOperation(GithubActionKind.RELEASE_UPLOAD, releaseId) in latest.unconfirmedOperations) {
                                pendingAttach = attachAction
                            } else currentAction(attachAction)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { attachmentReadFailed = true }
                    finally { readingAttachment = false }
                }
            }
        }
    } else null

    LaunchedEffect(state.canManage, state.journalReady) {
        if (!state.canManage || !state.journalReady) { pendingAttach = null; dialog = null }
    }
    pendingAttach?.takeIf { writable }?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAttach = null },
            title = { Text(stringResource(R.string.github_actions_repeat)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${state.fullName} · ${action.fileName}")
                Text(stringResource(R.string.github_release_repeat_warning))
            } },
            confirmButton = { TextButton(onClick = {
                pendingAttach = null
                if (writable && action.expectedAccountId == state.viewerAccountId) onAction(action.copy(repeatConfirmed = true))
            }) { Text(stringResource(R.string.github_actions_repeat)) } },
            dismissButton = { TextButton(onClick = { pendingAttach = null }) { Text(stringResource(R.string.github_cancel)) } }
        )
    }

    fun open(kind: String, release: GithubRelease?) {
        if (kind == "create" || kind == "edit") {
            onAction(ReleasesAction.OpenEditor(release?.id))
            return
        }
        dialog = kind
        repeatDeleteRequested = false
        formId = release?.id ?: 0L
        tag = release?.tagName.orEmpty()
        onAction(ReleasesAction.DismissMutation)
    }
    fun close() {
        dialog = null
        repeatDeleteRequested = false
        formId = 0L
        pendingAttach = null
        onAction(ReleasesAction.CloseEditor)
        onAction(ReleasesAction.DismissMutation)
    }

    LaunchedEffect(outcome) {
        if (outcome is ReleaseMutationOutcome.Succeeded) {
            dialog = null
            onAction(ReleasesAction.DismissMutation)
        }
    }
    val editor = state.editor
    if (editor != null) {
        val editing = editor.releaseId != null
        val editorKind = if (editing) ReleaseMutationKind.Edit else ReleaseMutationKind.Create
        val failure = (outcome as? ReleaseMutationOutcome.Failed)
            ?.takeIf { it.kind == editorKind }
            ?.failure
        androidx.activity.compose.BackHandler { if (!busy) close() }
        GithubDetailScaffold(
            title = stringResource(if (editing) R.string.github_edit_release else R.string.github_new_release),
            subtitle = state.fullName,
            backContentDescription = stringResource(R.string.github_back),
            onBack = { if (!busy) close() },
            modifier = modifier,
            actions = {
                TextButton(enabled = writable && editor.tagName.isNotBlank(), onClick = {
                    val operation = if (editor.releaseId == null) GithubActionOperation(GithubActionKind.RELEASE_CREATE, 1L)
                    else GithubActionOperation(GithubActionKind.RELEASE_CHANGE, editor.releaseId)
                    val save = ReleasesAction.Save(editor.releaseId, editor.tagName, editor.title, editor.body,
                        editor.targetCommitish, editor.isDraft, editor.isPrerelease, editor.accountId)
                    if (operation in uncertain) repeatSave() else onAction(save)
                }) { Text(stringResource(R.string.github_save_release)) }
            }
        ) { padding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                if (state.journalUnavailable) JournalUnavailableNotice()
                val editorOperation = if (editor.releaseId == null) GithubActionOperation(GithubActionKind.RELEASE_CREATE, 1L)
                    else GithubActionOperation(GithubActionKind.RELEASE_CHANGE, editor.releaseId)
                if (editorOperation in uncertain) ReleaseOutcomeNotice(state.fullName, editorOperation,
                    onCheck = { onAction(ReleasesAction.Refresh) },
                    onAcknowledge = { onAction(ReleasesAction.Acknowledge(editorOperation, state.viewerAccountId)) })
                OutlinedTextField(editor.tagName, { onAction(ReleasesAction.EditEditor(editor.copy(tagName = it))) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canManage && !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_tag_field)) })
                OutlinedTextField(editor.title, { onAction(ReleasesAction.EditEditor(editor.copy(title = it))) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canManage && !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_title_field)) })
                OutlinedTextField(editor.targetCommitish, { onAction(ReleasesAction.EditEditor(editor.copy(targetCommitish = it))) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canManage && !busy, singleLine = true,
                    label = { Text(stringResource(R.string.github_release_target_field)) })
                OutlinedTextField(editor.body, { onAction(ReleasesAction.EditEditor(editor.copy(body = it))) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canManage && !busy, minLines = 6, maxLines = 14,
                    label = { Text(stringResource(R.string.github_release_notes)) })
                ReleaseToggle(stringResource(R.string.github_release_draft), editor.isDraft,
                    { onAction(ReleasesAction.EditEditor(editor.copy(isDraft = it))) }, state.canManage && !busy)
                ReleaseToggle(stringResource(R.string.github_release_prerelease), editor.isPrerelease,
                    { onAction(ReleasesAction.EditEditor(editor.copy(isPrerelease = it))) }, state.canManage && !busy)
                failure?.let { ReleaseMutationFailureMessage(it) }
            }
        }
        return
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
    if (dialog == "delete" && state.canManage && state.journalReady) {
        val failure = (outcome as? ReleaseMutationOutcome.Failed)
            ?.takeIf { it.kind == ReleaseMutationKind.Delete }
            ?.failure
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(if (repeatDeleteRequested) R.string.github_actions_repeat else R.string.github_delete_release)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tag, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (repeatDeleteRequested) R.string.github_release_repeat_warning else R.string.github_delete_release_confirm),
                    style = MaterialTheme.typography.bodySmall
                )
                failure?.let { ReleaseMutationFailureMessage(it) }
            } },
            confirmButton = { TextButton(
                enabled = writable && formId > 0L,
                onClick = {
                    if (!repeatDeleteRequested && GithubActionOperation(GithubActionKind.RELEASE_CHANGE, formId) in uncertain) repeatDeleteRequested = true
                    else onAction(ReleasesAction.Delete(formId, state.viewerAccountId, repeatDeleteRequested))
                }
            ) { Text(stringResource(if (repeatDeleteRequested) R.string.github_actions_repeat else R.string.github_delete_release)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { close() }) {
                Text(stringResource(R.string.github_cancel))
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
            IconButton(onClick = { open("create", null) }, enabled = writable) {
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
                if (state.journalUnavailable) githubFullSpanItem(key = "journal-error") { JournalUnavailableNotice() }
                if (attachmentReadFailed) githubFullSpanItem(key = "attachment-read-error") {
                    Text(stringResource(R.string.github_release_attachment_read_error), color = MaterialTheme.colorScheme.error)
                }
                state.unconfirmedOperations.forEach { operation ->
                    githubFullSpanItem(key = "uncertain-${operation.kind}-${operation.targetId}") {
                        ReleaseOutcomeNotice(state.fullName, operation,
                            onCheck = { onAction(ReleasesAction.Refresh) },
                            onAcknowledge = { onAction(ReleasesAction.Acknowledge(operation, state.viewerAccountId)) })
                    }
                }
                items(state.items, key = GithubRelease::id) { release ->
                    ReleaseListRow(
                        release = release,
                        isBusy = !writable,
                        onClick = { onOpenRelease(release) },
                        onEdit = { open("edit", release) },
                        onSetDraft = { draft ->
                            onAction(ReleasesAction.OpenEditor(release.id, asDraft = draft))
                        },
                        onDelete = { open("delete", release) },
                        onAttach = { if (picker != null) { attachTarget = release.id; picker.launch(arrayOf("*/*")) } },
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
            .fillMaxWidth().githubCollectionSurface()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .githubCollectionPadding()
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
        GithubCollectionDivider(
            modifier = Modifier.padding(top = 13.dp)
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

@Composable
private fun JournalUnavailableNotice() {
    Text(stringResource(R.string.github_actions_journal_error), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseOutcomeNotice(subject: String, operation: GithubActionOperation,
    onCheck: () -> Unit, onAcknowledge: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer, shape = GithubExpressiveShapes.container) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.github_release_uncertain), color = MaterialTheme.colorScheme.onErrorContainer)
            val actionLabel = when (operation.kind) {
                GithubActionKind.RELEASE_CREATE -> R.string.github_new_release
                GithubActionKind.RELEASE_CHANGE -> R.string.github_release_change_operation
                GithubActionKind.RELEASE_UPLOAD -> R.string.github_release_attach_asset
                else -> R.string.github_release_remove_asset
            }
            Text("$subject · ${stringResource(actionLabel)} · #${operation.targetId}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCheck) { Text(stringResource(R.string.github_check_latest)) }
                TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.github_actions_acknowledge)) }
            }
        }
    }
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
    var removeTargetId by remember(state.fullName, state.viewerAccountId) { mutableStateOf<Long?>(null) }
    val removeTarget = release?.assets?.firstOrNull { it.id == removeTargetId }
    var repeatRemoveRequested by remember(state.fullName, state.viewerAccountId, removeTargetId, state.canManage, state.journalReady) { mutableStateOf(false) }
    val writable = state.canManage && state.journalReady && !state.isRemovingAsset
    LaunchedEffect(state.canManage, state.journalReady) {
        if (!state.canManage || !state.journalReady) removeTargetId = null
    }
    if (removeTarget != null && state.canManage && state.journalReady) {
        AlertDialog(
            onDismissRequest = { removeTargetId = null },
            title = { Text(stringResource(if (repeatRemoveRequested) R.string.github_actions_repeat else R.string.github_release_remove_asset)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(removeTarget.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (repeatRemoveRequested) R.string.github_release_repeat_warning else R.string.github_release_remove_asset_confirm),
                    style = MaterialTheme.typography.bodySmall
                )
            } },
            confirmButton = { TextButton(enabled = writable, onClick = {
                val operation = GithubActionOperation(GithubActionKind.RELEASE_ASSET_DELETE, removeTarget.id)
                if (!repeatRemoveRequested && operation in state.unconfirmedOperations) repeatRemoveRequested = true
                else {
                    removeTargetId = null
                    onAction(ReleaseDetailAction.RemoveAsset(removeTarget.id, state.viewerAccountId, repeatRemoveRequested))
                }
            }) { Text(stringResource(if (repeatRemoveRequested) R.string.github_actions_repeat else R.string.github_release_remove_asset)) } },
            dismissButton = { TextButton(onClick = { removeTargetId = null }) {
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
                        onRemoveAsset = { asset -> if (writable) removeTargetId = asset.id },
                        canRemoveAsset = writable,
                        removingAssetId = state.removingAssetId,
                        assetRemovalFailed = state.assetRemovalFailed,
                        notices = {
                            if (state.journalUnavailable) item(key = "journal-error") { JournalUnavailableNotice() }
                            state.unconfirmedOperations.forEach { operation ->
                                item(key = "uncertain-${operation.kind}-${operation.targetId}") {
                                    ReleaseOutcomeNotice(state.fullName, operation,
                                        onCheck = { onAction(ReleaseDetailAction.Retry) },
                                        onAcknowledge = { onAction(ReleaseDetailAction.Acknowledge(operation, state.viewerAccountId)) })
                                }
                            }
                        },
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
    canRemoveAsset: Boolean,
    removingAssetId: Long?,
    assetRemovalFailed: Boolean,
    notices: LazyListScope.() -> Unit,
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
                    assetRemovalFailed = assetRemovalFailed,
                    canRemoveAsset = canRemoveAsset
                )
            }
        }
    ) { paneModifier, expanded ->
        LazyColumn(
            state = listState,
            modifier = paneModifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            notices()
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
                            else listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.let {
                                (it - release.assets.size - 2 - if (assetRemovalFailed) 1 else 0).coerceAtLeast(0)
                            })
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
                            imageContext = MarkdownImageContext(fullName, release.tagName),
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
                assetRemovalFailed = assetRemovalFailed,
                canRemoveAsset = canRemoveAsset
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
    assetRemovalFailed: Boolean,
    canRemoveAsset: Boolean
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
                canRemove = canRemoveAsset,
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
    canRemove: Boolean,
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
            if (canRemove) Box {
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
