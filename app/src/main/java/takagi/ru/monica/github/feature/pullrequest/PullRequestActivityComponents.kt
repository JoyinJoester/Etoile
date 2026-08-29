package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCharacterCounter
import takagi.ru.monica.github.component.githubRelativeTime
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.component.GithubOpenOnGithubButton
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubUserMetadataLine
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubMergeMethod
import takagi.ru.monica.github.domain.GithubMergeDraft
import takagi.ru.monica.github.domain.GithubPullRequest
import takagi.ru.monica.github.domain.GithubPullRequestReview
import takagi.ru.monica.github.domain.GithubPullRequestReviewComment
import takagi.ru.monica.github.domain.GithubPullRequestReviewDraft
import takagi.ru.monica.github.domain.GithubPullRequestState
import takagi.ru.monica.github.domain.GithubReviewEvent
import takagi.ru.monica.github.domain.GithubReviewState
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
internal fun PullRequestReviewCard(
    review: GithubPullRequestReview,
    fullName: String,
    ref: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReviewStateBadge(review.state)
                Spacer(Modifier.width(10.dp))
                GithubUserMetadataLine(
                    prefix = "",
                    login = review.author.login,
                    avatarUrl = review.author.avatarUrl,
                    suffix = stringResource(
                        R.string.github_review_metadata_suffix,
                        review.submittedAt?.take(10).orEmpty()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            GithubOpenOnGithubButton(onClick = { onOpenExternal(review.htmlUrl) })
            }
            if (review.body.isNotBlank()) {
                MarkdownPreviewText(
                    markdown = review.body,
                    imageBitmaps = emptyMap(),
                    onOpenExternalLink = { target ->
                        onOpenExternal(GithubWebUrls.resolveMarkdownLink(fullName, ref, "", target))
                    },
                    renderImages = false,
                    maxElements = 220,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
internal fun PullRequestReviewCommentCard(
    comment: GithubPullRequestReviewComment,
    fullName: String,
    ref: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            GithubUserMetadataLine(
                prefix = "",
                login = comment.author.login,
                avatarUrl = comment.author.avatarUrl,
                suffix = githubRelativeTime(comment.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.github_review_comment_location,
                    comment.path,
                    comment.line ?: comment.startLine ?: 0
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (comment.diffHunk.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = GithubExpressiveShapes.compact,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = comment.diffHunk,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        maxLines = 4
                    )
                }
            }
            MarkdownPreviewText(
                markdown = comment.body,
                imageBitmaps = emptyMap(),
                onOpenExternalLink = { target ->
                    onOpenExternal(GithubWebUrls.resolveMarkdownLink(fullName, ref, comment.path, target))
                },
                renderImages = false,
                maxElements = 220,
                modifier = Modifier.padding(top = 10.dp)
            )
            GithubOpenOnGithubButton(
                onClick = { onOpenExternal(comment.htmlUrl) },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
internal fun PullRequestReviewComposer(
    body: String,
    canWrite: Boolean,
    isValidationError: Boolean,
    isSubmitError: Boolean,
    isSubmitting: Boolean,
    onBodyChanged: (String) -> Unit,
    onSubmit: (GithubReviewEvent) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        GithubSectionHeader(title = stringResource(R.string.github_review_pull_request))
        if (!canWrite) {
            GithubMessageState(
                title = stringResource(R.string.github_sign_in_to_write),
                actionLabel = stringResource(R.string.github_sign_in),
                onAction = onSignIn
            )
            return@Column
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_review_body)) },
                    minLines = 4,
                    maxLines = 12,
                    isError = isValidationError,
                    supportingText = {
                        GithubCharacterCounter(
                            current = body.length,
                            maximum = GithubPullRequestReviewDraft.MAX_BODY_LENGTH
                        )
                    },
                    shape = GithubExpressiveShapes.control
                )
                if (isValidationError) {
                    Text(
                        text = stringResource(R.string.github_review_input_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isSubmitError) {
                    Text(
                        text = stringResource(R.string.github_review_submit_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(24.dp),
                        strokeWidth = 3.dp
                    )
                }
                FilledTonalButton(
                    onClick = { onSubmit(GithubReviewEvent.APPROVE) },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = GithubExpressiveShapes.control
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.github_approve))
                }
                OutlinedButton(
                    onClick = { onSubmit(GithubReviewEvent.REQUEST_CHANGES) },
                    enabled = body.isNotBlank() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = GithubExpressiveShapes.control
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.github_request_changes))
                }
                TextButton(
                    onClick = { onSubmit(GithubReviewEvent.COMMENT) },
                    enabled = body.isNotBlank() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.github_submit_review))
                }
            }
        }
    }
}

