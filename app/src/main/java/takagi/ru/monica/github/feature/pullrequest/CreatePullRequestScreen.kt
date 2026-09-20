package takagi.ru.monica.github.feature.pullrequest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
fun CreatePullRequestScreen(
    fullName: String, state: CreatePullRequestState, onEdit: (CreatePullRequestState) -> Unit,
    onSubmit: () -> Unit, onLoadBranches: () -> Unit, onBack: () -> Unit,
    onPreview: () -> Unit, onOpenExternal: (String) -> Unit = {},
    onLoadTemplates: () -> Unit = {}, onApplyTemplate: (String, String) -> Unit = { _, _ -> }
) {
    var replacement by remember { mutableStateOf<Pair<String, String>?>(null) }
    replacement?.let { pending ->
        AlertDialog(onDismissRequest = { replacement = null },
            title = { Text(stringResource(R.string.github_pr_template_replace)) },
            text = { Text(stringResource(R.string.github_pr_template_replace_message)) },
            confirmButton = { TextButton(enabled = !state.submitting, onClick = {
                onApplyTemplate(pending.first, pending.second); replacement = null
            }) { Text(stringResource(R.string.github_pr_template_apply)) } },
            dismissButton = { TextButton(onClick = { replacement = null }) { Text(stringResource(R.string.discussion_cancel)) } })
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.github_create_pr)) }, navigationIcon = {
        IconButton(onClick = onBack, enabled = !state.submitting) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.github_back))
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(fullName, style = MaterialTheme.typography.titleMedium)
            BranchInput(state.base, stringResource(R.string.github_create_pr_base), state.branches, !state.submitting) { onEdit(state.copy(base = it)) }
            BranchInput(state.head, stringResource(R.string.github_create_pr_head), state.branches, !state.submitting) { onEdit(state.copy(head = it)) }
            Text(stringResource(R.string.github_create_pr_direction), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onPreview, enabled = !state.comparing && !state.submitting &&
                takagi.ru.monica.github.domain.GithubCreatePullRequestDraft.fromInput("Compare", "", state.base, state.head, headRepository = state.headRepository).isSuccess) {
                Text(stringResource(R.string.github_create_pr_preview))
            }
            if (state.comparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.compareError) Text(stringResource(R.string.github_create_pr_preview_error), color = MaterialTheme.colorScheme.error)
            state.comparison?.let { comparison ->
                Text(stringResource(R.string.github_create_pr_comparison, comparison.aheadBy, comparison.behindBy, comparison.files.size))
                if (comparison.aheadBy == 0) Text(stringResource(R.string.github_create_pr_no_changes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comparison.fileLimitReached) Text(stringResource(R.string.github_create_pr_file_limit), style = MaterialTheme.typography.bodySmall)
                var shown by remember(comparison) { mutableIntStateOf(5) }
                var selected by remember(comparison) { mutableStateOf<String?>(null) }
                comparison.files.take(shown).forEach { file ->
                    TextButton(onClick = { selected = if (selected == file.filename) null else file.filename }) {
                        Text("${file.filename}  +${file.additions} −${file.deletions}", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (selected == file.filename) PullRequestDiffCard(file, onOpenExternal)
                }
                if (shown < comparison.files.size) TextButton(onClick = { shown += 20 }) { Text(stringResource(R.string.discussion_more)) }
            }
            if (state.branchPage != null || state.branchesError) TextButton(onClick = onLoadBranches, enabled = !state.loadingBranches) {
                Text(stringResource(if (state.branchesError) R.string.discussion_retry else R.string.github_create_pr_more_branches))
            }
            if (state.loadingBranches) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (':' in state.head || state.headRepository.isNotEmpty()) OutlinedTextField(state.headRepository, { onEdit(state.copy(headRepository = it)) }, label = { Text(stringResource(R.string.github_create_pr_head_repo)) },
                enabled = !state.submitting, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.title, { onEdit(state.copy(title = it)) }, label = { Text(stringResource(R.string.discussion_subject)) },
                enabled = !state.submitting, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onLoadTemplates, enabled = !state.loadingTemplates && !state.submitting) {
                Text(stringResource(R.string.github_pr_templates))
            }
            if (state.loadingTemplates) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.templatesError) Text(stringResource(R.string.github_pr_templates_error), color = MaterialTheme.colorScheme.error)
            if (state.templatesLoaded && state.templates.isEmpty()) Text(stringResource(R.string.github_pr_templates_empty))
            state.templates.forEach { template ->
                TextButton(enabled = template.body != null && !state.submitting, onClick = {
                    if (state.body.isEmpty()) onApplyTemplate(template.id, state.body)
                    else replacement = template.id to state.body
                }) { Text("${template.repository}/${template.path}" + if (template.body == null) " · " + stringResource(R.string.github_pr_template_unavailable) else "") }
            }
            OutlinedTextField(state.body, { onEdit(state.copy(body = it)) }, label = { Text(stringResource(R.string.discussion_body)) },
                enabled = !state.submitting, minLines = 5, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(state.draft, { onEdit(state.copy(draft = it)) }, enabled = !state.submitting)
                Text(stringResource(R.string.github_create_pr_draft))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(state.maintainerCanModify, { onEdit(state.copy(maintainerCanModify = it)) }, enabled = !state.submitting)
                Text(stringResource(R.string.github_create_pr_maintainer))
            }
            if (state.failed) Text(stringResource(R.string.github_create_pr_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onSubmit, enabled = !state.submitting && state.validated().isSuccess) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.github_create_pr))
            }
        }
    }
}

@Composable
private fun BranchInput(value: String, label: String, branches: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { choosing = true }, enabled = enabled && branches.isNotEmpty()) {
                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.github_create_pr_choose_branch))
                }
            })
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                branches.forEach { branch -> DropdownMenuItem(text = { Text(branch) }, onClick = { onChange(branch); choosing = false }) }
            }
    }
}
