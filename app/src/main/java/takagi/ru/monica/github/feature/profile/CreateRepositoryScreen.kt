package takagi.ru.monica.github.feature.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.feature.repository.repositoryActionFailureText

@Composable
fun CreateRepositoryScreen(
    state: CreateRepositoryUiState,
    accountLogin: String,
    onAction: (CreateRepositoryAction) -> Unit,
    onBack: () -> Unit,
    onCreated: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.created?.id) {
        state.created?.let { created ->
            onCreated(created)
            onAction(CreateRepositoryAction.ConsumeCreated)
        }
    }
    GithubDetailScaffold(
        title = stringResource(R.string.github_create_repository),
        subtitle = "@$accountLogin",
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
                value = state.name,
                readOnly = state.isSubmitting,
                onValueChange = { onAction(CreateRepositoryAction.NameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_repository_name)) },
                singleLine = true,
                isError = state.showNameHint,
                supportingText = if (state.showNameHint) {
                    { Text(stringResource(R.string.github_repository_name_invalid)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = GithubExpressiveShapes.control
            )
            OutlinedTextField(
                value = state.description,
                readOnly = state.isSubmitting,
                onValueChange = { onAction(CreateRepositoryAction.DescriptionChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_repository_description)) },
                placeholder = { Text(stringResource(R.string.github_description_hint)) },
                singleLine = true,
                shape = GithubExpressiveShapes.control
            )
            FormChoiceRow(
                title = stringResource(R.string.github_repository_visibility),
                summary = stringResource(
                    if (state.isPrivate) R.string.github_visibility_private
                    else R.string.github_visibility_public
                ),
                checked = state.isPrivate,
                enabled = !state.isSubmitting,
                onCheckedChange = { onAction(CreateRepositoryAction.TogglePrivate) }
            )
            FormChoiceRow(
                title = stringResource(R.string.github_add_readme),
                summary = null,
                checked = state.autoInit,
                enabled = !state.isSubmitting,
                onCheckedChange = { onAction(CreateRepositoryAction.ToggleAutoInit) }
            )
            Text(
                text = stringResource(R.string.github_create_repository_owner_note, accountLogin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.failure?.let { failure ->
                Text(
                    text = repositoryActionFailureText(failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = { onAction(CreateRepositoryAction.Submit) },
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
                    Text(stringResource(R.string.github_create_repository_action))
                }
            }
        }
    }
}

@Composable
private fun FormChoiceRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = { onCheckedChange() }, enabled = enabled)
    }
}
