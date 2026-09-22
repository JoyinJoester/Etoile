package takagi.ru.monica.github.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/** Full-page editor used by conversation details instead of an inline composer. */
@Composable
fun GithubCommentEditorScreen(
    value: String,
    maxLength: Int,
    canWrite: Boolean,
    isValidationError: Boolean,
    isSubmitError: Boolean,
    isSubmitting: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    disabledMessage: String? = null
) {
    BackHandler { if (!isSubmitting) onBack() }
    GithubDetailScaffold(
        title = stringResource(R.string.github_write_comment),
        subtitle = stringResource(R.string.github_comment_editor_subtitle),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            GithubCommentComposer(
                value = value,
                maxLength = maxLength,
                canWrite = canWrite,
                isValidationError = isValidationError,
                isSubmitError = isSubmitError,
                isSubmitting = isSubmitting,
                onValueChange = onValueChange,
                onSubmit = onSubmit,
                onSignIn = onSignIn,
                disabledMessage = disabledMessage
            )
            if (!canWrite && disabledMessage == null) {
                Text(
                    text = stringResource(R.string.github_comment_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
