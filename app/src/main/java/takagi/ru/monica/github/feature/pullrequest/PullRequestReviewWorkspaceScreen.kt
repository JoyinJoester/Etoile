package takagi.ru.monica.github.feature.pullrequest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.*
import takagi.ru.monica.github.domain.*

@Composable
internal fun pullRequestReviewWorkspaceOverlay(
    detail: PullRequestDetailUiState,
    onAction: (PullRequestDetailAction) -> Unit,
    onSignIn: () -> Unit
): Boolean {
    val state = detail.reviewWorkspace
    val dispatch: (PullRequestReviewWorkspaceAction) -> Unit = { onAction(PullRequestDetailAction.ReviewWorkspace(it)) }
    state.confirmation?.let { confirmation ->
        val message = when (confirmation) {
            is ReviewWorkspaceConfirmation.ReplaceEditor -> stringResource(R.string.github_review_discard_line)
            ReviewWorkspaceConfirmation.DiscardLine -> stringResource(R.string.github_review_discard_line)
            is ReviewWorkspaceConfirmation.Remove -> stringResource(R.string.github_review_remove_draft)
            ReviewWorkspaceConfirmation.Clear -> stringResource(R.string.github_review_clear_draft)
            is ReviewWorkspaceConfirmation.Resolve -> stringResource(
                if (confirmation.resolved) R.string.github_review_resolve_confirm else R.string.github_review_unresolve_confirm)
            ReviewWorkspaceConfirmation.Submit -> pluralStringResource(R.plurals.github_review_batch_confirm, state.comments.size, state.comments.size,
                state.commitSha?.take(12) ?: detail.pullRequest?.head?.sha?.take(12).orEmpty())
        }
        AlertDialog(onDismissRequest = { dispatch(PullRequestReviewWorkspaceAction.Cancel) },
            title = { Text(stringResource(R.string.github_review_confirm_title)) },
            text = { Column {
                Text("${detail.fullName} #${detail.number}")
                Text(message, modifier = Modifier.padding(top = 8.dp))
                if (confirmation == ReviewWorkspaceConfirmation.Submit) {
                    Text(stringResource(when (state.event) {
                        GithubReviewEvent.APPROVE -> R.string.github_approve
                        GithubReviewEvent.REQUEST_CHANGES -> R.string.github_request_changes
                        GithubReviewEvent.COMMENT -> R.string.github_submit_review
                    }), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(state.body.take(300), style = MaterialTheme.typography.bodySmall)
                }
                if (confirmation is ReviewWorkspaceConfirmation.Remove) state.comments.getOrNull(confirmation.index)?.let {
                    Text(it.path, style = MaterialTheme.typography.labelMedium)
                    Text(it.body.take(300), style = MaterialTheme.typography.bodySmall)
                }
                if (confirmation is ReviewWorkspaceConfirmation.Resolve) {
                    val root = state.threads.firstOrNull { it.id == confirmation.id }?.rootCommentId
                    detail.reviewComments.firstOrNull { it.threadId == root }?.let { comment ->
                        Text(comment.path, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        Text(comment.body.take(300), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.Confirm) }) { Text(stringResource(R.string.github_review_confirm)) } },
            dismissButton = { TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.Cancel) }) { Text(stringResource(R.string.github_cancel)) } })
    }
    val editor = state.editor
    if (state.isLineOpen && editor != null) {
        GithubCommentEditorScreen(
            title = stringResource(R.string.github_review_line_title),
            subtitle = stringResource(R.string.github_review_line_subject, editor.path, editor.line,
                stringResource(if (editor.side == GithubDiffSide.LEFT) R.string.github_review_left else R.string.github_review_right)),
            submitLabel = stringResource(if (state.editingIndex != null) R.string.github_review_save_draft else R.string.github_review_post_now),
            value = editor.body, maxLength = (GithubReviewSubmissionDraft.MAX_TOTAL_TEXT - state.body.length -
                state.comments.filterIndexed { index, _ -> index != state.editingIndex }.sumOf { it.body.length }).coerceAtLeast(0),
            canWrite = detail.canReplyToReview,
            isValidationError = state.validationError, isSubmitError = state.sendError, isSubmitting = state.isSending,
            onValueChange = { dispatch(PullRequestReviewWorkspaceAction.LineBody(it)) },
            onSubmit = { dispatch(if (state.editingIndex != null) PullRequestReviewWorkspaceAction.QueueLine else PullRequestReviewWorkspaceAction.SendLine) },
            onSignIn = onSignIn, onBack = { dispatch(PullRequestReviewWorkspaceAction.CloseLine) },
            disabledMessage = if (state.stale || state.commitSha != detail.pullRequest?.head?.sha || !detail.canReplyToReview ||
                detail.pullRequest?.state != GithubPullRequestState.OPEN || detail.pullRequest.isMerged) stringResource(R.string.github_review_stale) else null,
            context = {
                Text(stringResource(R.string.github_review_commit, state.commitSha?.take(12).orEmpty()), style = MaterialTheme.typography.labelMedium)
                if (state.stale || state.commitSha != detail.pullRequest?.head?.sha || !detail.canReplyToReview || detail.pullRequest?.state != GithubPullRequestState.OPEN) {
                    SelectionContainer { Text(editor.body, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { onAction(PullRequestDetailAction.RetryPullRequest); onAction(PullRequestDetailAction.RetryFiles) }, enabled = !state.isSending) {
                        Text(stringResource(R.string.github_retry))
                    }
                }
                if (state.editingIndex == null) {
                    OutlinedButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.QueueLine) },
                        enabled = !state.isSending && !state.stale && editor.body.isNotBlank() && detail.canReplyToReview &&
                            state.commitSha == detail.pullRequest?.head?.sha && detail.pullRequest?.state == GithubPullRequestState.OPEN) {
                        Text(stringResource(R.string.github_review_queue))
                    }
                }
                if (state.sendError) Text(stringResource(R.string.github_review_ambiguous_failure), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.DiscardLine) }, enabled = !state.isSending) {
                    Text(stringResource(R.string.github_review_discard_editor))
                }
            }
        )
        return true
    }
    if (!state.isOpen) return false
    BackHandler { if (!state.isSending) dispatch(PullRequestReviewWorkspaceAction.Close) }
    GithubDetailScaffold(title = stringResource(R.string.github_review_batch_title), subtitle = "${detail.fullName} #${detail.number}",
        backContentDescription = stringResource(R.string.github_back), onBack = { dispatch(PullRequestReviewWorkspaceAction.Close) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val sha = state.commitSha ?: detail.pullRequest?.head?.sha
            Text(stringResource(R.string.github_review_commit, sha?.take(12).orEmpty()), style = MaterialTheme.typography.labelMedium)
            Text(pluralStringResource(R.plurals.github_review_batch_count, state.comments.size, state.comments.size), style = MaterialTheme.typography.titleSmall)
            if (state.stale || sha != detail.pullRequest?.head?.sha || !detail.canReplyToReview || detail.pullRequest?.state != GithubPullRequestState.OPEN) {
                GithubMessageState(title = stringResource(R.string.github_review_stale), actionLabel = stringResource(R.string.github_retry),
                    onAction = { onAction(PullRequestDetailAction.RetryPullRequest); onAction(PullRequestDetailAction.RetryFiles) })
            }
            if (state.editor != null) {
                Text(stringResource(R.string.github_review_unqueued))
                TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.ResumeLine) }, enabled = !state.isSending) {
                    Text(stringResource(R.string.github_review_resume_line))
                }
            }
            state.comments.forEachIndexed { index, comment ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.github_review_line_subject, comment.path, comment.line,
                            stringResource(if (comment.side == GithubDiffSide.LEFT) R.string.github_review_left else R.string.github_review_right)),
                            style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(comment.body, style = MaterialTheme.typography.bodyMedium) }
                        Row {
                            TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.Edit(index)) }, enabled = !state.isSending && state.editor == null) {
                                Text(stringResource(R.string.github_review_edit_draft))
                            }
                            TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.Remove(index)) }, enabled = !state.isSending && state.editor == null) {
                                Text(stringResource(R.string.github_review_remove))
                            }
                        }
                    }
                }
            }
            OutlinedTextField(value = state.body, onValueChange = { dispatch(PullRequestReviewWorkspaceAction.Body(it)) },
                readOnly = state.isSending, label = { Text(stringResource(R.string.github_review_body)) },
                modifier = Modifier.fillMaxWidth(), minLines = 4,
                supportingText = { GithubCharacterCounter(state.body.length + state.comments.sumOf { it.body.length }, GithubReviewSubmissionDraft.MAX_TOTAL_TEXT) })
            val own = detail.viewerLogin == detail.pullRequest?.author?.login
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GithubReviewEvent.entries.forEach { event ->
                    FilterChip(selected = state.event == event, onClick = { dispatch(PullRequestReviewWorkspaceAction.Event(event)) },
                        enabled = !state.isSending && (!own || event == GithubReviewEvent.COMMENT),
                        label = { Text(stringResource(when (event) {
                            GithubReviewEvent.COMMENT -> R.string.github_submit_review
                            GithubReviewEvent.APPROVE -> R.string.github_approve
                            GithubReviewEvent.REQUEST_CHANGES -> R.string.github_request_changes
                        })) })
                }
            }
            if (own) Text(stringResource(R.string.github_review_own_pr), style = MaterialTheme.typography.bodySmall)
            if (state.validationError) Text(stringResource(R.string.github_review_batch_validation), color = MaterialTheme.colorScheme.error)
            if (state.sendError) Text(stringResource(R.string.github_review_ambiguous_failure), color = MaterialTheme.colorScheme.error)
            if (state.isSending) CircularProgressIndicator()
            Button(onClick = { dispatch(PullRequestReviewWorkspaceAction.Submit) },
                enabled = !state.isSending && !state.stale && state.editor == null && detail.canReplyToReview &&
                    detail.pullRequest?.state == GithubPullRequestState.OPEN && sha == detail.pullRequest.head.sha) {
                Text(stringResource(R.string.github_review_send_batch))
            }
            TextButton(onClick = { dispatch(PullRequestReviewWorkspaceAction.Clear) }, enabled = !state.isSending && state.hasDraft) {
                Text(stringResource(R.string.github_review_clear))
            }
        }
    }
    return true
}

