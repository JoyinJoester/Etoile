package takagi.ru.monica.github.feature.issues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubCharacterCounter
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubMessageState
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueDraft

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateIssueScreen(
    state: CreateIssueUiState,
    canSubmit: Boolean,
    onAction: (CreateIssueAction) -> Unit,
    onBack: () -> Unit,
    onCreated: (GithubIssue) -> Unit,
    onSignIn: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.createdIssue?.id) {
        state.createdIssue?.let { issue ->
            onCreated(issue)
            onAction(CreateIssueAction.ConsumeCreatedIssue)
        }
    }
    val back = {
        if (state.isChoosingTemplate && state.hasChosenTemplate) onAction(CreateIssueAction.DismissTemplateChooser)
        else onBack()
    }
    BackHandler(enabled = state.isChoosingTemplate && state.hasChosenTemplate, onBack = back)
    GithubDetailScaffold(
        title = stringResource(R.string.github_new_issue), subtitle = state.fullName,
        backContentDescription = stringResource(R.string.github_back), onBack = back,
        modifier = modifier, contentMaxWidth = GithubAdaptiveLayout.formMaxWidth
    ) { padding ->
        if (!canSubmit) {
            GithubMessageState(
                title = stringResource(R.string.github_sign_in_to_write),
                actionLabel = stringResource(R.string.github_sign_in), onAction = onSignIn,
                modifier = Modifier.padding(padding).padding(horizontal = 20.dp)
            )
            return@GithubDetailScaffold
        }
        key(state.isChoosingTemplate, state.selectedTemplate?.id, state.isPreviewing) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isChoosingTemplate) {
                    IssueTemplateChooser(state, onAction, onOpenExternal)
                    return@Column
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (state.selectedTemplate?.isBuiltIn == true) R.string.github_issue_builtin_templates else R.string.github_issue_template),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.selectedTemplate?.name ?: stringResource(R.string.github_issue_template_blank),
                        style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onAction(CreateIssueAction.ShowTemplateChooser) }, enabled = !state.isSubmitting
                        ) { Text(stringResource(R.string.github_issue_change_template)) }
                        TextButton(
                            onClick = { onAction(CreateIssueAction.TogglePreview) }, enabled = !state.isSubmitting
                        ) { Text(stringResource(if (state.isPreviewing) R.string.github_issue_edit_draft else R.string.github_issue_preview)) }
                    }
                }
                if (state.isLoadingTemplates || state.templateLoadError) {
                    IssueTemplateLoadStatus(state, onAction, onOpenExternal)
                } else if (!state.selectionAllowed && state.hasChosenTemplate) {
                    IssueFormError(stringResource(R.string.github_issue_template_unavailable))
                }
                state.selectedTemplate?.let { template ->
                    if (template.labels.isNotEmpty() || template.assignees.isNotEmpty()) {
                        Surface(shape = GithubExpressiveShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (template.labels.isNotEmpty()) Text(
                                    stringResource(R.string.github_issue_template_labels, template.labels.joinToString(", ")),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (template.assignees.isNotEmpty()) Text(
                                    stringResource(R.string.github_issue_template_assignees, template.assignees.joinToString(", ") { "@$it" }),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(stringResource(R.string.github_issue_template_metadata_note), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.isPreviewing) {
                    Surface(shape = GithubExpressiveShapes.container, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(state.title.ifBlank { stringResource(R.string.github_issue_title) }, style = MaterialTheme.typography.titleLarge)
                            IssueTemplateMarkdown(
                                text = state.submissionBody.ifBlank { stringResource(R.string.github_issue_body_empty) },
                                fullName = state.fullName, onOpenExternal = onOpenExternal
                            )
                        }
                    }
                } else {
                    val titleRequester = remember { BringIntoViewRequester() }
                    LaunchedEffect(state.validationError) {
                        if (state.validationError) titleRequester.bringIntoView()
                    }
                    OutlinedTextField(
                        value = state.title, readOnly = state.isSubmitting,
                        onValueChange = { onAction(CreateIssueAction.TitleChanged(it)) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(titleRequester),
                        label = { Text(stringResource(R.string.github_issue_title)) },
                        singleLine = true, isError = state.validationError,
                        supportingText = { GithubCharacterCounter(state.title.length, GithubIssueDraft.MAX_TITLE_LENGTH) },
                        shape = GithubExpressiveShapes.control
                    )
                    if (state.selectedTemplate?.isForm == true) {
                        state.selectedTemplate.fields.forEach { field ->
                            key(field.id) {
                                IssueFormFieldEditor(
                                    field = field, answer = state.answers[field.id], enabled = !state.isSubmitting,
                                    hasError = field.id in state.invalidFieldIds,
                                    focusError = !state.validationError && field.id == state.invalidFieldIds.firstOrNull(),
                                    fullName = state.fullName, sourcePath = state.selectedTemplate.id,
                                    onTextChanged = { onAction(CreateIssueAction.FormTextChanged(field.id, it)) },
                                    onToggleOption = { onAction(CreateIssueAction.ToggleFormOption(field.id, it)) },
                                    onOpenExternal = onOpenExternal
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = state.body, readOnly = state.isSubmitting,
                            onValueChange = { onAction(CreateIssueAction.BodyChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.github_issue_description)) },
                            minLines = 10, maxLines = 24, shape = GithubExpressiveShapes.container
                        )
                    }
                }
                GithubCharacterCounter(state.submissionBody.length, GithubIssueDraft.MAX_BODY_LENGTH)
                if (state.validationError) IssueFormError(stringResource(R.string.github_issue_input_error))
                if (state.invalidFieldIds.isNotEmpty()) IssueFormError(stringResource(R.string.github_issue_form_required_error))
                if (state.bodyLengthError) IssueFormError(stringResource(R.string.github_issue_body_length_error))
                if (state.submitError) IssueFormError(stringResource(R.string.github_issue_submit_error))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onAction(CreateIssueAction.Submit)
                    }, modifier = Modifier.fillMaxWidth(),
                    enabled = state.canPublish && state.title.isNotBlank(), shape = GithubExpressiveShapes.control
                ) {
                    if (state.isSubmitting) CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp).size(18.dp), strokeWidth = 2.dp
                    )
                    Text(stringResource(if (state.isSubmitting) R.string.github_creating_issue else R.string.github_create_issue))
                }
            }
        }
    }
    if (state.showTemplateChangeConfirmation) {
        val replaceTitle = stringResource(R.string.github_issue_template_replace_title)
        val replaceBody = stringResource(R.string.github_issue_template_replace_body)
        val replaceLabel = stringResource(R.string.github_issue_template_replace)
        val cancelLabel = stringResource(R.string.github_cancel)
        AlertDialog(
            onDismissRequest = { onAction(CreateIssueAction.CancelTemplateChange) },
            title = { Text(replaceTitle) },
            text = { Text(replaceBody) },
            confirmButton = {
                TextButton(onClick = { onAction(CreateIssueAction.ConfirmTemplateChange) }) {
                    Text(replaceLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CreateIssueAction.CancelTemplateChange) }) {
                    Text(cancelLabel)
                }
            }
        )
    }
}
