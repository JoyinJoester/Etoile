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

@Composable
internal fun DiscussionCommentActions(
    comment: GithubDiscussionComment,
    edit: suspend (String, String) -> Result<GithubDiscussionComment>,
    delete: suspend (String) -> Result<Unit>,
    onEdited: (GithubDiscussionComment) -> Unit,
    onDeleted: () -> Unit,
    enabled: Boolean = true
) {
    if (!comment.canEdit && !comment.canDelete) return
    var editing by rememberSaveable(comment.id) { mutableStateOf(false) }
    var confirming by rememberSaveable(comment.id) { mutableStateOf(false) }
    var draft by rememberSaveable(comment.id) { mutableStateOf(comment.body) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (comment.canEdit) TextButton(enabled = enabled && !busy, onClick = {
            editing = true; failed = false
        }) { Text(stringResource(R.string.discussion_edit_comment)) }
        if (comment.canDelete) TextButton(enabled = enabled && !busy, onClick = {
            confirming = true; failed = false
        }) { Text(stringResource(R.string.discussion_delete_comment)) }
    }
    if (editing && comment.canEdit) AlertDialog(
        onDismissRequest = { if (!busy) editing = false },
        title = { Text(stringResource(R.string.discussion_edit_comment)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { if (it.length <= 65536) draft = it },
                enabled = !busy, minLines = 3, maxLines = 10, modifier = Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.discussion_edit_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = enabled && !busy && draft.isNotBlank(), onClick = {
            busy = true; failed = false
            scope.launch {
                try {
                    edit(comment.id, draft).fold(onSuccess = {
                        draft = it.body; editing = false; onEdited(it)
                    }, onFailure = { failed = true })
                } finally { busy = false }
            }
        }) { Text(stringResource(R.string.discussion_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editing = false }) {
            Text(stringResource(R.string.discussion_cancel))
        } }
    )
    if (confirming && comment.canDelete) AlertDialog(
        onDismissRequest = { if (!busy) confirming = false },
        title = { Text(stringResource(R.string.discussion_delete_comment)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.discussion_delete_comment_confirm))
            Text(comment.body, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (failed) Text(stringResource(R.string.discussion_delete_comment_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = enabled && !busy, onClick = {
            busy = true; failed = false
            scope.launch {
                try {
                    delete(comment.id).fold(onSuccess = { confirming = false; onDeleted() },
                        onFailure = { failed = true })
                } finally { busy = false }
            }
        }) { Text(stringResource(R.string.discussion_delete_comment)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirming = false }) {
            Text(stringResource(R.string.discussion_cancel))
        } }
    )
}