@Composable
internal fun PullRequestActionsCard(
    pullRequest: GithubPullRequest,
    canWrite: Boolean,
    isMerging: Boolean,
    mergeError: Boolean,
    mergeValidationError: Boolean,
    mergeSucceeded: Boolean,
    onMerge: (GithubMergeMethod, String, String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMethodName by rememberSaveable(pullRequest.id) {
        mutableStateOf(GithubMergeMethod.SQUASH.name)
    }
    val methods = GithubMergeMethod.entries
    val selectedMethod = methods.firstOrNull { it.name == selectedMethodName } ?: GithubMergeMethod.SQUASH
    var pendingMergeMethodName by rememberSaveable(pullRequest.id) { mutableStateOf<String?>(null) }
    var mergeCommitTitle by rememberSaveable(pullRequest.id) { mutableStateOf("") }
    var mergeCommitMessage by rememberSaveable(pullRequest.id) { mutableStateOf("") }
    val pendingMergeMethod = methods.firstOrNull { it.name == pendingMergeMethodName }

    if (pendingMergeMethod != null) {
        AlertDialog(
            onDismissRequest = {
                pendingMergeMethodName = null
                mergeCommitTitle = ""
                mergeCommitMessage = ""
            },
            title = { Text(stringResource(R.string.github_confirm_merge_pr_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.github_confirm_merge_pr_message,
                            pullRequest.head.label,
                            pullRequest.base.label,
                            pullRequestMergeActionLabel(pendingMergeMethod)
                        )
                    )
                    if (pendingMergeMethod != GithubMergeMethod.REBASE) {
                        OutlinedTextField(
                            value = mergeCommitTitle,
                            onValueChange = {
                                if (it.length <= GithubMergeDraft.MAX_TITLE_LENGTH) mergeCommitTitle = it
                            },
                            label = { Text(stringResource(R.string.github_merge_commit_title)) },
                            supportingText = {
                                GithubCharacterCounter(
                                    current = mergeCommitTitle.length,
                                    maximum = GithubMergeDraft.MAX_TITLE_LENGTH
                                )
                            },
                            maxLines = 2,
                            shape = GithubExpressiveShapes.control,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                        OutlinedTextField(
                            value = mergeCommitMessage,
                            onValueChange = {
                                if (it.length <= GithubMergeDraft.MAX_MESSAGE_LENGTH) mergeCommitMessage = it
                            },
                            label = { Text(stringResource(R.string.github_merge_commit_message)) },
                            supportingText = {
                                GithubCharacterCounter(
                                    current = mergeCommitMessage.length,
                                    maximum = GithubMergeDraft.MAX_MESSAGE_LENGTH
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            shape = GithubExpressiveShapes.control,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.github_merge_head_sha_guard, pullRequest.head.sha.take(12)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMergeMethodName = null
                        onMerge(pendingMergeMethod, mergeCommitTitle, mergeCommitMessage)
                        mergeCommitTitle = ""
                        mergeCommitMessage = ""
                    }
                ) {
                    Text(stringResource(R.string.github_confirm_merge))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingMergeMethodName = null
                        mergeCommitTitle = ""
                        mergeCommitMessage = ""
                    }
                ) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }

    Column(modifier = modifier.padding(bottom = 24.dp)) {
        GithubSectionHeader(title = stringResource(R.string.github_merge_pull_request))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when {
                    pullRequest.isMerged || mergeSucceeded -> {
                        Text(
                            text = stringResource(R.string.github_merged_success),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    !canWrite -> {
                        GithubMessageState(
                            title = stringResource(R.string.github_sign_in_to_write),
                            actionLabel = stringResource(R.string.github_sign_in),
                            onAction = onSignIn
                        )
                    }
                    pullRequest.state == GithubPullRequestState.OPEN -> {
                        Text(
                            text = stringResource(R.string.github_merge_method),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        GithubFilterRow(
                            labels = listOf(
                                stringResource(R.string.github_merge),
                                stringResource(R.string.github_squash),
                                stringResource(R.string.github_rebase)
                            ),
                            selectedIndex = methods.indexOf(selectedMethod),
                            onSelected = { selectedMethodName = methods[it].name },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (mergeError) {
                            Text(
                                text = stringResource(R.string.github_merge_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        if (mergeValidationError) {
                            Text(
                                text = stringResource(R.string.github_merge_validation_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        Button(
                            onClick = { pendingMergeMethodName = selectedMethod.name },
                            enabled = !isMerging && pullRequest.mergeable != false,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = GithubExpressiveShapes.control
                        ) {
                            if (isMerging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(pullRequestMergeActionLabel(selectedMethod))
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun ReviewStateBadge(state: GithubReviewState) {
    val approved = state == GithubReviewState.APPROVED
    val changesRequested = state == GithubReviewState.CHANGES_REQUESTED
    val container = when {
        approved -> MaterialTheme.colorScheme.primaryContainer
        changesRequested -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        approved -> MaterialTheme.colorScheme.onPrimaryContainer
        changesRequested -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = GithubExpressiveShapes.control, color = container) {
        Text(
            text = reviewStateLabel(state),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun reviewStateLabel(state: GithubReviewState): String = stringResource(
    when (state) {
        GithubReviewState.APPROVED -> R.string.github_review_approved
        GithubReviewState.CHANGES_REQUESTED -> R.string.github_review_changes_requested
        GithubReviewState.COMMENTED -> R.string.github_review_commented
        GithubReviewState.DISMISSED -> R.string.github_review_dismissed
        GithubReviewState.PENDING -> R.string.github_review_pending
        GithubReviewState.UNKNOWN -> R.string.github_review_commented
    }
)

@Composable
private fun pullRequestMergeActionLabel(method: GithubMergeMethod): String = stringResource(
    when (method) {
        GithubMergeMethod.MERGE -> R.string.github_merge
        GithubMergeMethod.SQUASH -> R.string.github_squash
        GithubMergeMethod.REBASE -> R.string.github_rebase
    }
)
