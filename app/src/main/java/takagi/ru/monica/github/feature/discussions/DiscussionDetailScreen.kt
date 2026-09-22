package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.domain.GithubDiscussion
import takagi.ru.monica.github.component.GithubCommentEditorScreen

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
    modifier: Modifier = Modifier
) {
    var currentDiscussion by remember(discussion) { mutableStateOf(discussion) }
    var editing by rememberSaveable(discussion.id) { mutableStateOf(false) }
    var editTitle by rememberSaveable(discussion.id) { mutableStateOf(discussion.title) }
    var editBody by rememberSaveable(discussion.id) { mutableStateOf(discussion.body) }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf(false) }
    var answered by rememberSaveable(discussion.id) { mutableStateOf(discussion.answered) }
    var commentCount by rememberSaveable(discussion.id) { mutableIntStateOf(discussion.comments) }
    var reply by rememberSaveable(discussion.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var answerBusy by remember { mutableStateOf(false) }
    var answerError by remember { mutableStateOf(false) }
    var replyEditorOpen by rememberSaveable(discussion.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val commentsLoader = remember(discussion.id) { DiscussionCommentsLoader(scope, loadComments) }
    val commentsState by commentsLoader.state.collectAsState()
    val comments = commentsState.items
    val nextCursor = commentsState.cursor
    val loading = commentsState.loading
    val loadFailed = commentsState.failed
    val loaded = commentsState.loaded
    val fetchComments: (Boolean) -> Unit = commentsLoader::fetch
    if (editing) AlertDialog(
        onDismissRequest = { if (!editBusy) editing = false },
        title = { Text(stringResource(R.string.discussion_edit)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(editTitle, { if (it.length <= 256) editTitle = it }, enabled = !editBusy,
                label = { Text(stringResource(R.string.discussion_subject)) })
            OutlinedTextField(editBody, { if (it.length <= 65536) editBody = it }, enabled = !editBusy,
                minLines = 4, maxLines = 10, label = { Text(stringResource(R.string.discussion_body)) })
            if (editError) Text(stringResource(R.string.discussion_edit_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !editBusy && editTitle.isNotBlank(), onClick = {
            editBusy = true; editError = false
            scope.launch {
                try {
                    onEdit(editTitle, editBody).fold(onSuccess = { currentDiscussion = it; editing = false },
                        onFailure = { editError = true })
                } finally { editBusy = false }
            }
        }) { Text(stringResource(R.string.discussion_save)) } },
        dismissButton = { TextButton(enabled = !editBusy, onClick = { editing = false }) {
            Text(stringResource(R.string.discussion_cancel))
        } }
    )
    LaunchedEffect(discussion.id) { fetchComments(true) }
    if (replyEditorOpen) {
        GithubCommentEditorScreen(
            value = reply,
            maxLength = 65536,
            canWrite = signedIn,
            isValidationError = reply.isBlank() && error,
            isSubmitError = error && reply.isNotBlank(),
            isSubmitting = sending,
            onValueChange = { if (it.length <= 65536) { reply = it; error = false } },
            onSubmit = {
                if (reply.isNotBlank() && !sending) {
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
                        } finally {
                            sending = false
                        }
                    }
                }
            },
            onSignIn = onBack,
            onBack = { if (!sending) replyEditorOpen = false }
        )
        return
    }
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.discussion_number, discussion.number)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.discussion_back)) }
    }) }, floatingActionButton = {
        if (signedIn) {
            FloatingActionButton(onClick = { replyEditorOpen = true }) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = stringResource(R.string.discussion_reply))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signedIn && currentDiscussion.canEdit) TextButton(onClick = {
                editTitle = currentDiscussion.title; editBody = currentDiscussion.body; editError = false; editing = true
            }) { Text(stringResource(R.string.discussion_edit)) }
            Text(currentDiscussion.title, style = MaterialTheme.typography.headlineSmall)
            Text("${discussion.category.name} · ${discussion.author ?: stringResource(R.string.discussion_deleted_user)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                takagi.ru.monica.ui.components.MarkdownPreviewText(currentDiscussion.body,
                    onOpenExternalLink = onOpenLink, renderImages = false, modifier = Modifier.padding(16.dp))
            }
            Text(if (answered) stringResource(R.string.discussion_answered) else stringResource(R.string.discussion_comment_count, commentCount), style = MaterialTheme.typography.labelMedium)
            comments.forEach { comment ->
                HorizontalDivider()
                Text(comment.author ?: stringResource(R.string.discussion_deleted_user), style = MaterialTheme.typography.labelLarge)
                takagi.ru.monica.ui.components.MarkdownPreviewText(comment.body,
                    onOpenExternalLink = onOpenLink, renderImages = false)
                key(comment.id) {
                    if (signedIn) DiscussionCommentActions(comment, editComment, deleteComment,
                        onEdited = commentsLoader::edited,
                        onDeleted = {
                            commentsLoader.deleted(comment.id)
                            commentCount = (commentCount - 1).coerceAtLeast(0)
                            if (comment.isAnswer) answered = false
                        }, enabled = !loading && !answerBusy)
                    DiscussionReplies(comment.id, signedIn,
                        load = { cursor -> loadReplies(comment.id, cursor) },
                        send = { body -> replyToComment(comment.id, body) }, onOpenLink = onOpenLink,
                        edit = editComment, delete = deleteComment)
                }
                if (comment.isAnswer) Text(stringResource(R.string.discussion_answered), color = MaterialTheme.colorScheme.primary)
                if (signedIn && (if (comment.isAnswer) comment.canUnmarkAnswer else comment.canMarkAnswer)) {
                    TextButton(enabled = !answerBusy && !loading, onClick = {
                        answerBusy = true; answerError = false
                        scope.launch {
                            try {
                                onMarkAnswer(comment.id, !comment.isAnswer).fold(
                                    onSuccess = { answered = !comment.isAnswer; fetchComments(true) }, onFailure = { answerError = true })
                            } finally { answerBusy = false }
                        }
                    }) { Text(stringResource(if (comment.isAnswer) R.string.discussion_unmark_answer else R.string.discussion_mark_answer)) }
                }
            }
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
