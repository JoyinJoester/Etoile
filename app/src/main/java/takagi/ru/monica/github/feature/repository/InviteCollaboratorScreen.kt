package takagi.ru.monica.github.feature.repository

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubCollaboratorRole

@Composable
fun InviteCollaboratorScreen(
    state: InviteCollaboratorUiState,
    fullName: String,
    onAction: (InviteCollaboratorAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_invite_collaborator),
        subtitle = fullName,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier,
        contentMaxWidth = GithubAdaptiveLayout.formMaxWidth
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.login,
                readOnly = state.isSubmitting,
                onValueChange = { onAction(InviteCollaboratorAction.LoginChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_collaborator_login)) },
                singleLine = true,
                isError = state.showLoginHint,
                supportingText = if (state.showLoginHint) {
                    { Text(stringResource(R.string.github_collaborator_login_invalid)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (state.canSubmit) onAction(InviteCollaboratorAction.Submit) }
                ),
                shape = GithubExpressiveShapes.control
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GithubTechnicalLabel(text = stringResource(R.string.github_collaborator_permission))
                // The endpoint only ever takes one permission value, so the form asks for one too.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GithubCollaboratorRole.assignable.forEach { role ->
                        FilterChip(
                            selected = state.role == role,
                            enabled = !state.isSubmitting,
                            onClick = { onAction(InviteCollaboratorAction.RoleChanged(role)) },
                            label = { Text(collaboratorRoleText(role)) },
                            shape = GithubExpressiveShapes.control,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = !state.isSubmitting,
                                selected = state.role == role,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.github_invite_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.feedback?.let { feedback ->
                Text(
                    text = collaboratorOutcomeText(feedback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.failure?.let { failure ->
                Text(
                    text = repositoryActionFailureText(failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = { onAction(InviteCollaboratorAction.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.github_invite_action))
                }
            }
        }
    }
}
