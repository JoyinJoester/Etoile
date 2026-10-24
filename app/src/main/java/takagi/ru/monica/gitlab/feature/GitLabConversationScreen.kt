package takagi.ru.monica.gitlab.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.*
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.gitlab.navigation.gitLabDocumentUrl
import takagi.ru.monica.ui.components.MarkdownPreviewText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitLabConversationScreen(
    state: GitLabConversationState,
    projectId: Long,
    number: Long,
    onAction: (GitLabConversationAction) -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit
) {
    val notesListState = key(state.account?.key, projectId, state.kind, number) { rememberLazyListState() }
    val diffsListState = key(state.account?.key, projectId, state.kind, number) { rememberLazyListState() }
    var composerOpen by rememberSaveable(state.account?.key, projectId, state.kind, number) { mutableStateOf(false) }
    var commentSubmitted by rememberSaveable(state.account?.key, projectId, state.kind, number) { mutableStateOf(false) }
    LaunchedEffect(state.writing, state.writeSucceeded, state.comment, commentSubmitted) {
        if (commentSubmitted && !state.writing) {
            if (state.writeSucceeded && state.comment.isEmpty()) composerOpen = false
            if (state.writeSucceeded || state.writeError != null) commentSubmitted = false
        }
    }
    val back = { if (state.editing && !state.writing) onAction(GitLabConversationAction.Editing(false)) else if (!state.writing) onBack() }
    BackHandler(enabled = state.editing || state.writing, onBack = back)
    val account = state.account
    val conversation = state.item
    val documentUrl = remember(account?.instanceUrl, conversation?.webUrl) {
        account?.let { gitLabDocumentUrl(it.instanceUrl, conversation?.webUrl.orEmpty()) }
    }
    if (account != null && conversation != null && state.editing) {
        GithubDetailScaffold(
            title = stringResource(R.string.gitlab_edit),
            subtitle = "#$number · ${account.instanceUrl}",
            backContentDescription = stringResource(R.string.github_back), onBack = back,
            showGithubServiceStatus = false,
            contentMaxWidth = takagi.ru.monica.github.design.GithubAdaptiveLayout.formMaxWidth
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GitLabWriteFeedback(state.writeError, onRefresh = { onAction(GitLabConversationAction.Refresh) }, onAccounts = onAccounts)
                GitLabDraftForm(state.draft, state.kind, creating = false, busy = state.writing,
                    subject = gitLabOperationTarget(account, projectId, "#$number"),
                    onChanged = { onAction(GitLabConversationAction.Draft(it)) }, onSave = { onAction(GitLabConversationAction.Save(it)) },
                    onClose = { onAction(GitLabConversationAction.Editing(false)) })
            }
        }
        return
    }
    if (account != null && conversation != null && composerOpen) {
        val canSend = state.comment.isNotBlank() && state.comment.length <= 65_536 && !state.writing && !conversation.discussionLocked
        val send: (Boolean) -> Unit = { confirmed ->
            commentSubmitted = true
            onAction(GitLabConversationAction.Send(confirmed))
        }
        val commands = githubConfirmedAction(stringResource(R.string.gitlab_submit_commands), stringResource(R.string.gitlab_quick_actions_warning),
            gitLabOperationTarget(account, projectId, "#$number\n${state.comment}"), enabled = canSend,
            onConfirm = { send(true) })
        GithubCommentEditorScreen(
            value = state.comment, maxLength = 65_536, canWrite = !conversation.discussionLocked,
            isValidationError = state.comment.length > 65_536, isSubmitError = state.writeError != null,
            isSubmitting = state.writing,
            onValueChange = { onAction(GitLabConversationAction.Comment(it)) },
            onSubmit = { if (canSend) { if (gitLabHasQuickActions(state.comment)) commands() else send(false) } },
            onSignIn = onAccounts, onBack = { if (!state.writing) { composerOpen = false; commentSubmitted = false } },
            title = stringResource(R.string.gitlab_send_comment), subtitle = "#$number · ${account.instanceUrl}",
            submitLabel = stringResource(R.string.gitlab_send_comment), showGithubServiceStatus = false,
            disabledMessage = if (conversation.discussionLocked) stringResource(R.string.gitlab_discussion_locked) else null,
            context = {
                Text(conversation.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                GitLabWriteFeedback(state.writeError, onRefresh = { onAction(GitLabConversationAction.Refresh) }, onAccounts = onAccounts)
            }
        )
        return
    }
    GithubDetailScaffold(
        title = stringResource(R.string.gitlab_conversation_heading, number, state.item?.title.orEmpty()),
        subtitle = state.account?.instanceUrl, backContentDescription = stringResource(R.string.github_back), onBack = back,
        showGithubServiceStatus = false,
        actions = { IconButton(onClick = { onAction(GitLabConversationAction.Refresh) }, enabled = state.account != null && !state.writing && !state.loading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.gitlab_refresh))
        }
            if (account != null && conversation != null) GitLabConversationMenu(state, account, conversation, projectId, number, onAction)
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = if (state.showDiff) diffsListState else notesListState,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.account == null) item(key = "account-required") {
                GithubMessageState(stringResource(R.string.gitlab_account_required), actionLabel = stringResource(R.string.gitlab_accounts), onAction = onAccounts)
            } else {
                if (state.loading || state.writing) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.writeError != null || state.writeSucceeded) item(key = "write-feedback") {
                    GitLabWriteFeedback(state.writeError, state.writeSucceeded, { onAction(GitLabConversationAction.Refresh) }, onAccounts)
                }
                state.error?.let { error -> item(key = "load-error") {
                    Column {
                        GithubMessageState(gitLabWorkError(error), actionLabel = stringResource(R.string.github_retry), onAction = { onAction(GitLabConversationAction.Refresh) })
                        GitLabAccessNotice(error, onAccounts)
                    }
                } }
                state.item?.let { item ->
                    item(key = "conversation-body") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, style = MaterialTheme.typography.headlineSmall)
                            Text(gitLabStatus(item.state), color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.gitlab_author_updated, item.author, item.updatedAt), style = MaterialTheme.typography.bodySmall)
                            if (item.description.isNotEmpty()) MarkdownPreviewText(item.description, documentBaseUrl = documentUrl)
                            if (state.kind == GitLabConversationKind.MERGE_REQUEST) {
                                GitLabSelectableBody(stringResource(R.string.gitlab_merge_branches, item.sourceBranch, item.targetBranch, item.sha))
                                if (item.draft) Text(stringResource(R.string.gitlab_draft_merge_request))
                                Text(stringResource(R.string.gitlab_merge_status, item.mergeStatus), style = MaterialTheme.typography.bodySmall)
                                if (!item.canMerge && item.state == "opened") Text(stringResource(R.string.gitlab_merge_blocked), style = MaterialTheme.typography.bodySmall)
                                GithubFilterRow(listOf(stringResource(R.string.gitlab_comments), stringResource(R.string.gitlab_diffs)),
                                    if (state.showDiff) 1 else 0, { onAction(GitLabConversationAction.ShowDiff(it == 1)) })
                            }
                        }
                    }
                    if (state.showDiff) {
                        items(state.diffs, key = { "diff:${it.oldPath}:${it.newPath}" }) { diff ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(diff.newPath, style = MaterialTheme.typography.titleSmall)
                                    if (diff.oldPath != diff.newPath) Text(diff.oldPath, style = MaterialTheme.typography.bodySmall)
                                    if (diff.unavailable) Text(stringResource(R.string.gitlab_diff_unavailable))
                                    else {
                                        Text(stringResource(R.string.gitlab_diff_preview_hint), style = MaterialTheme.typography.bodySmall)
                                        SelectionContainer {
                                            Text(diff.patch.take(32_000), Modifier.horizontalScroll(rememberScrollState()),
                                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false,
                                                maxLines = 300, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "diffs-status") {
                            GithubPagedListStatus(state.diffs.size, state.diffsLoading && state.diffs.isEmpty(), state.diffsLoading && state.diffs.isNotEmpty(),
                                state.diffsError != null, state.diffsNext != null && !state.writing, gitLabWorkError(state.diffsError), stringResource(R.string.gitlab_no_diffs),
                                { onAction(GitLabConversationAction.Diffs(state.diffs.isNotEmpty() && state.diffsNext != null)) }, { onAction(GitLabConversationAction.Diffs(true)) })
                            if (state.diffsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    } else {
                        item(key = "comments-heading") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.gitlab_comments), style = MaterialTheme.typography.titleMedium)
                                if (state.comment.isNotEmpty()) Text(stringResource(R.string.gitlab_draft_hint), style = MaterialTheme.typography.bodySmall)
                                if (item.discussionLocked) Text(stringResource(R.string.gitlab_discussion_locked))
                                FilledTonalButton(onClick = { composerOpen = true }, enabled = !state.writing && !item.discussionLocked) {
                                    Text(stringResource(R.string.gitlab_send_comment))
                                }
                            }
                        }
                        items(state.notes, key = { "note:${it.id}" }) { note ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.gitlab_author_updated, note.author, note.createdAt), style = MaterialTheme.typography.labelMedium)
                                    if (note.system) Text(stringResource(R.string.gitlab_system_note), style = MaterialTheme.typography.labelSmall)
                                    MarkdownPreviewText(note.body, documentBaseUrl = documentUrl)
                                }
                            }
                        }
                        item(key = "notes-status") {
                            GithubPagedListStatus(state.notes.size, state.notesLoading && state.notes.isEmpty(), state.notesLoading && state.notes.isNotEmpty(),
                                state.notesError != null, state.notesNext != null && !state.writing, gitLabWorkError(state.notesError), stringResource(R.string.gitlab_no_comments),
                                { onAction(GitLabConversationAction.Notes(state.notes.isNotEmpty() && state.notesNext != null)) }, { onAction(GitLabConversationAction.Notes(true)) })
                            if (state.notesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitLabConversationMenu(
    state: GitLabConversationState, account: GitLabAccount, item: GitLabConversation,
    projectId: Long, number: Long, onAction: (GitLabConversationAction) -> Unit
) {
    var expanded by remember(account.key, projectId, number) { mutableStateOf(false) }
    val enabled = !state.writing && !state.loading
    val stateTitle = stringResource(if (item.state == "opened") R.string.gitlab_close_conversation else R.string.gitlab_reopen_conversation)
    val change = githubConfirmedAction(stateTitle, stringResource(R.string.gitlab_state_warning),
        gitLabOperationTarget(account, projectId, "#$number · ${item.title}\n${item.state}"), enabled = enabled,
        onConfirm = { onAction(GitLabConversationAction.ChangeState(item.state)) })
    val target = GitLabMergeTarget(number, item.sha, item.sourceBranch, item.targetBranch)
    val merge = githubConfirmedAction(stringResource(R.string.gitlab_merge), stringResource(R.string.gitlab_merge_warning),
        gitLabOperationTarget(account, projectId, "#$number\n${item.sourceBranch} → ${item.targetBranch}\n${item.sha}"),
        enabled = enabled && item.canMerge, onConfirm = { onAction(GitLabConversationAction.Merge(target)) })
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.github_more_actions))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.gitlab_edit)) }, enabled = enabled && item.state != "merged",
                onClick = { expanded = false; onAction(GitLabConversationAction.Editing(true)) })
            if (item.state in setOf("opened", "closed")) DropdownMenuItem(text = { Text(stateTitle) }, enabled = enabled,
                onClick = { expanded = false; change() })
            if (state.kind == GitLabConversationKind.MERGE_REQUEST) DropdownMenuItem(text = { Text(stringResource(R.string.gitlab_merge)) },
                enabled = enabled && item.canMerge, onClick = { expanded = false; merge() })
        }
    }
}
