package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubDiscussionComment

/** The detail screen owns in-flight state when a virtualized row leaves composition. */
@Stable
internal class DiscussionCommentMutationState {
    var busy by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var deleting by mutableStateOf(false)
    var draft by mutableStateOf<String?>(null)
}

@Composable
internal fun DiscussionCommentActions(
    comment: GithubDiscussionComment,
    delete: suspend (String) -> Result<Unit>,
    onDeleted: () -> Unit,
    enabled: Boolean = true,
    parentScope: CoroutineScope? = null,
    mutationState: DiscussionCommentMutationState? = null,
    onEditRequested: (GithubDiscussionComment) -> Unit = {},
    confirmationKey: Any? = null
) {
    if (!comment.canEdit && !comment.canDelete) return
    var confirming by remember(comment.id, comment.canDelete, confirmationKey) { mutableStateOf(false) }
    val request = mutationState ?: remember(comment.id, confirmationKey) { DiscussionCommentMutationState() }
    val localScope = rememberCoroutineScope()
    val scope = parentScope ?: localScope
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (comment.canEdit) TextButton(enabled = enabled && !request.busy, onClick = {
            request.failed = false; request.deleting = false; onEditRequested(comment)
        }) { Text(stringResource(R.string.discussion_edit_comment)) }
        if (comment.canDelete) TextButton(enabled = enabled && !request.busy, onClick = {
            confirming = true; request.failed = false; request.deleting = true
        }) { Text(stringResource(R.string.discussion_delete_comment)) }
    }
    if (request.failed && !confirming) Text(
        stringResource(if (request.deleting) R.string.discussion_delete_comment_error else R.string.discussion_edit_error),
        color = MaterialTheme.colorScheme.error)
    if (confirming && comment.canDelete) AlertDialog(
        onDismissRequest = { if (!request.busy) confirming = false },
        title = { Text(stringResource(R.string.discussion_delete_comment)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.discussion_delete_comment_confirm))
            Text(comment.body, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (request.failed) Text(stringResource(R.string.discussion_delete_comment_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = enabled && !request.busy, onClick = {
            if (!request.busy) {
                request.busy = true; request.failed = false
                scope.launch {
                    try {
                        delete(comment.id).fold(onSuccess = { confirming = false; onDeleted() },
                            onFailure = { request.failed = true })
                    } finally { request.busy = false }
                }
            }
        }) { Text(stringResource(R.string.discussion_delete_comment)) } },
        dismissButton = { TextButton(enabled = !request.busy, onClick = { confirming = false }) {
            Text(stringResource(R.string.discussion_cancel))
        } }
    )
}
