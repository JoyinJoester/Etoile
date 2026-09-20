package takagi.ru.monica.github.feature.issues

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubIssueFormAnswer
import takagi.ru.monica.github.domain.GithubIssueFormField
import takagi.ru.monica.github.domain.GithubIssueFormFieldType
import takagi.ru.monica.github.domain.GithubIssueTemplateLanguage
import takagi.ru.monica.github.navigation.GithubWebUrls
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
internal fun IssueTemplateChooser(
    state: CreateIssueUiState,
    onAction: (CreateIssueAction) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.github_issue_choose_template), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.github_issue_choose_template_description),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.isLoadingTemplates || state.templateLoadError) {
            IssueTemplateLoadStatus(state, onAction, onOpenExternal)
            return@Column
        }
        val catalog = state.catalog ?: return@Column
        if (catalog.templates.isNotEmpty()) Text(stringResource(R.string.github_issue_repository_templates),
            style = MaterialTheme.typography.titleMedium)
        catalog.templates.forEach { template ->
            IssueTemplateChoice(
                name = template.name,
                description = if (template.isSupported) template.description else stringResource(R.string.github_issue_template_web_only),
                external = !template.isSupported,
                onClick = {
                    if (template.isSupported) onAction(CreateIssueAction.SelectTemplate(template.id))
                    else onOpenExternal(GithubWebUrls.newIssue(state.fullName, template.fileName))
                }
            )
        }
        if (catalog.blankIssuesEnabled) {
            if (catalog.templates.isEmpty()) Text(stringResource(R.string.github_issue_no_repository_templates),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.github_issue_builtin_templates), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.github_issue_template_language), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GithubIssueTemplateLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = state.builtInLanguage == language,
                        onClick = { onAction(CreateIssueAction.SelectBuiltInLanguage(language)) },
                        label = { Text(stringResource(if (language == GithubIssueTemplateLanguage.CHINESE)
                            R.string.github_issue_template_chinese else R.string.github_issue_template_english)) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = GithubExpressiveShapes.control
                    )
                }
            }
            state.builtInTemplates.forEach { template ->
                IssueTemplateChoice(template.name, template.description,
                    onClick = { onAction(CreateIssueAction.SelectTemplate(template.id)) })
            }
            IssueTemplateChoice(
                name = stringResource(R.string.github_issue_template_blank),
                description = stringResource(R.string.github_issue_template_blank_description),
                onClick = { onAction(CreateIssueAction.SelectTemplate(null)) }
            )
        } else Text(stringResource(R.string.github_issue_template_disabled), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        catalog.contactLinks.forEach { link ->
            IssueTemplateChoice(link.name, link.about, external = true, onClick = { onOpenExternal(link.url) })
        }
        TextButton(onClick = { onAction(CreateIssueAction.LoadTemplates) }) { Text(stringResource(R.string.github_issue_reload_templates)) }
        TextButton(onClick = { onOpenExternal(GithubWebUrls.issueTemplateChooser(state.fullName)) }) {
            Text(stringResource(R.string.github_open_on_github))
        }
    }
}

@Composable
internal fun IssueTemplateLoadStatus(
    state: CreateIssueUiState,
    onAction: (CreateIssueAction) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    if (state.isLoadingTemplates) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.github_issue_template_loading), style = MaterialTheme.typography.bodyMedium)
        }
    } else if (state.templateLoadError) {
        Column {
            IssueFormError(stringResource(R.string.github_issue_template_error))
            TextButton(onClick = { onAction(CreateIssueAction.LoadTemplates) }) { Text(stringResource(R.string.github_retry)) }
            TextButton(onClick = { onOpenExternal(GithubWebUrls.issueTemplateChooser(state.fullName)) }) {
                Text(stringResource(R.string.github_open_on_github))
            }
        }
    }
}

@Composable
private fun IssueTemplateChoice(name: String, description: String, external: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IssueFormFieldEditor(
    field: GithubIssueFormField,
    answer: GithubIssueFormAnswer?,
    enabled: Boolean,
    hasError: Boolean,
    focusError: Boolean,
    fullName: String,
    sourcePath: String,
    onTextChanged: (String) -> Unit,
    onToggleOption: (Int) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val doneLabel = stringResource(R.string.github_issue_form_done)
    LaunchedEffect(focusError) { if (focusError) bringIntoView.bringIntoView() }
    val label = if (field.required) stringResource(R.string.github_issue_form_required_label, field.label) else field.label
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (field.type == GithubIssueFormFieldType.MARKDOWN) {
            IssueTemplateMarkdown(field.value, fullName, onOpenExternal, sourcePath)
            return@Column
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (field.description.isNotBlank()) IssueTemplateMarkdown(field.description, fullName, onOpenExternal, sourcePath)
        when (field.type) {
            GithubIssueFormFieldType.INPUT, GithubIssueFormFieldType.TEXTAREA -> OutlinedTextField(
                value = answer?.text.orEmpty(), onValueChange = onTextChanged, readOnly = !enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                placeholder = field.placeholder.takeIf(String::isNotBlank)?.let { { Text(it) } },
                singleLine = field.type == GithubIssueFormFieldType.INPUT,
                minLines = if (field.type == GithubIssueFormFieldType.TEXTAREA) 4 else 1,
                maxLines = if (field.type == GithubIssueFormFieldType.TEXTAREA) 16 else 1,
                isError = hasError, shape = GithubExpressiveShapes.control
            )
            GithubIssueFormFieldType.DROPDOWN -> {
                var expanded by rememberSaveable { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = GithubExpressiveShapes.control
                ) {
                    Text(answer?.selections.orEmpty().mapNotNull { field.options.getOrNull(it)?.label }.joinToString(", ")
                        .ifBlank { stringResource(if (field.multiple) R.string.github_issue_form_choose_multiple else R.string.github_issue_form_choose_option) },
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                }
                if (expanded) AlertDialog(
                    onDismissRequest = { expanded = false },
                    title = { Text(field.label) },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            itemsIndexed(field.options) { index, option ->
                                val selected = index in answer?.selections.orEmpty()
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                        value = selected, enabled = enabled,
                                        role = if (field.multiple) Role.Checkbox else Role.RadioButton,
                                        onValueChange = { onToggleOption(index); if (!field.multiple) expanded = false }
                                    ).padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (field.multiple) Checkbox(checked = selected, onCheckedChange = null)
                                    else RadioButton(selected = selected, onClick = null)
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { expanded = false }) { Text(doneLabel) } }
                )
            }
            GithubIssueFormFieldType.CHECKBOXES -> field.options.forEachIndexed { index, option ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = index in answer?.selections.orEmpty(), enabled = enabled, role = Role.Checkbox,
                        onValueChange = { onToggleOption(index) }
                    ).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = index in answer?.selections.orEmpty(), onCheckedChange = null, enabled = enabled)
                    Column(Modifier.weight(1f)) {
                        IssueTemplateMarkdown(option.label, fullName, onOpenExternal, sourcePath)
                        if (option.required) Text(stringResource(R.string.github_issue_form_required),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            GithubIssueFormFieldType.MARKDOWN -> Unit
        }
        if (hasError) IssueFormError(stringResource(R.string.github_issue_form_field_error))
    }
}

@Composable
internal fun IssueTemplateMarkdown(text: String, fullName: String, onOpenExternal: (String) -> Unit, sourcePath: String = "") {
    MarkdownPreviewText(
        markdown = text, renderImages = false,
        onOpenExternalLink = { onOpenExternal(GithubWebUrls.resolveMarkdownLink(fullName, "HEAD", sourcePath, it)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun IssueFormError(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
