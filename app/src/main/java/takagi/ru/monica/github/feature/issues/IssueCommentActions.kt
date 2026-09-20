package takagi.ru.monica.github.feature.issues

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft

@Composable
internal fun IssueCommentActions(comment: GithubIssueComment, busy: Boolean, failed: Boolean,
    onAction: (IssueDetailAction) -> Unit) {
    var editing by rememberSaveable(comment.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(comment.id) { mutableStateOf(false) }
    var draft by rememberSaveable(comment.id) { mutableStateOf(comment.body) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(busy, failed) {
        if (submitted && !busy && !failed) { editing = false; deleting = false; submitted = false }
    }
    Row {
        TextButton(onClick = { draft = comment.body; editing = true }, enabled = !busy) {
            Text(stringResource(R.string.github_comment_edit))
        }
        TextButton(onClick = { deleting = true }, enabled = !busy) {
            Text(stringResource(R.string.github_comment_delete))
        }
    }
    if (editing || deleting) AlertDialog(
        onDismissRequest = { if (!busy) { editing = false; deleting = false; submitted = false } },
        title = { Text(stringResource(if (editing) R.string.github_comment_edit else R.string.github_comment_delete)) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (editing) OutlinedTextField(value = draft, onValueChange = {
                    if (it.length <= GithubIssueCommentDraft.MAX_BODY_LENGTH) draft = it
                }, enabled = !busy, minLines = 4, maxLines = 10)
                else Text(stringResource(R.string.github_comment_delete_confirm))
                if (failed && submitted) Text(stringResource(R.string.github_comment_change_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !busy && (!editing || draft.isNotBlank()), onClick = {
            submitted = true
            onAction(if (editing) IssueDetailAction.EditComment(comment.id, draft) else IssueDetailAction.DeleteComment(comment.id))
        }) { Text(stringResource(R.string.github_comment_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editing = false; deleting = false; submitted = false }) {
            Text(stringResource(android.R.string.cancel))
        } }
    )
}
