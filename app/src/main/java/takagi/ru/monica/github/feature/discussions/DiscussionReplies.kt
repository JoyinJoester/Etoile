package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubDiscussionComment
import takagi.ru.monica.ui.components.MarkdownPreviewText

/** Owned by the detail screen so recycling an off-screen comment cannot cancel a pending reply. */
@Stable
internal class DiscussionThreadState(
    val loader: DiscussionCommentsLoader,
    expanded: Boolean = false,
    draft: String = ""
) {
    var expanded by mutableStateOf(expanded)
    var draft by mutableStateOf(draft)
    var sending by mutableStateOf(false)
    var sendFailed by mutableStateOf(false)
    var readRevision: Long? = null
}

@Composable
internal fun DiscussionReplies(
    state: DiscussionThreadState,
    scope: CoroutineScope,
    signedIn: Boolean,
    send: suspend (String) -> Result<String>,
    onOpenLink: (String) -> Unit,
    delete: suspend (String) -> Result<Unit>,
    onDraftChange: (String) -> Unit = {},
    mutationStates: MutableMap<String, DiscussionCommentMutationState> = mutableMapOf(),
    onEditRequested: (GithubDiscussionComment) -> Unit = {},
    viewerAccountId: Long? = null,
    uncertainReply: Boolean = false,
    refreshKey: Long = 0,
    threadId: String? = null
) {
    val loader = state.loader
    val connection by loader.state.collectAsState()
    val replies = connection.items
    val cursor = connection.cursor
    val loaded = connection.loaded
    val failed = connection.failed
    val busy = state.sending || connection.loading
    val fetch: (Boolean) -> Unit = loader::fetch
    TextButton(onClick = { state.expanded = !state.expanded }) { Text(stringResource(R.string.discussion_thread_replies)) }
    LaunchedEffect(state.expanded, refreshKey) {
        if (state.expanded && (!loaded || state.readRevision != refreshKey)) {
            state.readRevision = refreshKey
            fetch(true)
        }
    }
    if (state.expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        replies.forEach { reply ->
            key(reply.id) {
            Text(reply.author ?: stringResource(R.string.discussion_deleted_user), style = MaterialTheme.typography.labelMedium)
            MarkdownPreviewText(reply.body, onOpenExternalLink = onOpenLink)
            if (signedIn) DiscussionCommentActions(reply, delete,
                onDeleted = { loader.deleted(reply.id) }, enabled = !busy,
                parentScope = scope, mutationState = mutationStates.getOrPut(reply.id) { DiscussionCommentMutationState() },
                onEditRequested = onEditRequested,
                confirmationKey = viewerAccountId)
            HorizontalDivider()
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp))
        if (failed) {
            Text(stringResource(R.string.discussion_load_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { fetch(!loaded) }, enabled = !busy) { Text(stringResource(R.string.discussion_retry)) }
        } else if (cursor != null) TextButton(onClick = { fetch(false) }, enabled = !busy) { Text(stringResource(R.string.discussion_more)) }
        if (signedIn) {
            val submitReply: () -> Unit = {
                if (!busy && state.draft.isNotBlank()) {
                    state.sending = true; state.sendFailed = false
                    scope.launch {
                        try {
                            send(state.draft).fold(onSuccess = { state.draft = ""; fetch(true) }, onFailure = { state.sendFailed = true })
                        } finally { state.sending = false }
                    }
                }
            }
            val confirmRepeat = takagi.ru.monica.github.component.githubConfirmedAction(
                title = stringResource(R.string.discussion_reply),
                message = stringResource(R.string.discussion_unconfirmed),
                subject = state.draft,
                enabled = !busy && state.draft.isNotBlank(),
                confirmationKey = viewerAccountId to threadId,
                onConfirm = submitReply
            )
            if (uncertainReply) Text(stringResource(R.string.discussion_unconfirmed), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(state.draft, { if (it.length <= 65536) { state.draft = it; onDraftChange(it) } }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discussion_reply)) })
            TextButton(enabled = !busy && state.draft.isNotBlank(), onClick = {
                if (uncertainReply) confirmRepeat() else submitReply()
            }) { Text(stringResource(R.string.discussion_reply)) }
            if (state.sendFailed) Text(stringResource(R.string.discussion_reply_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
