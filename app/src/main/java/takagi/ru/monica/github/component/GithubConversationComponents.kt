package takagi.ru.monica.github.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubReactionCounts
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun GithubCommentComposer(
    value: String,
    maxLength: Int,
    canWrite: Boolean,
    isValidationError: Boolean,
    isSubmitError: Boolean,
    isSubmitting: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    disabledMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        GithubSectionHeader(title = stringResource(R.string.github_write_comment))
        if (disabledMessage != null) {
            GithubMessageState(title = disabledMessage)
            return@Column
        }
        if (!canWrite) {
            GithubMessageState(
                title = stringResource(R.string.github_sign_in_to_write),
                actionLabel = stringResource(R.string.github_sign_in),
                onAction = onSignIn
            )
            return@Column
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            shape = GithubExpressiveShapes.container,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_comment_hint)) },
                    minLines = 4,
                    maxLines = 12,
                    isError = isValidationError,
                    supportingText = {
                        GithubCharacterCounter(current = value.length, maximum = maxLength)
                    },
                    shape = GithubExpressiveShapes.control
                )
                if (isValidationError) {
                    Text(
                        stringResource(R.string.github_comment_input_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isSubmitError) {
                    Text(
                        stringResource(R.string.github_comment_submit_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = value.isNotBlank() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = GithubExpressiveShapes.control
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.github_submit_comment))
                }
            }
        }
    }
}

@Composable
fun GithubCommentCard(
    comment: GithubIssueComment,
    fullName: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
    ref: String = "HEAD",
    activeReactions: Set<GithubReactionContent> = emptySet(),
    isReactionUpdating: Boolean = false,
    hasReactionError: Boolean = false,
    canReact: Boolean = false,
    onReaction: (GithubReactionContent) -> Unit = {}
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
                suffix = stringResource(
                    R.string.github_comment_metadata_suffix,
                    comment.createdAt.take(10)
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            MarkdownPreviewText(
                markdown = comment.body,
                imageBitmaps = emptyMap(),
                onOpenExternalLink = { target ->
                    onOpenExternal(GithubWebUrls.resolveMarkdownLink(fullName, ref, "", target))
                },
                renderImages = false,
                maxElements = 220,
                modifier = Modifier.padding(top = 10.dp)
            )
            GithubReactionBar(
                counts = comment.reactions,
                activeReactions = activeReactions,
                isUpdating = isReactionUpdating,
                hasError = hasReactionError,
                canReact = canReact,
                onReaction = onReaction,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun GithubReactionBar(
    counts: GithubReactionCounts,
    activeReactions: Set<GithubReactionContent>,
    isUpdating: Boolean,
    hasError: Boolean,
    canReact: Boolean,
    onReaction: (GithubReactionContent) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleReactions = remember(counts, activeReactions) {
        GithubReactionContent.entries.filter { counts.count(it) > 0 || it in activeReactions }
    }
    if (visibleReactions.isEmpty() && !canReact && !hasError) return

    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleReactions.forEach { reaction ->
                FilterChip(
                    selected = reaction in activeReactions,
                    onClick = { onReaction(reaction) },
                    enabled = canReact && !isUpdating,
                    label = { Text("${reaction.emoji()} ${counts.count(reaction)}") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (canReact) {
                Box {
                    FilledTonalIconButton(
                        onClick = { menuExpanded = true },
                        enabled = !isUpdating
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddReaction,
                                contentDescription = stringResource(R.string.github_add_reaction)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        GithubReactionContent.entries.forEach { reaction ->
                            DropdownMenuItem(
                                text = {
                                    Text("${reaction.emoji()} ${stringResource(reaction.labelResource())}")
                                },
                                onClick = {
                                    menuExpanded = false
                                    onReaction(reaction)
                                }
                            )
                        }
                    }
                }
            }
        }
        if (hasError) {
            Text(
                text = stringResource(R.string.github_reaction_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun GithubReactionContent.emoji(): String = when (this) {
    GithubReactionContent.PLUS_ONE -> "👍"
    GithubReactionContent.MINUS_ONE -> "👎"
    GithubReactionContent.LAUGH -> "😄"
    GithubReactionContent.CONFUSED -> "😕"
    GithubReactionContent.HEART -> "❤️"
    GithubReactionContent.HOORAY -> "🎉"
    GithubReactionContent.ROCKET -> "🚀"
    GithubReactionContent.EYES -> "👀"
}

private fun GithubReactionContent.labelResource(): Int = when (this) {
    GithubReactionContent.PLUS_ONE -> R.string.github_reaction_plus_one
    GithubReactionContent.MINUS_ONE -> R.string.github_reaction_minus_one
    GithubReactionContent.LAUGH -> R.string.github_reaction_laugh
    GithubReactionContent.CONFUSED -> R.string.github_reaction_confused
    GithubReactionContent.HEART -> R.string.github_reaction_heart
    GithubReactionContent.HOORAY -> R.string.github_reaction_hooray
    GithubReactionContent.ROCKET -> R.string.github_reaction_rocket
    GithubReactionContent.EYES -> R.string.github_reaction_eyes
}
