package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubDiscussionComment
import takagi.ru.monica.github.domain.GithubDiscussionComments
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
internal fun DiscussionReplies(
    id: String,
    signedIn: Boolean,
    load: suspend (String?) -> Result<GithubDiscussionComments>,
    send: suspend (String) -> Result<String>,
    onOpenLink: (String) -> Unit,
    edit: suspend (String, String) -> Result<GithubDiscussionComment>,
    delete: suspend (String) -> Result<Unit>
) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var sending by remember(id) { mutableStateOf(false) }
    var sendFailed by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loader = remember(id) { DiscussionCommentsLoader(scope, load) }
    val connection by loader.state.collectAsState()
    val replies = connection.items
    val cursor = connection.cursor
    val loaded = connection.loaded
    val failed = connection.failed
    val busy = sending || connection.loading
    val fetch: (Boolean) -> Unit = loader::fetch
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.discussion_thread_replies)) }
    LaunchedEffect(expanded) { if (expanded && !loaded) fetch(true) }
    if (expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        replies.forEach { reply ->
            key(reply.id) {
            Text(reply.author ?: stringResource(R.string.discussion_deleted_user), style = MaterialTheme.typography.labelMedium)
            MarkdownPreviewText(reply.body, onOpenExternalLink = onOpenLink, renderImages = false)
            if (signedIn) DiscussionCommentActions(reply, edit, delete,
                onEdited = loader::edited,
                onDeleted = { loader.deleted(reply.id) }, enabled = !busy)
            HorizontalDivider()
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp))
        if (failed) {
            Text(stringResource(R.string.discussion_load_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { fetch(!loaded) }, enabled = !busy) { Text(stringResource(R.string.discussion_retry)) }
        } else if (cursor != null) TextButton(onClick = { fetch(false) }, enabled = !busy) { Text(stringResource(R.string.discussion_more)) }
        if (signedIn) {
            OutlinedTextField(draft, { if (it.length <= 65536) draft = it }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.discussion_reply)) })
            TextButton(enabled = !busy && draft.isNotBlank(), onClick = {
                sending = true; sendFailed = false
                scope.launch {
                    val result = try { send(draft) } finally { sending = false }
                    result.fold(onSuccess = { draft = ""; fetch(true) }, onFailure = { sendFailed = true })
                }
            }) { Text(stringResource(R.string.discussion_reply)) }
            if (sendFailed) Text(stringResource(R.string.discussion_reply_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
