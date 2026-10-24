package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.domain.GithubDiscussion
import takagi.ru.monica.github.component.GithubCommentEditorScreen
import takagi.ru.monica.github.component.GithubDetailScaffold

@Composable
fun DiscussionDetailScreen(
    discussion: GithubDiscussion,
    onEdit: suspend (String, String) -> Result<GithubDiscussion>,
    onOpenLink: (String) -> Unit,
    signedIn: Boolean,
    loadComments: suspend (String?) -> Result<takagi.ru.monica.github.domain.GithubDiscussionComments>,
    onMarkAnswer: suspend (String, Boolean) -> Result<Unit>,
    loadReplies: suspend (String, String?) -> Result<takagi.ru.monica.github.domain.GithubDiscussionComments>,
    replyToComment: suspend (String, String) -> Result<String>,
    editComment: suspend (String, String) -> Result<takagi.ru.monica.github.domain.GithubDiscussionComment>,
    deleteComment: suspend (String) -> Result<Unit>,
    onReply: suspend (String) -> Result<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewerAccountId: Long? = null,
    draft: DiscussionDetailDraft = DiscussionDetailDraft(),
    onEditDraftChange: (String, String) -> Unit = { _, _ -> },
    onReplyDraftChange: (String) -> Unit = {},
    onThreadDraftChange: (String, String) -> Unit = { _, _ -> },
    onCommentDraftChange: (String, String) -> Unit = { _, _ -> },
    unconfirmedOperations: Set<String> = emptySet(),
    refreshKey: Long = 0,
    onRefresh: () -> Unit = {},
    onAcknowledgeUnconfirmed: () -> Unit = {},
    refreshing: Boolean = false,
    refreshFailed: Boolean = false
) {
    var currentDiscussion by remember(discussion) { mutableStateOf(discussion) }
    var editing by rememberSaveable(discussion.id) { mutableStateOf(false) }
    var editTitle by rememberSaveable(discussion.id) { mutableStateOf(draft.editTitle ?: discussion.title) }
    var editBody by rememberSaveable(discussion.id) { mutableStateOf(draft.editBody ?: discussion.body) }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf(false) }
    var answered by remember(discussion.id, discussion.answered) { mutableStateOf(discussion.answered) }
    var commentCount by remember(discussion.id, discussion.comments) { mutableIntStateOf(discussion.comments) }
    var reply by rememberSaveable(discussion.id) { mutableStateOf(draft.reply) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var answerBusy by remember { mutableStateOf(false) }
    var answerError by remember { mutableStateOf(false) }
    var replyEditorOpen by rememberSaveable(discussion.id) { mutableStateOf(false) }
    var menuExpanded by remember(discussion.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val threadStates = rememberSaveable(discussion.id, saver = mapSaver(
        save = { states: MutableMap<String, DiscussionThreadState> ->
            states.mapValues { (_, thread) -> arrayListOf(thread.expanded, thread.draft) }
        },
        restore = { saved -> saved.mapValues { (id, value) ->
            val values = value as List<*>
            DiscussionThreadState(DiscussionCommentsLoader(scope) { cursor -> loadReplies(id, cursor) },
                expanded = values[0] as Boolean, draft = values[1] as String)
        }.let { mutableStateMapOf<String, DiscussionThreadState>().apply { putAll(it) } } }
    )) { mutableStateMapOf<String, DiscussionThreadState>() }
    val commentMutations = remember(discussion.id, viewerAccountId) {
        mutableStateMapOf<String, DiscussionCommentMutationState>()
    }
    var editingComment by remember(discussion.id, viewerAccountId) {
        mutableStateOf<takagi.ru.monica.github.domain.GithubDiscussionComment?>(null)
    }
    val openCommentEditor: (takagi.ru.monica.github.domain.GithubDiscussionComment) -> Unit = {
        if (!editBusy && !sending && !answerBusy && threadStates.values.none { thread -> thread.sending } &&
            commentMutations.values.none { mutation -> mutation.busy }) editingComment = it
    }
    val mutationBusy = editBusy || sending || answerBusy || threadStates.values.any { it.sending } ||
        commentMutations.values.any { it.busy }
    androidx.activity.compose.BackHandler(mutationBusy) { }
    val commentsLoader = remember(discussion.id) { DiscussionCommentsLoader(scope, loadComments) }
    val commentsState by commentsLoader.state.collectAsState()
    val comments = commentsState.items
    val nextCursor = commentsState.cursor
    val loading = commentsState.loading
    val loadFailed = commentsState.failed
    val loaded = commentsState.loaded
    val fetchComments: (Boolean) -> Unit = commentsLoader::fetch
    LaunchedEffect(discussion.id, refreshKey) { fetchComments(true) }
    androidx.activity.compose.BackHandler(editing) { if (!editBusy) editing = false }
    val commentToEdit = editingComment
    if (commentToEdit != null) {
        val request = commentMutations.getOrPut(commentToEdit.id) { DiscussionCommentMutationState() }
        val commentBody = request.draft ?: draft.commentEdits[commentToEdit.id] ?: commentToEdit.body
        GithubCommentEditorScreen(
            value = commentBody, maxLength = 65536, canWrite = signedIn && commentToEdit.canEdit,
            isValidationError = false, isSubmitError = request.failed, isSubmitting = request.busy,
            title = stringResource(R.string.discussion_edit_comment), subtitle = commentToEdit.author.orEmpty(),
            submitLabel = stringResource(R.string.discussion_save),
            onValueChange = {
                if (it.length <= 65536) {
                    request.draft = it; request.failed = false; onCommentDraftChange(commentToEdit.id, it)
                }
            },
            onSubmit = {
                if (signedIn && commentToEdit.canEdit && !request.busy && commentBody.isNotBlank()) {
                    request.busy = true; request.failed = false
                    scope.launch {
                        try {
                            editComment(commentToEdit.id, commentBody).fold(onSuccess = { updated ->
                                request.draft = null
                                commentsLoader.edited(updated)
                                threadStates.values.forEach { it.loader.edited(updated) }
                                editingComment = null
                            }, onFailure = { request.failed = true })
                        } finally { request.busy = false }
                    }
                }
            }, onSignIn = onBack, onBack = { if (!request.busy) editingComment = null },
            context = { if (request.failed) Text(stringResource(R.string.discussion_edit_error), color = MaterialTheme.colorScheme.error) }
        )
        return
    }
    if (editing) {
        GithubDetailScaffold(
            title = stringResource(R.string.discussion_edit),
            backContentDescription = stringResource(R.string.discussion_back),
            onBack = { if (!editBusy) editing = false },
            contentMaxWidth = takagi.ru.monica.github.design.GithubAdaptiveLayout.formMaxWidth
        ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(editTitle, { if (it.length <= 256) { editTitle = it; onEditDraftChange(it, editBody) } }, enabled = !editBusy,
                singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discussion_subject)) })
            OutlinedTextField(editBody, { if (it.length <= 65536) { editBody = it; onEditDraftChange(editTitle, it) } }, enabled = !editBusy,
                minLines = 8, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discussion_body)) })
            if (editError) Text(stringResource(R.string.discussion_edit_error), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !editBusy, onClick = { editing = false }) {
                Text(stringResource(R.string.discussion_cancel))
            }
            Button(enabled = !editBusy && editTitle.isNotBlank(), onClick = {
            editBusy = true; editError = false
            scope.launch {
                try {
                    onEdit(editTitle, editBody).fold(onSuccess = { currentDiscussion = it; editing = false },
                        onFailure = { editError = true })
                } finally { editBusy = false }
            }
            }) { Text(stringResource(R.string.discussion_save)) }
            }
        }
        }
        return
    }
    if (replyEditorOpen) {
        val submitReply: () -> Unit = {
            if (signedIn && reply.isNotBlank() && !sending) {
                sending = true
                error = false
                scope.launch {
                    try {
                        onReply(reply).fold(
                            onSuccess = {
                                commentCount++
                                reply = ""
                                replyEditorOpen = false
                                fetchComments(true)
                            },
                            onFailure = { error = true }
                        )
                    } finally { sending = false }
                }
            }
        }
        val confirmRepeat = takagi.ru.monica.github.component.githubConfirmedAction(
            title = stringResource(R.string.discussion_reply),
            message = stringResource(R.string.discussion_unconfirmed),
            subject = "${discussion.url}\n$reply",
            enabled = signedIn && !sending && reply.isNotBlank(),
            confirmationKey = viewerAccountId to discussion.id,
            onConfirm = submitReply
        )
        GithubCommentEditorScreen(
            value = reply,
            maxLength = 65536,
            canWrite = signedIn,
            isValidationError = reply.isBlank() && error,
            isSubmitError = error && reply.isNotBlank(),
            isSubmitting = sending,
            onValueChange = { if (it.length <= 65536) { reply = it; onReplyDraftChange(it); error = false } },
            onSubmit = {
                if ("reply" in unconfirmedOperations) confirmRepeat() else submitReply()
            },
            onSignIn = onBack,
            onBack = { if (!sending) replyEditorOpen = false },
            context = {
                if ("reply" in unconfirmedOperations) {
                    Text(stringResource(R.string.discussion_unconfirmed), color = MaterialTheme.colorScheme.error)
                }
            }
        )
        return
    }
    GithubDetailScaffold(
        modifier = modifier,
        title = stringResource(R.string.discussion_number, discussion.number),
        backContentDescription = stringResource(R.string.discussion_back),
        onBack = { if (!mutationBusy) onBack() },
        actions = {
            if (signedIn && currentDiscussion.canEdit) Box {
                IconButton(enabled = !mutationBusy, onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.github_more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.discussion_edit)) }, onClick = {
                        menuExpanded = false
                        editTitle = draft.editTitle ?: currentDiscussion.title; editBody = draft.editBody ?: currentDiscussion.body
                        editError = false; editing = true
                    })
                }
            }
        }, floatingActionButton = {
        if (signedIn) {
            FloatingActionButton(onClick = { if (!mutationBusy) replyEditorOpen = true }) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = stringResource(R.string.discussion_reply))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (signedIn) 92.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "discussion-body") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentDiscussion.title, style = MaterialTheme.typography.headlineSmall)
                    if (unconfirmedOperations.isNotEmpty()) {
                        Text(stringResource(R.string.discussion_unconfirmed), color = MaterialTheme.colorScheme.error)
                        if (refreshing) CircularProgressIndicator(Modifier.size(24.dp))
                        if (refreshFailed) Text(stringResource(R.string.discussion_load_error), color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRefresh, enabled = !mutationBusy && !refreshing) { Text(stringResource(R.string.discussion_check_latest)) }
                            TextButton(onClick = onAcknowledgeUnconfirmed, enabled = !mutationBusy) { Text(stringResource(R.string.discussion_result_reviewed)) }
                        }
                    }
                    Text("${discussion.category.name} · ${discussion.author ?: stringResource(R.string.discussion_deleted_user)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        takagi.ru.monica.ui.components.MarkdownPreviewText(currentDiscussion.body,
                            onOpenExternalLink = onOpenLink, modifier = Modifier.padding(16.dp))
                    }
                    Text(if (answered) stringResource(R.string.discussion_answered) else stringResource(R.string.discussion_comment_count, commentCount), style = MaterialTheme.typography.labelMedium)
                }
            }
            items(comments, key = { "comment:${it.id}" }, contentType = { "discussion-comment" }) { comment ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    Text(comment.author ?: stringResource(R.string.discussion_deleted_user), style = MaterialTheme.typography.labelLarge)
                    takagi.ru.monica.ui.components.MarkdownPreviewText(comment.body,
                        onOpenExternalLink = onOpenLink)
                    key(comment.id) {
                        if (signedIn) DiscussionCommentActions(comment, deleteComment,
                            onDeleted = {
                                commentsLoader.deleted(comment.id)
                                commentCount = (commentCount - 1).coerceAtLeast(0)
                                if (comment.isAnswer) answered = false
                            }, enabled = !loading && !answerBusy,
                            parentScope = scope, mutationState = commentMutations.getOrPut(comment.id) { DiscussionCommentMutationState() },
                            onEditRequested = openCommentEditor,
                            confirmationKey = viewerAccountId)
                        val thread = threadStates.getOrPut(comment.id) {
                            DiscussionThreadState(DiscussionCommentsLoader(scope) { cursor -> loadReplies(comment.id, cursor) },
                                draft = draft.threadReplies[comment.id].orEmpty())
                        }
                        DiscussionReplies(thread, scope, signedIn,
                            send = { body -> replyToComment(comment.id, body) }, onOpenLink = onOpenLink,
                            delete = deleteComment,
                            onDraftChange = { onThreadDraftChange(comment.id, it) },
                            mutationStates = commentMutations, onEditRequested = openCommentEditor, viewerAccountId = viewerAccountId,
                            uncertainReply = "reply:${comment.id}" in unconfirmedOperations,
                            refreshKey = refreshKey, threadId = comment.id)
                    }
                    if (comment.isAnswer) Text(stringResource(R.string.discussion_answered), color = MaterialTheme.colorScheme.primary)
                    if (signedIn && (if (comment.isAnswer) comment.canUnmarkAnswer else comment.canMarkAnswer)) {
                        val confirmAnswer = takagi.ru.monica.github.component.githubConfirmedAction(
                            title = stringResource(if (comment.isAnswer) R.string.discussion_unmark_answer else R.string.discussion_mark_answer),
                            message = stringResource(R.string.discussion_answer_confirm),
                            subject = "${discussion.url}\n${comment.author.orEmpty()}: ${comment.body.take(240)}",
                            enabled = !mutationBusy && !loading,
                            confirmationKey = viewerAccountId to comment.id
                        ) {
                            answerBusy = true; answerError = false
                            scope.launch {
                                try {
                                    onMarkAnswer(comment.id, !comment.isAnswer).fold(
                                        onSuccess = { answered = !comment.isAnswer; fetchComments(true) }, onFailure = { answerError = true })
                                } finally { answerBusy = false }
                            }
                        }
                        TextButton(enabled = !mutationBusy && !loading, onClick = confirmAnswer) {
                            Text(stringResource(if (comment.isAnswer) R.string.discussion_unmark_answer else R.string.discussion_mark_answer))
                        }
                    }
                }
            }
            item(key = "discussion-list-status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (answerError) Text(stringResource(R.string.discussion_answer_error), color = MaterialTheme.colorScheme.error)
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                    if (loadFailed) {
                        Text(stringResource(R.string.discussion_load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { fetchComments(!loaded) }) { Text(stringResource(R.string.discussion_retry)) }
                    } else if (nextCursor != null) TextButton(enabled = !loading, onClick = { fetchComments(false) }) {
                        Text(stringResource(R.string.discussion_more))
                    }
                    if (!signedIn) {
                        Text(stringResource(R.string.discussion_sign_in), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
