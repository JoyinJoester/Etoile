package takagi.ru.monica.gitlab.feature

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.foundation.text.KeyboardActions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.*
import takagi.ru.monica.gitlab.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitLabWorkListScreen(
    state: GitLabWorkListState,
    onAction: (GitLabWorkListAction) -> Unit,
    projectId: Long,
    onConversation: (GitLabConversationKind, Long) -> Unit,
    onPipeline: (Long) -> Unit,
    onAccounts: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    var displayedQuery by rememberSaveable { mutableStateOf(state.query) }
    var displayedAccount by rememberSaveable { mutableStateOf(state.account?.key) }
    var displayedSection by rememberSaveable { mutableStateOf(state.section.name) }
    var displayedFilter by rememberSaveable { mutableStateOf(state.filter) }
    LaunchedEffect(state.query, state.account?.key, state.section, state.filter) {
        if (displayedQuery != state.query || displayedAccount != state.account?.key ||
            displayedSection != state.section.name || displayedFilter != state.filter) listState.scrollToItem(0)
        displayedQuery = state.query
        displayedAccount = state.account?.key
        displayedSection = state.section.name
        displayedFilter = state.filter
    }
    val back = { if (state.editorOpen && !state.writing) onAction(GitLabWorkListAction.Editor(false)) else if (!state.writing) onBack() }
    BackHandler(enabled = state.editorOpen || state.writing, onBack = back)
    GithubDetailScaffold(title = gitLabSectionTitle(state.section), subtitle = state.account?.instanceUrl,
        backContentDescription = stringResource(R.string.github_back), onBack = back, showGithubServiceStatus = false,
        actions = { IconButton(onClick = { onAction(GitLabWorkListAction.Refresh) }, enabled = state.account != null && !state.writing && !state.loading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.gitlab_refresh))
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.account == null) item {
                GithubMessageState(stringResource(R.string.gitlab_account_required), actionLabel = stringResource(R.string.gitlab_accounts), onAction = onAccounts)
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GitLabWorkSection.entries.forEach { section -> FilterChip(selected = state.section == section,
                                onClick = { onAction(GitLabWorkListAction.Section(section)) }, enabled = !state.writing, label = { Text(gitLabSectionTitle(section)) }) }
                        }
                        GithubSearchField(state.searchInput, { onAction(GitLabWorkListAction.SearchChanged(it)) },
                            label = stringResource(if (state.section == GitLabWorkSection.PIPELINES) R.string.gitlab_pipeline_ref_filter else R.string.gitlab_work_search),
                            enabled = !state.writing, keyboardActions = KeyboardActions(onSearch = {
                                if (!state.writing) onAction(GitLabWorkListAction.Search)
                            }), modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { onAction(GitLabWorkListAction.Search) }, enabled = !state.writing) { Text(stringResource(R.string.gitlab_apply_filter)) }
                        if (state.section != GitLabWorkSection.PIPELINES) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filters = if (state.kind == GitLabConversationKind.ISSUE) listOf("opened", "closed", "all") else listOf("opened", "closed", "merged", "all")
                                filters.forEach { value -> FilterChip(selected = state.filter == value, onClick = { onAction(GitLabWorkListAction.Filter(value)) },
                                    enabled = !state.writing, label = { Text(gitLabStatus(value)) }) }
                            }
                            FilledTonalButton(onClick = { onAction(GitLabWorkListAction.Editor(true)) }, enabled = !state.writing) { Text(stringResource(R.string.gitlab_create)) }
                        } else {
                            val ref = state.searchInput.trim()
                            val run = githubConfirmedAction(stringResource(R.string.gitlab_run_pipeline), stringResource(R.string.gitlab_run_warning),
                                gitLabOperationTarget(state.account, projectId, ref), enabled = ref.isNotEmpty() && !state.writing,
                                onConfirm = { onAction(GitLabWorkListAction.Run(ref)) })
                            FilledTonalButton(onClick = run, enabled = ref.isNotEmpty() && !state.writing) { Text(stringResource(R.string.gitlab_run_pipeline)) }
                        }
                        GitLabWriteFeedback(state.writeError, onRefresh = { onAction(GitLabWorkListAction.Refresh) }, onAccounts = onAccounts)
                        if (state.writing || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (state.editorOpen && state.section != GitLabWorkSection.PIPELINES) item {
                    GitLabDraftForm(state.draft, state.kind, creating = true, busy = state.writing,
                        subject = gitLabOperationTarget(state.account, projectId, gitLabSectionTitle(state.section)), { onAction(GitLabWorkListAction.Draft(it)) },
                        { onAction(GitLabWorkListAction.Create(it)) }, { onAction(GitLabWorkListAction.Editor(false)) })
                }
                items(state.conversations, key = { "conversation:${state.section}:${it.id}" }) { item ->
                    val accent = if (item.state == "opened") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    Row(Modifier.fillMaxWidth().githubCollectionSurface()
                        .clickable(enabled = !state.writing, role = Role.Button) { onConversation(state.kind, item.number) }
                        .githubCollectionPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top) {
                        Icon(if (state.kind == GitLabConversationKind.MERGE_REQUEST) Icons.AutoMirrored.Filled.CallSplit
                            else if (item.state == "opened") Icons.Default.RadioButtonChecked else Icons.Default.CheckCircle,
                            contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.gitlab_conversation_heading, item.number, item.title),
                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(gitLabStatus(item.state), color = accent, style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.gitlab_author_updated, item.author, githubRelativeTime(item.updatedAt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(state.pipelines, key = { "pipeline:${it.id}" }) { pipeline ->
                    val failed = pipeline.status == "failed"
                    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Row(Modifier.fillMaxWidth().githubCollectionSurface()
                        .clickable(enabled = !state.writing, role = Role.Button) { onPipeline(pipeline.id) }
                        .githubCollectionPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top) {
                        Icon(when (pipeline.status) {
                            "success" -> Icons.Default.CheckCircle
                            "failed" -> Icons.Default.ErrorOutline
                            else -> Icons.Default.PlayCircle
                        }, null, Modifier.size(20.dp), tint = accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.gitlab_pipeline_heading, pipeline.id), style = MaterialTheme.typography.titleMedium)
                            Text(pipeline.ref, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(gitLabStatus(pipeline.status), color = accent, style = MaterialTheme.typography.labelMedium)
                            Text(pipeline.sha.take(12), style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Column {
                        GithubPagedListStatus(state.conversations.size + state.pipelines.size, state.loading, state.loadingMore,
                            state.error != null, state.nextPage != null && !state.writing, gitLabWorkError(state.error), stringResource(R.string.gitlab_work_empty),
                            { onAction(GitLabWorkListAction.Retry) }, { onAction(GitLabWorkListAction.More) })
                        GitLabAccessNotice(state.error, onAccounts)
                    }
                }
            }
        }
    }
}