@Composable
internal fun PullRequestReviewWorkspaceEntry(state: PullRequestDetailUiState, onAction: (PullRequestDetailAction) -> Unit) {
    if (state.viewerLogin == null) return
    Column {
        FilledTonalButton(onClick = { onAction(PullRequestDetailAction.ReviewWorkspace(PullRequestReviewWorkspaceAction.Open)) }) {
            Text(pluralStringResource(R.plurals.github_review_batch_entry, state.reviewWorkspace.comments.size, state.reviewWorkspace.comments.size))
        }
        if (state.reviewWorkspace.editor != null) {
            TextButton(onClick = { onAction(PullRequestDetailAction.ReviewWorkspace(PullRequestReviewWorkspaceAction.ResumeLine)) }) {
                Text(stringResource(R.string.github_review_resume_line))
            }
        }
        if (state.reviewWorkspace.sent) GithubMessageState(title = stringResource(R.string.github_review_posted))
    }
}

@Composable
internal fun PullRequestReviewThreadControls(state: PullRequestDetailUiState, rootId: Long, onAction: (PullRequestDetailAction) -> Unit) {
    val workspace = state.reviewWorkspace
    val thread = workspace.threads.firstOrNull { it.rootCommentId == rootId } ?: return
    Column {
        Text(stringResource(if (thread.isResolved) R.string.github_review_resolved else R.string.github_review_unresolved), style = MaterialTheme.typography.labelMedium)
        if (thread.isOutdated) Text(stringResource(R.string.github_review_comment_outdated), style = MaterialTheme.typography.bodySmall)
        if (state.viewerLogin != null && (if (thread.isResolved) thread.viewerCanUnresolve else thread.viewerCanResolve)) {
            TextButton(onClick = { onAction(PullRequestDetailAction.ReviewWorkspace(PullRequestReviewWorkspaceAction.Resolve(thread.id, !thread.isResolved))) },
                enabled = thread.id !in workspace.threadBusy && !workspace.isSending) {
                Text(stringResource(if (thread.isResolved) R.string.github_review_unresolve else R.string.github_review_resolve))
            }
        }
        if (thread.id in workspace.threadBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (thread.id in workspace.threadErrors) Text(stringResource(R.string.github_review_thread_error), color = MaterialTheme.colorScheme.error)
    }
}
