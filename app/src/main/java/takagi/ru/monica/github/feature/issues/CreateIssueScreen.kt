package takagi.ru.monica.github.feature.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCharacterCounter
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueDraft

@Composable
fun CreateIssueScreen(
    state: CreateIssueUiState,
    canSubmit: Boolean,
    onAction: (CreateIssueAction) -> Unit,
    onBack: () -> Unit,
    onCreated: (GithubIssue) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.createdIssue?.id) {
        state.createdIssue?.let { issue ->
            onCreated(issue)
            onAction(CreateIssueAction.ConsumeCreatedIssue)
        }
    }

    GithubDetailScaffold(
        title = stringResource(R.string.github_new_issue),
        subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        if (!canSubmit) {
            GithubMessageState(
                title = stringResource(R.string.github_sign_in_to_write),
                actionLabel = stringResource(R.string.github_sign_in),
                onAction = onSignIn,
                modifier = Modifier.padding(padding).padding(horizontal = 20.dp)
            )
            return@GithubDetailScaffold
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { onAction(CreateIssueAction.TitleChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_issue_title)) },
                singleLine = true,
                isError = state.validationError,
                supportingText = {
                    GithubCharacterCounter(
                        current = state.title.length,
                        maximum = GithubIssueDraft.MAX_TITLE_LENGTH
                    )
                },
                shape = GithubExpressiveShapes.control
            )
            OutlinedTextField(
                value = state.body,
                onValueChange = { onAction(CreateIssueAction.BodyChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_issue_description)) },
                minLines = 10,
                maxLines = 24,
                supportingText = {
                    GithubCharacterCounter(
                        current = state.body.length,
                        maximum = GithubIssueDraft.MAX_BODY_LENGTH
                    )
                },
                shape = GithubExpressiveShapes.container
            )
            if (state.validationError) {
                Text(
                    stringResource(R.string.github_issue_input_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.submitError) {
                Text(
                    stringResource(R.string.github_issue_submit_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { onAction(CreateIssueAction.Submit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting && state.title.isNotBlank(),
                shape = GithubExpressiveShapes.control
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp).size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    stringResource(
                        if (state.isSubmitting) R.string.github_creating_issue else R.string.github_create_issue
                    )
                )
            }
        }
    }
}
