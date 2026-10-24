package takagi.ru.monica.gitlab.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.github.component.githubConfirmedAction
import takagi.ru.monica.github.component.GithubCharacterCounter

@Composable
internal fun gitLabSectionTitle(section: GitLabWorkSection): String = stringResource(when (section) {
    GitLabWorkSection.ISSUES -> R.string.gitlab_issues
    GitLabWorkSection.MERGE_REQUESTS -> R.string.gitlab_merge_requests
    GitLabWorkSection.PIPELINES -> R.string.gitlab_pipelines
})

@Composable
internal fun gitLabStatus(status: String): String = when (status) {
    "opened" -> stringResource(R.string.gitlab_status_opened)
    "closed" -> stringResource(R.string.gitlab_status_closed)
    "merged" -> stringResource(R.string.gitlab_status_merged)
    "all" -> stringResource(R.string.gitlab_status_all)
    "success" -> stringResource(R.string.gitlab_status_success)
    "failed" -> stringResource(R.string.gitlab_status_failed)
    "canceled" -> stringResource(R.string.gitlab_status_canceled)
    "running" -> stringResource(R.string.gitlab_status_running)
    "pending", "created", "preparing", "waiting_for_resource", "scheduled" -> stringResource(R.string.gitlab_status_pending)
    "manual" -> stringResource(R.string.gitlab_status_manual)
    "skipped" -> stringResource(R.string.gitlab_status_skipped)
    else -> status
}

@Composable
internal fun gitLabWorkError(error: GitLabReadError?): String = stringResource(when (error) {
    GitLabReadError.AUTHENTICATION -> R.string.gitlab_work_authentication
    GitLabReadError.PERMISSION -> R.string.gitlab_work_permission
    GitLabReadError.NOT_FOUND -> R.string.gitlab_read_not_found
    GitLabReadError.RATE_LIMIT -> R.string.gitlab_read_rate_limit
    GitLabReadError.CONFLICT -> R.string.gitlab_work_conflict
    GitLabReadError.INVALID_INPUT -> R.string.gitlab_work_invalid_input
    GitLabReadError.INVALID_RESPONSE -> R.string.gitlab_read_invalid
    GitLabReadError.LOG_TOO_LARGE -> R.string.gitlab_log_too_large
    else -> R.string.gitlab_read_network
})

@Composable
internal fun GitLabAccessNotice(error: GitLabReadError?, onAccounts: () -> Unit) {
    if (error == GitLabReadError.AUTHENTICATION || error == GitLabReadError.PERMISSION) {
        TextButton(onClick = onAccounts) { Text(stringResource(R.string.gitlab_accounts)) }
    }
}

@Composable
internal fun gitLabOperationTarget(account: GitLabAccount, projectId: Long, target: String): String =
    stringResource(R.string.gitlab_operation_target, account.username, account.instanceUrl, projectId, target)

@Composable
internal fun GitLabWriteFeedback(error: GitLabReadError?, success: Boolean = false, onRefresh: () -> Unit, onAccounts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (error != null) {
            Text(gitLabWorkError(error), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.gitlab_write_uncertain), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.gitlab_check_remote)) }
            GitLabAccessNotice(error, onAccounts)
        }
        if (success) Text(stringResource(R.string.gitlab_write_succeeded), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun GitLabDraftForm(
    draft: GitLabConversationDraft,
    kind: GitLabConversationKind,
    creating: Boolean,
    busy: Boolean,
    subject: String,
    onChanged: (GitLabConversationDraft) -> Unit,
    onSave: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val confirmCommands = githubConfirmedAction(stringResource(R.string.gitlab_submit_commands), stringResource(R.string.gitlab_quick_actions_warning),
        "$subject\n${draft.title}\n${draft.description}", enabled = !busy && draft.isValid(kind, creating), onConfirm = { onSave(true) })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (creating) R.string.gitlab_create else R.string.gitlab_edit), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.gitlab_draft_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(draft.title, { onChanged(draft.copy(title = it)) }, label = { Text(stringResource(R.string.gitlab_title)) },
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.description, { onChanged(draft.copy(description = it)) }, label = { Text(stringResource(R.string.gitlab_description)) },
            minLines = 5, maxLines = 14, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            supportingText = { GithubCharacterCounter(draft.description.length, 65_536) })
        if (creating && kind == GitLabConversationKind.MERGE_REQUEST) {
            OutlinedTextField(draft.sourceBranch, { onChanged(draft.copy(sourceBranch = it)) }, label = { Text(stringResource(R.string.gitlab_source_branch)) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.targetBranch, { onChanged(draft.copy(targetBranch = it)) }, label = { Text(stringResource(R.string.gitlab_target_branch)) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (gitLabHasQuickActions(draft.description)) confirmCommands() else onSave(false) },
                enabled = !busy && draft.isValid(kind, creating)) { Text(stringResource(R.string.gitlab_submit)) }
            TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.gitlab_close_keep_draft)) }
        }
    }
}

@Composable
internal fun GitLabSelectableBody(text: String) { SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) } }
